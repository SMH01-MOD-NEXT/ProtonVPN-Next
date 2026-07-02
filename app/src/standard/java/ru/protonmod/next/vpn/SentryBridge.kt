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

package ru.protonmod.next.vpn

import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message
import ru.protonmod.next.utils.ProtonLogger

/**
 * A bridge to retrieve Sentry configuration from native code and report events back.
 */
object SentryBridge {
    init {
        System.loadLibrary("next")
    }

    /**
     * Returns the XOR-protected Sentry DSN from native code.
     */
    fun getSentryDsn(): String = getSentryDsnNative()

    /**
     * Reports a security event to Sentry Android.
     * Called from native code.
     */
    @JvmStatic
    fun reportSecurityEvent(event: String) {
        val sentryEvent = SentryEvent().apply {
            message = Message().apply {
                message = event
            }
            level = SentryLevel.FATAL
            logger = "security"
            setTag("category", "security")
            setTag("tamper_detected", "true")
        }
        Sentry.captureEvent(sentryEvent)
        // Also ensure it goes to the Logs explorer
        ProtonLogger.e("Security", event)
    }

    /**
     * Flushes pending Sentry events and terminates the process.
     * Called from native code when a critical security violation (e.g. Frida) is detected.
     */
    @JvmStatic
    fun flushAndTerminate() {
        // Run flush and kill on a background thread to avoid blocking the main thread (ANR).
        Thread {
            // Give Sentry 3 seconds to deliver the security event before we die.
            Sentry.flush(3000)
            android.os.Process.killProcess(android.os.Process.myPid())
        }.start()
    }

    /**
     * Reports a log message to Sentry Android Logs explorer.
     * Called from native code.
     */
    @JvmStatic
    fun reportLog(level: Int, tag: String, message: String) {
        val sentryLevel = when (level) {
            2 -> SentryLevel.DEBUG
            3 -> SentryLevel.INFO
            4 -> SentryLevel.WARNING
            5 -> SentryLevel.ERROR
            6 -> SentryLevel.FATAL
            else -> SentryLevel.INFO
        }
        when (sentryLevel) {
            SentryLevel.DEBUG -> ProtonLogger.d(tag, message)
            SentryLevel.INFO -> ProtonLogger.i(tag, message)
            SentryLevel.WARNING -> ProtonLogger.w(tag, message)
            SentryLevel.ERROR -> ProtonLogger.e(tag, message)
            SentryLevel.FATAL -> ProtonLogger.e(tag, "[FATAL] $message")
        }
    }

    private external fun getSentryDsnNative(): String
}
