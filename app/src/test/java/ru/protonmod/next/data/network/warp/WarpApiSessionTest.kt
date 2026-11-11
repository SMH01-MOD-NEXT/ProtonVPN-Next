package ru.protonmod.next.data.network.warp

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*
import ru.protonmod.next.vpn.ImportedWireGuard
import ru.protonmod.next.data.network.WarpEndpoints

@OptIn(ExperimentalCoroutinesApi::class)
class WarpApiSessionTest {
    private class Engine(var confirmed: Boolean = false) : WarpApiEngine {
        var stimulated = 0; var closed = 0
        override fun handshakeConfirmed() = confirmed
        override fun stimulateHandshake() { stimulated++ }
        override fun close() { closed++ }
    }
    private val proxy = WarpApiProxy(12345, "user", "password")
    @Test fun `listener ready is not handshake and retry occurs after exactly five seconds`() = runTest {
        val session = WarpApiSession { testScheduler.currentTime }
        val engines = mutableListOf<Engine>()
        val job = backgroundScope.launch { session.run { Engine().also { engines += it } to proxy } }
        runCurrent(); assertEquals(1, engines.size); assertEquals(1, engines[0].stimulated)
        assertEquals(WarpApiState.Starting, session.state.value)
        advanceTimeBy(4_999); runCurrent(); assertEquals(1, engines.size)
        advanceTimeBy(1); runCurrent(); assertEquals(2, engines.size); assertEquals(1, engines[0].closed)
        job.cancelAndJoin(); assertEquals(1, engines[1].closed); assertEquals(WarpApiState.Stopped, session.state.value)
    }
    @Test fun `confirmed device is reused until cancellation with no periodic re-registration or restart`() = runTest {
        val session = WarpApiSession { testScheduler.currentTime }; val engine = Engine(true); var creates = 0
        val job = backgroundScope.launch { session.run { creates++; engine to proxy } }
        runCurrent(); assertEquals(WarpApiState.Ready(proxy), session.state.value)
        advanceTimeBy(60_000); runCurrent(); assertEquals(1, creates)
        job.cancelAndJoin(); assertEquals(1, engine.closed)
    }
    @Test fun `startup errors are throttled and never expose ready state`() = runTest {
        val session = WarpApiSession { testScheduler.currentTime }; var attempts = 0
        val job = backgroundScope.launch { session.run { attempts++; error("fixture") } }
        runCurrent(); assertEquals(1, attempts); assertEquals(WarpApiState.Retrying, session.state.value)
        advanceTimeBy(4_999); runCurrent(); assertEquals(1, attempts)
        advanceTimeBy(1); runCurrent(); assertEquals(2, attempts)
        job.cancelAndJoin()
    }
    @Test fun `all profiles have authenticated loopback only no TUN no direct outbound and tunneled DNS`() {
        val key = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
        val raw = "[Interface]\nPrivateKey = $key\nAddress = 172.16.0.2/32\nDNS = 1.1.1.1\n[Peer]\nPublicKey = $key\nEndpoint = 127.0.0.1:9\nAllowedIPs = 0.0.0.0/0\nReserved = 1, 2, 3\n"
        (0..3).forEach { profile ->
            val config = Json.parseToJsonElement(WarpApiConfig.build(ImportedWireGuard.encode(WarpEndpoints.apply(raw, profile = profile)), 12345, "u", "p")).jsonObject
            assertFalse(config.containsKey("outbounds")); assertFalse(config.containsKey("experimental"))
            val inbound = config.getValue("inbounds").jsonArray.single().jsonObject
            assertEquals("socks", inbound.getValue("type").jsonPrimitive.content)
            assertEquals("127.0.0.1", inbound.getValue("listen").jsonPrimitive.content)
            assertEquals("p", inbound.getValue("users").jsonArray.single().jsonObject.getValue("password").jsonPrimitive.content)
            assertEquals("warp-api", config.getValue("route").jsonObject.getValue("final").jsonPrimitive.content)
            assertEquals("warp-api", config.getValue("dns").jsonObject.getValue("servers").jsonArray.single().jsonObject.getValue("detour").jsonPrimitive.content)
        }
    }
}
