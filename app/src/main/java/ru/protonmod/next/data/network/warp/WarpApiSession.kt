/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.data.network.warp

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface WarpApiEngine : AutoCloseable {
    fun handshakeConfirmed(): Boolean
    fun stimulateHandshake()
}

internal sealed interface WarpApiState {
    data object Stopped : WarpApiState
    data object Starting : WarpApiState
    data class Ready(val proxy: WarpApiProxy) : WarpApiState
    data object Retrying : WarpApiState
    data object Failed : WarpApiState
}

internal data class WarpApiProxy(val port: Int, val username: String, val password: String)

/** One lifecycle coroutine per settings/network generation. Cancellation closes
 * the current native instance before a replacement is allowed to start. */
internal class WarpApiSession(private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val mutableState = MutableStateFlow<WarpApiState>(WarpApiState.Stopped)
    val state = mutableState.asStateFlow()
    fun stopped() { mutableState.value = WarpApiState.Stopped }
    fun failed() { mutableState.value = WarpApiState.Failed }
    suspend fun run(create: suspend () -> Pair<WarpApiEngine, WarpApiProxy>) {
        while (currentCoroutineContext().isActive) {
            mutableState.value = WarpApiState.Starting
            val started = nowMillis()
            try {
                val (engine, proxy) = create()
                try {
                    engine.stimulateHandshake()
                    val ready = withTimeoutOrNull(5_000) {
                        while (!engine.handshakeConfirmed()) delay(50)
                        true
                    } == true
                    if (ready) {
                        mutableState.value = WarpApiState.Ready(proxy)
                        awaitCancellation()
                    }
                } finally { mutableState.value = WarpApiState.Stopped; engine.close() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* bounded retry; never expose private config/native errors */ }
            mutableState.value = WarpApiState.Retrying
            // Failed starts must not create a tight CPU/network retry loop.
            val elapsed = nowMillis() - started
            delay((5_000 - elapsed).coerceAtLeast(0))
        }
    }
}
