/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.protonmod.next.data.network

import android.content.Context
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import javax.inject.Provider
import javax.net.SocketFactory
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.vpn.AmneziaVpnManager

class Socks5Socket(
    private val proxyHost: String,
    private val proxyPort: Int,
    private val proxyUsername: String = "",
    private val proxyPassword: String = ""
) : Socket() {

    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        if (endpoint !is InetSocketAddress) {
            super.connect(endpoint, timeout)
            return
        }

        val targetHost = endpoint.hostString
        val targetPort = endpoint.port

        // 1. Connect to the SOCKS5 proxy server
        super.connect(InetSocketAddress(proxyHost, proxyPort), timeout)

        val inputStream = DataInputStream(this.getInputStream())
        val outputStream = DataOutputStream(this.getOutputStream())

        // 2. SOCKS5 Greeting / Method Selection
        val hasAuth = proxyUsername.isNotEmpty()
        if (hasAuth) {
            outputStream.write(byteArrayOf(0x05.toByte(), 0x02.toByte(), 0x00.toByte(), 0x02.toByte()))
        } else {
            outputStream.write(byteArrayOf(0x05.toByte(), 0x01.toByte(), 0x00.toByte()))
        }
        outputStream.flush()

        val version = inputStream.readByte()
        if (version != 0x05.toByte()) {
            throw IOException("Unsupported SOCKS version: $version")
        }

        val selectedMethod = inputStream.readByte()
        if (selectedMethod == 0x02.toByte() && hasAuth) {
            // Username/Password authentication (SOCKS5 Subnegotiation)
            val userBytes = proxyUsername.toByteArray(Charsets.UTF_8)
            val passBytes = proxyPassword.toByteArray(Charsets.UTF_8)

            outputStream.writeByte(0x01) // Subnegotiation version
            outputStream.writeByte(userBytes.size)
            outputStream.write(userBytes)
            outputStream.writeByte(passBytes.size)
            outputStream.write(passBytes)
            outputStream.flush()

            val authVer = inputStream.readByte()
            val authStatus = inputStream.readByte()
            if (authStatus != 0x00.toByte()) {
                throw IOException("SOCKS5 authentication failed with status: $authStatus")
            }
        } else if (selectedMethod != 0x00.toByte()) {
            throw IOException("SOCKS5 proxy requires authentication method $selectedMethod, which is not supported/provided")
        }

        // 3. Send CONNECT command with DOMAINNAME address type (forces remote DNS resolution)
        val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
        outputStream.writeByte(0x05) // Version 5
        outputStream.writeByte(0x01) // Connect command
        outputStream.writeByte(0x00) // Reserved
        outputStream.writeByte(0x03) // ATYP: DOMAINNAME
        outputStream.writeByte(hostBytes.size)
        outputStream.write(hostBytes)
        outputStream.writeShort(targetPort)
        outputStream.flush()

        // 4. Read response
        val repVer = inputStream.readByte()
        if (repVer != 0x05.toByte()) {
            throw IOException("Invalid SOCKS connect response version: $repVer")
        }

        val repStatus = inputStream.readByte()
        if (repStatus != 0x00.toByte()) {
            val errorMsg = when (repStatus.toInt()) {
                1 -> "general SOCKS server failure"
                2 -> "connection not allowed by ruleset"
                3 -> "Network unreachable"
                4 -> "Host unreachable"
                5 -> "Connection refused"
                6 -> "TTL expired"
                7 -> "Command not supported"
                8 -> "Address type not supported"
                else -> "unknown error $repStatus"
            }
            throw IOException("SOCKS connection failed: $errorMsg")
        }

        inputStream.readByte() // Reserved byte

        val atyp = inputStream.readByte()
        when (atyp) {
            0x01.toByte() -> { // IPv4
                val addr = ByteArray(4)
                inputStream.readFully(addr)
            }
            0x03.toByte() -> { // Domain name
                val len = inputStream.readByte().toInt() and 0xFF
                val addr = ByteArray(len)
                inputStream.readFully(addr)
            }
            0x04.toByte() -> { // IPv6
                val addr = ByteArray(16)
                inputStream.readFully(addr)
            }
            else -> throw IOException("Unsupported address type in SOCKS connect response: $atyp")
        }
        inputStream.readShort() // Bind port (ignored)
    }
}

