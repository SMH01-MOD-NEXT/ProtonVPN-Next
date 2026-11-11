package ru.protonmod.next.vpn

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ImportedWireGuardTest {
    private val key = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
    private fun config(extra: String = "", peerExtra: String = "") = """
        [Interface]
        PrivateKey = $key
        Address = 172.16.0.2/32, 2606:4700::2/128
        DNS = 1.1.1.1
        $extra
        [Peer]
        PublicKey = $key
        Endpoint = engage.cloudflareclient.com:2408
        AllowedIPs = 0.0.0.0/0, ::/0
        $peerExtra
    """.trimIndent()
    @Test fun `WARP reserved bytes use standard WireGuard endpoint not AWG`() {
        val text = config(peerExtra = "Reserved = 1, 2, 255")
        val link = ImportedWireGuard.encode(text)
        assertEquals(text, ImportedWireGuard.decode(link))
        val proxy = ProxyLinkParser.parseChain(link).single().outbound
        assertEquals("wireguard", proxy.getValue("type").jsonPrimitive.content)
        assertFalse(proxy.containsKey("useIntegratedTun"))
        val peer = proxy.getValue("peers").jsonArray.single().jsonObject
        assertEquals("255", peer.getValue("reserved").jsonArray.last().jsonPrimitive.content)
        assertEquals("engage.cloudflareclient.com", ProxyLinkParser.inspectLink(link).server)
        assertEquals(2408, ProxyLinkParser.inspectLink(link).port)
    }
    @Test fun `AWG directives select AWG and its own preshared key field`() {
        val node = ImportedWireGuard.parse(config("Jc = 4", "PresharedKey = $key"), "hop").outbound
        assertEquals("awg", node.getValue("type").jsonPrimitive.content)
        assertTrue(node.getValue("peers").jsonArray.single().jsonObject.containsKey("preshared_key"))
        assertEquals("4", node.getValue("jc").jsonPrimitive.content)
    }
    @Test fun `shell hooks duplicate peers invalid keys and malformed addresses fail closed`() {
        listOf(config("PostUp = sh -c bad"), config().replace("172.16.0.2/32", "999.1.1.1/32"),
            config() + "\n[Peer]\nPublicKey = $key", config().replace(key, "bad"),
            config(peerExtra = "Reserved = 256, 0, 0"), config(peerExtra = "Reserved = 1, 2"),
            config("Jc = 4", "Reserved = 1, 2, 3"), config().replace(":2408", ":0"),
            config().replace("0.0.0.0/0", "0.0.0.0/33")
        ).forEach { assertTrue(runCatching { ImportedWireGuard.encode(it) }.isFailure) }
    }
    @Test fun `chain retains UDP endpoint and links detour tags`() {
        val link = ImportedWireGuard.encode(config())
        val chain = ProxyLinkParser.parseChain("vless://00000000-0000-0000-0000-000000000001@example.com:443\n$link")
        assertEquals("proxy-2", chain.first().outbound.getValue("detour").jsonPrimitive.content)
        assertEquals("wireguard", chain.last().outbound.getValue("type").jsonPrimitive.content)
    }
}
