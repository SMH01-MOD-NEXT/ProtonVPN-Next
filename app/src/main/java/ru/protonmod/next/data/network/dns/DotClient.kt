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

package ru.protonmod.next.data.network.dns

import ru.protonmod.next.utils.ProtonLogger
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.random.Random

/**
 * DNS over TLS (RFC 7858) against a fixed address on port 853.
 *
 * This is the second transport, not the first. DoH on 443 is normally the
 * better choice because it is indistinguishable from ordinary web traffic, but
 * when 443 to a known resolver is throttled or reset, 853 is a genuinely
 * different path that often survives — different port, different filtering
 * rule, frequently a different verdict.
 *
 * The socket is always opened to a literal address supplied by the caller, so
 * nothing here consults the system resolver. The hostname is used only as SNI
 * and for certificate validation, which is what makes the connection
 * authenticated rather than merely encrypted.
 *
 * Messages are built and parsed by hand: the wire format is small, and pulling
 * in a resolver library to send one question would be a poor trade.
 */
@Singleton
class DotClient @Inject constructor() {

    private companion object {
        const val TAG = "DotClient"
        const val PORT = 853

        const val TYPE_A = 1
        const val TYPE_AAAA = 28
        const val CLASS_IN = 1

        /** Maximum answer we are willing to read, well above any real response. */
        const val MAX_RESPONSE_BYTES = 8 * 1024

        /** Compression pointers must always move backwards; this bounds the walk. */
        const val MAX_NAME_JUMPS = 16
    }

    /**
     * Resolves [hostname] through [provider] over TLS.
     *
     * Each of the provider's literal addresses is tried in turn, so a provider
     * with a blackholed primary still answers on its secondary. Returns an
     * empty list when the provider cannot be reached or has no answer; callers
     * treat that as "try the next provider" rather than as a hard failure.
     */
    fun resolve(
        provider: DnsProviders.Provider,
        hostname: String,
        timeoutMs: Int = 5_000,
    ): List<InetAddress> {
        for (address in provider.addresses) {
            try {
                val answers = query(address, provider.dotHostname, hostname, timeoutMs)
                if (answers.isNotEmpty()) {
                    ProtonLogger.d(TAG, "DoT ${provider.displayName} ($address) resolved $hostname")
                    return answers
                }
            } catch (e: Exception) {
                ProtonLogger.d(TAG, "DoT ${provider.displayName} ($address) failed: ${e.message}")
            }
        }
        return emptyList()
    }

    /**
     * One TLS session, two questions (A and AAAA), pipelined over the same
     * connection as RFC 7858 allows. Opening a fresh TLS session per record
     * type would double the handshake cost on exactly the slow networks this
     * path exists for.
     */
    private fun query(
        serverAddress: String,
        tlsHostname: String,
        hostname: String,
        timeoutMs: Int,
    ): List<InetAddress> {
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory

        Socket().use { raw ->
            raw.connect(InetSocketAddress(InetAddress.getByName(serverAddress), PORT), timeoutMs)
            raw.soTimeout = timeoutMs

            val tls = factory.createSocket(raw, tlsHostname, PORT, false) as SSLSocket
            tls.soTimeout = timeoutMs

            // Without this the handshake sends no SNI and skips hostname
            // verification, which would leave the session encrypted but
            // unauthenticated — an on-path resolver could answer instead.
            tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
            tls.startHandshake()

            val out = DataOutputStream(tls.outputStream.buffered())
            val input = DataInputStream(tls.inputStream.buffered())

            val idA = Random.nextInt(0, 0xFFFF)
            val idAaaa = Random.nextInt(0, 0xFFFF)
            writeMessage(out, buildQuery(idA, hostname, TYPE_A))
            writeMessage(out, buildQuery(idAaaa, hostname, TYPE_AAAA))
            out.flush()

            val results = mutableListOf<InetAddress>()
            repeat(2) {
                val response = readMessage(input) ?: return@repeat
                results.addAll(parseAnswers(response, hostname))
            }
            return results.distinctBy { it.hostAddress ?: "" }
        }
    }

    /** TCP-framed DNS carries a two-byte big-endian length prefix. */
    private fun writeMessage(out: DataOutputStream, message: ByteArray) {
        out.writeShort(message.size)
        out.write(message)
    }

