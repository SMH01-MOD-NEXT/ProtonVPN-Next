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

package ru.protonmod.next.utils

import android.util.Log
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.protonmod.next.BuildConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * A professional logging wrapper for Proton VPN-Next.
 *
 * Automatically handles debug/release logic, tag generation from stack trace,
 * and integrates with Sentry for remote diagnostics.
 *
 * Uses the standard Android Sentry SDK for all app-level telemetry:
 *  - INFO/DEBUG/VERBOSE → breadcrumbs + Sentry Logs (never creates issues/events)
 *  - WARN (with throwable) / ERROR → breadcrumbs + Sentry Logs + captureMessage (creates an issue)
 *
 * The native JNI Sentry functions are intentionally left in this file but are ONLY
 * used by security/anti-tamper code paths, not by any app logging methods.
 */
object ProtonLogger {

    private const val DEFAULT_TAG = "ProtonVPN"
    private const val CALL_STACK_INDEX = 4

    /**
     * Minimum interval (ms) between breadcrumbs with the same category+message prefix.
     * Prevents high-frequency tunnel log loops from flooding the Sentry breadcrumb buffer
     * and saturating DefaultDispatcher worker threads (which causes background ANRs).
     */
    private const val BREADCRUMB_RATE_LIMIT_MS = 1_000L

    /**
     * Tracks the last time a breadcrumb with a given dedup key was emitted.
     */
    private val breadcrumbLastEmitted = ConcurrentHashMap<String, Long>()

    /**
     * Tracks the last time a Sentry log entry with a given dedup key was emitted.
     */
    private val sentryLogLastEmitted = ConcurrentHashMap<String, Long>()

    /**
     * Dedicated background scope for dispatching Sentry log calls off the calling thread.
     */
    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Controlled by SettingsManager at runtime/startup */
    var isNonFatalEnabled: Boolean = true
    /** Controlled by SettingsManager at runtime/startup */
    var isAnalyticsEnabled: Boolean = true
    /** Controlled by SettingsManager at runtime/startup */
    var isSentryLogsEnabled: Boolean = true
    /** Global toggle for logcat output. Can be overridden in release builds. */
    var isLogcatEnabled: Boolean = BuildConfig.DEBUG

    /** Log at VERBOSE level */
    fun v(tag: String? = null, message: String, throwable: Throwable? = null) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val decoratedMsg = "[$threadName] $message"
        if (isLogcatEnabled) {
            Log.v(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, message, SentryLevel.DEBUG)
        addSentryLog(finalTag, decoratedMsg, SentryLevel.DEBUG, throwable)
    }

    /** Log at VERBOSE level with a lazy message lambda for better performance */
    inline fun v(tag: String? = null, throwable: Throwable? = null, crossinline message: () -> String) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val msg = message()
        val decoratedMsg = "[$threadName] $msg"
        if (isLogcatEnabled) {
            Log.v(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, msg, SentryLevel.DEBUG)
        addSentryLog(finalTag, decoratedMsg, SentryLevel.DEBUG, throwable)
    }

    /** Log at DEBUG level */
    fun d(tag: String? = null, message: String, throwable: Throwable? = null) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val decoratedMsg = "[$threadName] $message"
        if (isLogcatEnabled) {
            Log.d(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, message, SentryLevel.DEBUG)
        addSentryLog(finalTag, decoratedMsg, SentryLevel.DEBUG, throwable)
    }

    /** Log at DEBUG level with a lazy message lambda for better performance */
    inline fun d(tag: String? = null, throwable: Throwable? = null, crossinline message: () -> String) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val msg = message()
        val decoratedMsg = "[$threadName] $msg"
        if (isLogcatEnabled) {
            Log.d(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, msg, SentryLevel.DEBUG)
        addSentryLog(finalTag, decoratedMsg, SentryLevel.DEBUG, throwable)
    }

    /** Log at INFO level */
    fun i(tag: String? = null, message: String, throwable: Throwable? = null) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val decoratedMsg = "[$threadName] $message"
        if (isLogcatEnabled) {
            Log.i(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, message, SentryLevel.INFO)
        addSentryLog(finalTag, decoratedMsg, SentryLevel.INFO, throwable)
        // INFO does NOT call captureMessage — it must not create Sentry issues/events.
    }

    /** Log at INFO level with a lazy message lambda for better performance */
    inline fun i(tag: String? = null, throwable: Throwable? = null, crossinline message: () -> String) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val msg = message()
        val decoratedMsg = "[$threadName] $msg"
        if (isLogcatEnabled) {
            Log.i(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, msg, SentryLevel.INFO)
        addSentryLog(finalTag, decoratedMsg, SentryLevel.INFO, throwable)
        // INFO does NOT call captureMessage — it must not create Sentry issues/events.
    }

    /** Log at WARN level */
    fun w(tag: String? = null, message: String, throwable: Throwable? = null) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val decoratedMsg = "[$threadName] $message"
        if (isLogcatEnabled) {
            Log.w(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, message, SentryLevel.WARNING)
        addSentryLog(finalTag, decoratedMsg, SentryLevel.WARNING, throwable)
        if (throwable != null && isNonFatalEnabled) {
            captureAppMessage("WARN: $decoratedMsg\n${Log.getStackTraceString(throwable)}", SentryLevel.WARNING)
        }
    }