/** A SOCKS5 socket factory with immutable settings for dedicated clients such as AI APIs. */
class FixedSocks5SocketFactory(
    private val proxyHost: String,
    private val proxyPort: Int,
    private val proxyUsername: String = "",
    private val proxyPassword: String = ""
) : SocketFactory() {
    private fun newSocket() = Socks5Socket(proxyHost, proxyPort, proxyUsername, proxyPassword)

    override fun createSocket(): Socket = newSocket()

    override fun createSocket(host: String?, port: Int): Socket = newSocket().apply {
        connect(InetSocketAddress.createUnresolved(host, port))
    }

    override fun createSocket(
        host: String?,
        port: Int,
        localHost: InetAddress?,
        localPort: Int
    ): Socket = newSocket().apply {
        bind(InetSocketAddress(localHost, localPort))
        connect(InetSocketAddress.createUnresolved(host, port))
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket = newSocket().apply {
        connect(InetSocketAddress(host, port))
    }

    override fun createSocket(
        address: InetAddress?,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int
    ): Socket = newSocket().apply {
        bind(InetSocketAddress(localAddress, localPort))
        connect(InetSocketAddress(address, port))
    }
}

class ApiBypassSocketFactory(
    private val context: Context,
    private val vpnManagerProvider: Provider<AmneziaVpnManager>,
    private val settingsManagerProvider: Provider<SettingsManager>,
    private val shouldUseApiBypass: (Context, Provider<AmneziaVpnManager>, Provider<SettingsManager>) -> Boolean
) : SocketFactory() {

    private val defaultFactory = getDefault()

    private fun getSocksSocketIfEnabled(): Socks5Socket? {
        val settings = settingsManagerProvider.get()
        val useProxy = shouldUseApiBypass(context, vpnManagerProvider, settingsManagerProvider)
        val strategy = settings.getApiBypassStrategySync()

        if (useProxy && strategy == SettingsManager.STRATEGY_CUSTOM_PROXY) {
            val type = settings.getApiProxyTypeSync()
            if (type == SettingsManager.PROXY_TYPE_SOCKS) {
                val host = settings.getApiProxyHostSync()
                val port = settings.getApiProxyPortSync()
                val username = settings.getApiProxyUsernameSync()
                val password = settings.getApiProxyPasswordSync()
                if (host.isNotEmpty()) {
                    return Socks5Socket(host, port, username, password)
                }
            }
        }
        return null
    }

    override fun createSocket(): Socket {
        return getSocksSocketIfEnabled() ?: defaultFactory.createSocket()
    }

    override fun createSocket(host: String?, port: Int): Socket {
        val socksSocket = getSocksSocketIfEnabled()
        if (socksSocket != null) {
            socksSocket.connect(InetSocketAddress.createUnresolved(host, port))
            return socksSocket
        }
        return defaultFactory.createSocket(host, port)
    }

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket {
        val socksSocket = getSocksSocketIfEnabled()
        if (socksSocket != null) {
            socksSocket.bind(InetSocketAddress(localHost, localPort))
            socksSocket.connect(InetSocketAddress.createUnresolved(host, port))
            return socksSocket
        }
        return defaultFactory.createSocket(host, port, localHost, localPort)
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket {
        val socksSocket = getSocksSocketIfEnabled()
        if (socksSocket != null) {
            socksSocket.connect(InetSocketAddress(host, port))
            return socksSocket
        }
        return defaultFactory.createSocket(host, port)
    }

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
        val socksSocket = getSocksSocketIfEnabled()
        if (socksSocket != null) {
            socksSocket.bind(InetSocketAddress(localAddress, localPort))
            socksSocket.connect(InetSocketAddress(address, port))
            return socksSocket
        }
        return defaultFactory.createSocket(address, port, localAddress, localPort)
    }
}
