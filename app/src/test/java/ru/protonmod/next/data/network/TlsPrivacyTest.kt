/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.data.network

import java.net.Socket
import java.security.PublicKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSession
import javax.net.ssl.X509ExtendedTrustManager
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class TlsPrivacyTest {
    private fun certificate(bytes: ByteArray): X509Certificate {
        val key = mock<PublicKey> { on { encoded } doReturn bytes }
        return mock { on { publicKey } doReturn key }
    }
    @Test fun `leaf pin is required not a matching certificate later in chain`() {
        val leaf = certificate(byteArrayOf(1))
        val other = certificate(byteArrayOf(2))
        val session = mock<SSLSession> { on { peerCertificates } doReturn arrayOf(leaf, other) }
        assertFalse(PinVerifier.check(session, listOf(MirrorTrustManager.spkiPin(other))))
        assertTrue(PinVerifier.check(session, listOf(MirrorTrustManager.spkiPin(leaf))))
        assertFalse(PinVerifier.check(session, emptyList()))
    }
    @Test fun `system rejection of an unpinned leaf is propagated by every overload`() {
        val system = mock<X509ExtendedTrustManager>()
        val chain = arrayOf(certificate(byteArrayOf(1)))
        val socket = mock<Socket>()
        val engine = mock<SSLEngine>()
        doThrow(CertificateException("untrusted")).whenever(system).checkServerTrusted(chain, "RSA")
        doThrow(CertificateException("untrusted")).whenever(system).checkServerTrusted(chain, "RSA", socket)
        doThrow(CertificateException("untrusted")).whenever(system).checkServerTrusted(chain, "RSA", engine)
        val manager = MirrorTrustManager(system)
        for (check in listOf<() -> Unit>(
            { manager.checkServerTrusted(chain, "RSA") },
            { manager.checkServerTrusted(chain, "RSA", socket) },
            { manager.checkServerTrusted(chain, "RSA", engine) },
        )) {
            try { check(); fail("Untrusted leaf accepted") } catch (_: CertificateException) { }
        }
    }
    @Test fun `system trusted chain still works`() {
        val system = mock<X509ExtendedTrustManager>()
        val chain = arrayOf(certificate(byteArrayOf(1)))
        MirrorTrustManager(system).checkServerTrusted(chain, "RSA")
        verify(system).checkServerTrusted(chain, "RSA")
    }
}