    private fun readMessage(input: DataInputStream): ByteArray? {
        val length = try {
            input.readUnsignedShort()
        } catch (e: Exception) {
            return null
        }
        if (length <= 0 || length > MAX_RESPONSE_BYTES) return null
        val buffer = ByteArray(length)
        input.readFully(buffer)
        return buffer
    }

    private fun buildQuery(id: Int, hostname: String, type: Int): ByteArray {
        val labels = hostname.trim('.').split('.').filter { it.isNotEmpty() }
        // Header is 12 bytes; each label costs a length byte, plus the root
        // terminator, plus QTYPE and QCLASS.
        val size = 12 + labels.sumOf { it.length + 1 } + 1 + 4
        val buffer = java.nio.ByteBuffer.allocate(size)

        buffer.putShort(id.toShort())
        buffer.putShort(0x0100)  // standard query, recursion desired
        buffer.putShort(1)       // one question
        buffer.putShort(0)       // no answers
        buffer.putShort(0)       // no authority records
        buffer.putShort(0)       // no additional records

        for (label in labels) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            require(bytes.size in 1..63) { "DNS label out of range: $label" }
            buffer.put(bytes.size.toByte())
            buffer.put(bytes)
        }
        buffer.put(0)            // root label terminates the name
        buffer.putShort(type.toShort())
        buffer.putShort(CLASS_IN.toShort())

        return buffer.array()
    }

    /**
     * Extracts A and AAAA records from a response.
     *
     * Only the record types asked for are read; CNAME chains are ignored
     * because resolvers return the flattened addresses alongside them, which is
     * all that is needed here. A malformed or truncated message yields an empty
     * list rather than an exception — a hostile middlebox is one of the
     * expected inputs on this path.
     */
    private fun parseAnswers(message: ByteArray, hostname: String): List<InetAddress> {
        if (message.size < 12) return emptyList()
        val buffer = java.nio.ByteBuffer.wrap(message)

        buffer.short                                  // transaction id
        val flags = buffer.short.toInt() and 0xFFFF
        val rcode = flags and 0x000F
        if (rcode != 0) return emptyList()            // NXDOMAIN, SERVFAIL, ...

        val questionCount = buffer.short.toInt() and 0xFFFF
        val answerCount = buffer.short.toInt() and 0xFFFF
        buffer.short                                  // authority count
        buffer.short                                  // additional count

        try {
            repeat(questionCount) {
                skipName(buffer)
                buffer.short                          // qtype
                buffer.short                          // qclass
            }

            val addresses = mutableListOf<InetAddress>()
            repeat(answerCount) {
                skipName(buffer)
                val type = buffer.short.toInt() and 0xFFFF
                buffer.short                          // class
                buffer.int                            // ttl
                val dataLength = buffer.short.toInt() and 0xFFFF
                if (dataLength < 0 || dataLength > buffer.remaining()) return addresses

                when {
                    type == TYPE_A && dataLength == 4 -> {
                        val raw = ByteArray(4)
                        buffer.get(raw)
                        addresses.add(InetAddress.getByAddress(hostname, raw))
                    }
                    type == TYPE_AAAA && dataLength == 16 -> {
                        val raw = ByteArray(16)
                        buffer.get(raw)
                        addresses.add(InetAddress.getByAddress(hostname, raw))
                    }
                    else -> buffer.position(buffer.position() + dataLength)
                }
            }
            return addresses
        } catch (e: Exception) {
            ProtonLogger.d(TAG, "Malformed DoT response for $hostname: ${e.message}")
            return emptyList()
        }
    }

    /**
     * Advances past a name, following RFC 1035 compression pointers.
     *
     * A pointer ends the name in the wire stream, so the cursor is left just
     * after it. The jump budget stops a crafted message with a pointer loop
     * from spinning here forever.
     */
    private fun skipName(buffer: java.nio.ByteBuffer) {
        var jumps = 0
        while (buffer.hasRemaining()) {
            val length = buffer.get().toInt() and 0xFF
            when {
                length == 0 -> return
                length and 0xC0 == 0xC0 -> {
                    buffer.get()                      // second half of the pointer
                    if (++jumps > MAX_NAME_JUMPS) return
                    return
                }
                else -> buffer.position(buffer.position() + length)
            }
        }
    }
}
