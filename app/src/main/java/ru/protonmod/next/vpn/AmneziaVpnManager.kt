package ru.protonmod.next.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
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
        private const val PROTON_CLIENT_IP = "10.2.0.2"
        private const val PROTON_DNS_IP = "10.2.0.1"
    }

    private val _tunnelState = MutableStateFlow(Tunnel.State.DOWN)
    val tunnelState: StateFlow<Tunnel.State> = _tunnelState

    init {
        // Слушаем изменения статуса от изолированного процесса :vpn
        val filter = IntentFilter(ProtonVpnService.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val stateStr = intent?.getStringExtra(ProtonVpnService.EXTRA_STATE)
                stateStr?.let {
                    Log.d(TAG, "Syncing state from :vpn process -> $it")
                    _tunnelState.value = Tunnel.State.valueOf(it)
                }
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    suspend fun connect(
        logicalServerId: String,
        server: PhysicalServer,
        session: SessionEntity
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting AWG connection to ${server.domain}")
            _tunnelState.value = Tunnel.State.TOGGLE

            // 1. Generate Ed25519 KeyPair using Proton's library
            // It is completely safe to use here because this runs in the main UI process,
            // while the AmneziaWG engine runs in the isolated :vpn process!
            val keyPair = com.proton.gopenpgp.ed25519.KeyPair()

            // Server strictly expects Ed25519 PKIX PEM, but WG needs derived X25519
            val publicKeyPem = keyPair.publicKeyPKIXPem()
            val wgPrivateKeyB64 = keyPair.toX25519Base64()

            // 2. Register our public key at /vpn/v1/certificate
            val regResult = vpnRepository.registerWireGuardKey(
                accessToken = session.accessToken,
                sessionId = session.sessionId,
                publicKeyPem = publicKeyPem
            )

            if (regResult.isFailure) {
                _tunnelState.value = Tunnel.State.DOWN
                return@withContext Result.failure(regResult.exceptionOrNull()!!)
            }

            // 3. Set internal IPs
            val localIp = PROTON_CLIENT_IP
            val dnsServer = PROTON_DNS_IP

            // 4. Resolve the target Node IP
            val targetIp = try {
                InetAddress.getByName(server.domain).hostAddress ?: throw Exception("DNS resolution failed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve domain: ${server.domain}", e)
                _tunnelState.value = Tunnel.State.DOWN
                throw e
            }

            // 5. Use the static Server Public Key from the server list
            val serverPubKey = server.wgPublicKey ?: throw Exception("Missing WG Public Key for Server")

            // 6. Build Config
            val config = buildAwgConfig(serverPubKey, wgPrivateKeyB64, localIp, dnsServer, targetIp)
            val configStr = config.toAwgQuickString()

            Log.d(TAG, "Sending AWG Config to isolated :vpn process...")

            // 7. Send config to isolated VPN process
            val intent = Intent(context, ProtonVpnService::class.java).apply {
                action = ProtonVpnService.ACTION_CONNECT
                putExtra(ProtonVpnService.EXTRA_CONFIG, configStr)
            }
            context.startService(intent)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            _tunnelState.value = Tunnel.State.DOWN
            Result.failure(e)
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        val intent = Intent(context, ProtonVpnService::class.java).apply {
            action = ProtonVpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
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
            .parseEndpoint("$targetIp:51820")
            .parseAllowedIPs("0.0.0.0/0")
            .setPersistentKeepalive(60)
            .build()

        val iface = Interface.Builder()
            .parsePrivateKey(privateKey)
            .parseAddresses("$localIp/32")
            .parseDnsServers(dnsServer)
            .setMtu(1420)
            .setJunkPacketCount(3)
            .setJunkPacketMinSize(1)
            .setJunkPacketMaxSize(3)
            .setInitPacketJunkSize(0)
            .setResponsePacketJunkSize(0)
            .setInitPacketMagicHeader("1")
            .setResponsePacketMagicHeader("2")
            .setUnderloadPacketMagicHeader("3")
            .setTransportPacketMagicHeader("4")
            .setSpecialJunkI1("<b 0xc6000000010843290a47ba8ba2ed000044d0e3efd9326adb60561baa3bc4b52471b2d459ddcc9a508dffddc97e4d40d811d3de7bc98cf06ea85902361ca3ae66b2a99c7de96f0ace4ba4710658aefde6dec6837bc1a48f47bbd63c6e60ff494d3e1bea5f13927922401c40b0f4570d26be6806b506a9ff5f75ca86fae5f8175d4b6bfd418df9b922cdff8e60b06decfe66f2b07da61a47b5c8b32fa999d8feac21c8878b6e15ee03b8388b2afd9ffd3b46753b0284907b10747e526eebf287ff08735929c4c5e4784a5e2ad3dd8ac8200d0e99ad1219e54060ddc72813e8a3e2291ac713c5f3251c5d748fd68782a2e8eb0c021e437a79aafb253efae3ee72e1051b647c45b676d3b9e474d4f60c7bf7d328106cb94f67eaf2c991cd7043371debbf2b4159b8f80f5da0e1b18f4da35fca0a88026b375f1082731d1cbbe9ba3ae2bfefec250ee328ded7f8330d4cda38f74a7fe10b58ace936fc83cfcb3e1ebed520f7559549a8f20568a248e16949611057a3dd9952bae9b7be518c2b5b2568b8582c165c73a6e8f9b042ec9702704a94dd99893421310d43ffc9caf003ff5fc7bcd718f3fa99d663a8bbad6b595ec1d4cf3c0ed1668d0541c4e5b7e5ded40c6628eb64b29f514424d08d8522ddf7b856e9b820441907177a3dbd9b958172173be8c45c8c7b1816fe4d24927f9b12778153fc118194786c6cf49bc5cf09177f73be27917a239592f9acd9a21150abbd1ca93b1e305dc64d9883429a032c3179e0639592c248cbacec00c90bfb5d5eaf8920bf80c47085a490ead8d0af45f6754e8ae5692f86be02c480ca2a1e6156dccf1bcb5c911c05e3c3a946ca23461669c57d287dcfa9dd187fc6a58394f0b2878c07e1d8cb6be41725d49c118e9ddbe1ae6e5d1a04b36ad98a24f0378deea84febb60b22dc81d8377fb3f21069d83e90b9eba2b6b0ea95acf5fd0482a00d064a9b73e0b3732fde45404e22349a514151381fc6095a8204234359c28765e360a57eb222418b11be704651e4da1b52b135d31ba63a7f06a0f7b8b6177f9bd02fb517877a1340e59d8dbe52ea8135bc80b2aa1959539803a31720ac949c7bf0785c2e413e8b83dd4fd40d8a63fbd832ecb727d0c0df04ce10dac6a7d6d75e264aaf856e7485cc2c4e1749f169e5ad4de6f89a2333e362910dd0d962e3bf59a353df5760fd15956fe32e40f863ea020e9709aa9a9ebeffc885e64231dc6fc384bc6a9e7e5c64c0eaf39f9f14a13658883ee8dd94737cb3a8c2f7020bfacb80f0122866635873e248e22b5a5abc84d507e1720d3fb5f827d75c1d39235a361a217eb0587d1639b0b31aef1fe91958220fcf934c2517dea2f1afe51cd63ac31b5f9323a427c36a5442f8a89b7494f1592666f62be0d8cf67fdf5ef38fafc55b7b4f569a105dfa9925f0a41913c6ee13064d4b83f9ee1c3231c402d68a624e2388e357144be99197dcafb92118d9a9ec6fe832771e12448a146fb5b9620a4718070b368aab646b03cce41ec4d5d9a9c880a9cff06aba991cc0845030abbac87c67255f0373eb38444a51d0958e57c7a33042697465c84abe6791cb8f28e484c4cd04f10791ad911b0dcc217f66cb3aa5fcdbb1e2be88139c4ac2652e469122408feba59ad04f66eb8ab8c80aaf10c2ec1f80b5be111d3ccc832df2395a947e335e7908fda5dcdaa14a61f0fa7156c94b1c96e5c191d850e341adc2e22c8f69fcfa5c3e403eadc933f18be3734bc345def4f40ea3e12>")
            .build()

        return Config.Builder()
            .setInterface(iface)
            .addPeer(peer)
            .build()
    }
}