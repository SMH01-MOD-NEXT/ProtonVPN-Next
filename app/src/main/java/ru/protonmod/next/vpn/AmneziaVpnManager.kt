package ru.protonmod.next.vpn

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import org.amnezia.awg.config.Interface
import org.amnezia.awg.config.Peer
import ru.protonmod.next.data.local.SessionEntity
import ru.protonmod.next.data.network.PhysicalServer
import ru.protonmod.next.data.repository.VpnRepository
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AmneziaVpnManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vpnRepository: VpnRepository
) {
    companion object {
        private const val TAG = "AmneziaVpnManager"
        private const val TUNNEL_NAME = "proton_awg"

        // Proton statically assigns these internal IPs to all WG clients
        private const val PROTON_CLIENT_IP = "10.2.0.2"
        private const val PROTON_DNS_IP = "10.2.0.1"
    }

    private val backend: GoBackend by lazy { GoBackend(context) }

    private val tunnel = object : Tunnel {
        override fun getName() = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) {
            _tunnelState.value = newState
            Log.d(TAG, "Tunnel state changed: $newState")
        }
    }

    private val _tunnelState = MutableStateFlow(Tunnel.State.DOWN)
    val tunnelState: StateFlow<Tunnel.State> = _tunnelState

    suspend fun connect(
        logicalServerId: String,
        server: PhysicalServer,
        session: SessionEntity
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting AWG connection to ${server.domain}")

            // 1. Generate Ed25519 KeyPair
            val keyPair = com.proton.gopenpgp.ed25519.KeyPair()
            val publicKeyPem = keyPair.publicKeyPKIXPem()
            val wgPrivateKeyB64 = keyPair.toX25519Base64()

            // 2. Register our public key at /vpn/v1/certificate
            val regResult = vpnRepository.registerWireGuardKey(
                accessToken = session.accessToken,
                sessionId = session.sessionId,
                publicKeyPem = publicKeyPem
            )

            if (regResult.isFailure) return@withContext Result.failure(regResult.exceptionOrNull()!!)

            // 3. Set internal IPs (Hardcoded per Proton's logic)
            val localIp = PROTON_CLIENT_IP
            val dnsServer = PROTON_DNS_IP

            // 4. Resolve the target Node IP from the physical server domain
            val targetIp = try {
                InetAddress.getByName(server.domain).hostAddress ?: throw Exception("DNS resolution failed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve domain: ${server.domain}", e)
                throw e
            }

            Log.d(TAG, "Target IP: $targetIp, Internal IP: $localIp")

            // 5. Use the static Server Public Key from the server list
            val serverPubKey = server.wgPublicKey ?: throw Exception("Missing WG Public Key for Server")

            // 6. Build Config with AmneziaWG Obfuscation parameters
            val config = buildAwgConfig(serverPubKey, wgPrivateKeyB64, localIp, dnsServer, targetIp)

            // ЛОГИРУЕМ ПОЛНЫЙ СОБРАННЫЙ КОНФИГ
            Log.d(TAG, "\n============= AWG CONFIG =============\n${config.toAwgQuickString()}\n======================================")

            // 7. Bring UP the interface
            Log.d(TAG, "Bringing up tunnel interface...")
            backend.setState(tunnel, Tunnel.State.UP, config)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            Result.failure(e)
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect error", e)
        }
    }

    private fun buildAwgConfig(
        serverPublicKey: String,
        privateKey: String,
        localIp: String,
        dnsServer: String,
        targetIp: String
    ): Config {
        val peer = Peer.Builder()
            .parsePublicKey(serverPublicKey)
            // Меняем порт на 51820 (стандартный для Proton WG), 443 иногда резервируется под OpenVPN TCP
            .parseEndpoint("$targetIp:51820")
            // Убираем ::/0, так как у нас нет назначенного IPv6 адреса. Из-за этого пакеты могли отбрасываться.
            .parseAllowedIPs("0.0.0.0/0")
            .setPersistentKeepalive(60)
            .build()

        val iface = Interface.Builder()
            .parsePrivateKey(privateKey)
            .parseAddresses("$localIp/32")
            .parseDnsServers(dnsServer)
            .setMtu(1280) // Оптимальный MTU для обфускации

            // AmneziaWG Obfuscation parameters (Proton Stealth signature)
            .setJunkPacketCount(3)
            .setJunkPacketMinSize(1)
            .setJunkPacketMaxSize(3)
            .setInitPacketJunkSize(0)
            .setResponsePacketJunkSize(0)
            .setInitPacketMagicHeader("1")
            .setResponsePacketMagicHeader("2")
            .setUnderloadPacketMagicHeader("3")
            .setTransportPacketMagicHeader("4")
            .build()

        return Config.Builder()
            .setInterface(iface)
            .addPeer(peer)
            .build()
    }
}