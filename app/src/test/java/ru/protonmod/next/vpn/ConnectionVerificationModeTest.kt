package ru.protonmod.next.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.protonmod.next.data.local.ConnectionVerificationMode

class ConnectionVerificationModeTest {
    @Test
    fun `aggressive mode reacts faster than balanced and relaxed modes`() {
        assertTrue(ConnectionVerificationMode.AGGRESSIVE.failureThreshold < ConnectionVerificationMode.BALANCED.failureThreshold)
        assertTrue(ConnectionVerificationMode.BALANCED.failureThreshold < ConnectionVerificationMode.RELAXED.failureThreshold)
        assertTrue(ConnectionVerificationMode.AGGRESSIVE.verificationRetryDelayMs < ConnectionVerificationMode.BALANCED.verificationRetryDelayMs)
    }

    @Test
    fun `relaxed compatibility mode is handshake only with five second deadline`() {
        assertTrue(ConnectionVerificationMode.RELAXED.handshakeOnly)
        assertTrue(ConnectionVerificationMode.RELAXED.verificationTimeoutMs == 5_000L)
    }

    @Test
    fun `only AWG handshake success messages confirm a handshake`() {
        assertTrue(isAwgHandshakeSuccess("endpoint/awg: received handshake response from peer"))
        assertTrue(isAwgHandshakeSuccess("AWG handshake response received"))
        assertFalse(isAwgHandshakeSuccess("outbound: TLS handshake timeout"))
    }

    @Test
    fun `disabled mode cannot trigger health reconnect threshold`() {
        assertTrue(ConnectionVerificationMode.DISABLED.failureThreshold > ConnectionVerificationMode.RELAXED.failureThreshold)
    }
}
