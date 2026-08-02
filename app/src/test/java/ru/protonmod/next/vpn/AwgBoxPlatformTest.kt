package ru.protonmod.next.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AwgBoxPlatformTest {
    @Test
    fun `disabled split tunneling applies no app filter`() {
        val policy = splitTunnelingAppPolicy(false, "include", setOf("org.example.app"), "org.example.vpn")
        assertTrue(policy.allowedApps.isEmpty())
        assertTrue(policy.disallowedApps.isEmpty())
    }

    @Test
    fun `empty exclude keeps every app in the VPN`() {
        val policy = splitTunnelingAppPolicy(true, "exclude", emptySet(), "org.example.vpn")
        assertTrue(policy.allowedApps.isEmpty())
        assertTrue(policy.disallowedApps.isEmpty())
    }

    @Test
    fun `exclude bypasses only selected apps`() {
        val policy = splitTunnelingAppPolicy(true, "exclude", setOf("org.telegram.messenger"), "org.example.vpn")
        assertTrue(policy.allowedApps.isEmpty())
        assertEquals(setOf("org.telegram.messenger"), policy.disallowedApps)
    }

    @Test
    fun `include tunnels selected apps and the VPN process`() {
        val policy = splitTunnelingAppPolicy(true, "include", setOf("org.telegram.messenger"), "org.example.vpn")
        assertEquals(setOf("org.telegram.messenger", "org.example.vpn"), policy.allowedApps)
        assertTrue(policy.disallowedApps.isEmpty())
    }
}
