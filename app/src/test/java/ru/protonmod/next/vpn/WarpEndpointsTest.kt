package ru.protonmod.next.vpn

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import ru.protonmod.next.data.network.WarpEndpoints

class WarpEndpointsTest {
    private val key = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
    private val config get() = "[Interface]\nPrivateKey = $key\nAddress = 172.16.0.2/32\nDNS = 1.1.1.1\n[Peer]\nPublicKey = $key\nEndpoint = engage.cloudflareclient.com:2408\nAllowedIPs = 0.0.0.0/0\nReserved = 1, 2, 3\n"
    @Test fun `catalog contains exactly published candidates not MASQUE ports`() {
        assertEquals(54, WarpEndpoints.ports.size)
        assertEquals(54, WarpEndpoints.ports.toSet().size)
        assertEquals(listOf(2408,500,1701,4500), WarpEndpoints.officialPorts)
        assertEquals(256, WarpEndpoints.officialIps.size)
        assertEquals(140, WarpEndpoints.communityIps.size)
        assertFalse(WarpEndpoints.ports.contains(443))
        assertTrue(runCatching { WarpEndpoints.validate("127.0.0.1", 2408) }.isFailure)
        assertTrue(runCatching { WarpEndpoints.validate("162.159.193.1", 8443) }.isFailure)
    }
    @Test fun `all listed ports preserve identity and standard WG reserved bytes`() {
        WarpEndpoints.ports.forEach { port ->
            val value = WarpEndpoints.apply(config, "162.159.193.1", port)
            assertTrue(value.contains("Endpoint = 162.159.193.1:$port"))
            assertTrue(value.contains("PrivateKey = $key")); assertTrue(value.contains("Reserved = 1, 2, 3"))
        }
    }
    @Test fun `AWG2 uses fresh QUIC masking and standard WireGuard header numbers`() {
        val a = WarpEndpoints.apply(config, awg2 = true)
        val b = WarpEndpoints.apply(config, awg2 = true)
        val node = ImportedWireGuard.parse(a, "warp").outbound
        assertEquals("awg", node.getValue("type").jsonPrimitive.content)
        assertEquals("4", node.getValue("jc").jsonPrimitive.content)
        assertEquals("40", node.getValue("jmin").jsonPrimitive.content)
        assertEquals("70", node.getValue("jmax").jsonPrimitive.content)
        (1..4).forEach { assertEquals("$it", node.getValue("h$it").jsonPrimitive.content); assertEquals("0",node.getValue("s$it").jsonPrimitive.content) }
        assertFalse(a.contains("Reserved")); assertTrue(a.contains("I1 = <b 0x")); assertNotEquals(a,b)
    }
}