    /** Log at WARN level with a lazy message lambda for better performance */
    inline fun w(tag: String? = null, throwable: Throwable? = null, crossinline message: () -> String) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val msg = message()
        val decoratedMsg = "[$threadName] $msg"
        if (isLogcatEnabled) {
            Log.w(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, msg, SentryLevel.WARNING)
        addSentryLog(finalTag, decoratedMsg, SentryLevel.WARNING, throwable)
        if (throwable != null && isNonFatalEnabled) {
            captureAppMessage("WARN: $decoratedMsg\n${Log.getStackTraceString(throwable)}", SentryLevel.WARNING)
        }
    }

    /** Log at ERROR level */
    fun e(tag: String? = null, message: String, throwable: Throwable? = null) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val decoratedMsg = "[$threadName] $message"
        if (isLogcatEnabled) {
            Log.e(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, message, SentryLevel.ERROR)
        addSentryLog(finalTag, decoratedMsg, SentryLevel.ERROR, throwable)
        if (isNonFatalEnabled) {
            val fullMsg = if (throwable != null) "$decoratedMsg\n${Log.getStackTraceString(throwable)}" else decoratedMsg
            captureAppMessage(fullMsg, SentryLevel.ERROR)
        }
    }

    /** Log at ERROR level with a lazy message lambda for better performance */
    inline fun e(tag: String? = null, throwable: Throwable? = null, crossinline message: () -> String) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val msg = message()
        val decoratedMsg = "[$threadName] $msg"
        if (isLogcatEnabled) {
            Log.e(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, msg, SentryLevel.ERROR)
        addSentryLog(finalTag, decoratedMsg, SentryLevel.ERROR, throwable)
        if (isNonFatalEnabled) {
            val fullMsg = if (throwable != null) "$decoratedMsg\n${Log.getStackTraceString(throwable)}" else decoratedMsg
            captureAppMessage(fullMsg, SentryLevel.ERROR)
        }
    }

    /**
     * Records a user action as a breadcrumb.
     * Useful for tracking UI interactions and sequence of events leading to a crash.
     */
    fun action(tag: String, message: String) {
        if (isLogcatEnabled) {
            Log.d(tag, "[ACTION] $message")
        }
        addSentryBreadcrumb(tag, message, SentryLevel.INFO, category = "ui.action")
    }

    /**
     * Professional error logging that accepts a message and an optional throwable.
     */
    fun error(tag: String? = null, message: String, throwable: Throwable? = null) {
        e(tag, message, throwable)
    }

    @PublishedApi
    internal fun addSentryBreadcrumb(
        tag: String,
        message: String,
        level: SentryLevel,
        category: String = "log.message"
    ) {
        if (!isAnalyticsEnabled) return

        val dedupKey = "$category:${message.take(60)}"
        val now = System.currentTimeMillis()
        val last = breadcrumbLastEmitted[dedupKey]
        if (last != null && now - last < BREADCRUMB_RATE_LIMIT_MS) {
            return
        }
        breadcrumbLastEmitted[dedupKey] = now

        val breadcrumb = Breadcrumb().apply {
            this.category = if (category == "log.message") tag else category
            this.message = message
            this.level = level
        }
        Sentry.addBreadcrumb(breadcrumb)
    }

    /**
     * Forwards a log entry to the Sentry Logs API (requires options.logs.isEnabled = true).
     * This feeds the real-time "Logs" explorer in Sentry, separate from breadcrumbs.
     * Never creates Sentry issues/events regardless of level.
     */
    @PublishedApi
    internal fun addSentryLog(tag: String, message: String, level: SentryLevel, throwable: Throwable? = null) {
        if (!isAnalyticsEnabled || !isSentryLogsEnabled) return

        val dedupKey = "$tag:${message.take(60)}"
        val now = System.currentTimeMillis()
        val last = sentryLogLastEmitted[dedupKey]
        if (last != null && now - last < BREADCRUMB_RATE_LIMIT_MS) {
            return
        }
        sentryLogLastEmitted[dedupKey] = now

        val fullMessage = if (throwable != null) {
            "[$tag] $message: ${throwable.message}\n${Log.getStackTraceString(throwable)}"
        } else {
            "[$tag] $message"
        }

        logScope.launch {
            Sentry.logger().log(level, fullMessage)
        }
    }

    /**
     * Captures a message as a Sentry issue/event using the standard Android Sentry SDK.
     * Only called for WARN (with throwable) and ERROR levels.
     */
    @PublishedApi
    internal fun captureAppMessage(message: String, level: SentryLevel) {
        logScope.launch {
            Sentry.captureMessage(message, level)
        }
    }

    // -------------------------------------------------------------------------
    // Native JNI Sentry functions — reserved EXCLUSIVELY for security /
    // anti-tamper code paths. Do NOT call these from any app logging methods.
    // -------------------------------------------------------------------------

    @JvmStatic
    external fun nativeAddBreadcrumb(category: String, message: String, level: Int)

    @JvmStatic
    external fun nativeCaptureMessage(message: String, level: Int)

    /**
     * Automatically extracts the class name from the stack trace to use as a tag.
     */
    @PublishedApi
    internal fun getAutoTag(): String {
        val stackTrace = Thread.currentThread().stackTrace
        return if (stackTrace.size > CALL_STACK_INDEX) {
            val element = stackTrace[CALL_STACK_INDEX]
            val className = element.className.substringAfterLast('.')
            className.substringBefore('$')
        } else {
            DEFAULT_TAG
        }
    }
}
