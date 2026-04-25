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
import io.sentry.Sentry
import io.sentry.Breadcrumb
import io.sentry.SentryLevel
import io.sentry.SentryLogLevel
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
     * Key = "$category:${message.take(60)}" to group near-duplicate messages.
     * ConcurrentHashMap so it is safe to access from multiple IO threads.
     */
    private val breadcrumbLastEmitted = ConcurrentHashMap<String, Long>()

    /**
     * Tracks the last time a Sentry log entry with a given dedup key was emitted.
     * Key = "$tag:${message.take(60)}" to group near-duplicate messages.
     * Mirrors breadcrumbLastEmitted to prevent the same high-frequency log flood
     * from saturating Sentry SDK internals and causing background ANRs.
     */
    private val sentryLogLastEmitted = ConcurrentHashMap<String, Long>()

    /**
     * Dedicated background scope for dispatching Sentry log calls off the calling thread.
     * Ensures Sentry.logger().log() (which acquires a scope lock) never blocks the
     * main/broadcast thread and cannot cause a background ANR.
     */
    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Controlled by SettingsManager at runtime/startup */
    var isNonFatalEnabled: Boolean = true
    /** Controlled by SettingsManager at runtime/startup */
    var isAnalyticsEnabled: Boolean = true
    /** Controlled by SettingsManager at runtime/startup */
    var isSentryLogsEnabled: Boolean = true

    /** Log at VERBOSE level */
    fun v(tag: String? = null, message: String, throwable: Throwable? = null) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val decoratedMsg = "[$threadName] $message"
        if (BuildConfig.DEBUG) {
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
        if (BuildConfig.DEBUG) {
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
        if (BuildConfig.DEBUG) {
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
        if (BuildConfig.DEBUG) {
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
        if (BuildConfig.DEBUG) {
            Log.i(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, message, SentryLevel.INFO)
        addSentryLog(finalTag, decoratedMsg, SentryLevel.INFO, throwable)
    }

    /** Log at INFO level with a lazy message lambda for better performance */
    inline fun i(tag: String? = null, throwable: Throwable? = null, crossinline message: () -> String) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val msg = message()
        val decoratedMsg = "[$threadName] $msg"
        if (BuildConfig.DEBUG) {
            Log.i(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, msg, SentryLevel.INFO)
        addSentryLog(finalTag, decoratedMsg, SentryLevel.INFO, throwable)
    }

    /** Log at WARN level */
    fun w(tag: String? = null, message: String, throwable: Throwable? = null) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val decoratedMsg = "[$threadName] $message"
        if (BuildConfig.DEBUG) {
            Log.w(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, message, SentryLevel.WARNING)
        addSentryLog(finalTag, decoratedMsg, SentryLevel.WARNING, throwable)
        if (throwable != null && isNonFatalEnabled) {
            Sentry.captureException(throwable)
        }
    }

    /** Log at WARN level with a lazy message lambda for better performance */
    inline fun w(tag: String? = null, throwable: Throwable? = null, crossinline message: () -> String) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val msg = message()
        val decoratedMsg = "[$threadName] $msg"
        if (BuildConfig.DEBUG) {
            Log.w(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, msg, SentryLevel.WARNING)
        addSentryLog(finalTag, decoratedMsg, SentryLevel.WARNING, throwable)
        if (throwable != null && isNonFatalEnabled) {
            Sentry.captureException(throwable)
        }
    }

    /** Log at ERROR level */
    fun e(tag: String? = null, message: String, throwable: Throwable? = null) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val decoratedMsg = "[$threadName] $message"
        if (BuildConfig.DEBUG) {
            Log.e(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, message, SentryLevel.ERROR)
        addSentryLog(finalTag, decoratedMsg, SentryLevel.ERROR, throwable)
        if (isNonFatalEnabled) {
            if (throwable != null) {
                Sentry.captureException(throwable)
            } else {
                Sentry.captureMessage(message, SentryLevel.ERROR)
            }
        }
    }

    /** Log at ERROR level with a lazy message lambda for better performance */
    inline fun e(tag: String? = null, throwable: Throwable? = null, crossinline message: () -> String) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val msg = message()
        val decoratedMsg = "[$threadName] $msg"
        if (BuildConfig.DEBUG) {
            Log.e(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, msg, SentryLevel.ERROR)
        addSentryLog(finalTag, decoratedMsg, SentryLevel.ERROR, throwable)
        if (isNonFatalEnabled) {
            if (throwable != null) {
                Sentry.captureException(throwable)
            } else {
                Sentry.captureMessage(msg, SentryLevel.ERROR)
            }
        }
    }

    /**
     * Records a user action as a breadcrumb.
     * Useful for tracking UI interactions and sequence of events leading to a crash.
     */
    fun action(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
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

        // Rate-limit repetitive breadcrumbs (e.g. high-frequency tunnel handshake/keepalive
        // log lines) to prevent saturating Dispatcher.IO threads and causing background ANRs.
        val dedupKey = "$category:${message.take(60)}"
        val now = System.currentTimeMillis()
        val last = breadcrumbLastEmitted[dedupKey]
        if (last != null && now - last < BREADCRUMB_RATE_LIMIT_MS) {
            return // Drop this breadcrumb; an identical one was emitted very recently
        }
        breadcrumbLastEmitted[dedupKey] = now

        val breadcrumb = Breadcrumb().apply {
            this.category = if (category == "log.message") tag else category
            this.message = message
            this.level = level
            if (category != "log.message") {
                this.setData("tag", tag)
            }
        }
        Sentry.addBreadcrumb(breadcrumb)
    }

    /**
     * Forwards a log entry to the Sentry Logs API (requires options.logs.isEnabled = true).
     * This feeds the real-time "Logs" explorer in Sentry, separate from breadcrumbs.
     *
     * Rate-limited (same interval as breadcrumbs) to drop high-frequency duplicate log lines
     * and dispatched asynchronously on [logScope] so the Sentry SDK's internal scope-lock
     * acquisition (Scope.getSpan) never blocks the calling thread — preventing background ANRs.
     */
    @PublishedApi
    internal fun addSentryLog(tag: String, message: String, level: SentryLevel, throwable: Throwable? = null) {
        if (!isAnalyticsEnabled || !isSentryLogsEnabled) return

        // Rate-limit: drop near-duplicate log lines emitted faster than BREADCRUMB_RATE_LIMIT_MS.
        val dedupKey = "$tag:${message.take(60)}"
        val now = System.currentTimeMillis()
        val last = sentryLogLastEmitted[dedupKey]
        if (last != null && now - last < BREADCRUMB_RATE_LIMIT_MS) {
            return // Drop; an identical entry was emitted very recently.
        }
        sentryLogLastEmitted[dedupKey] = now

        val fullMessage = "[$tag] $message"
        val logLevel = when (level) {
            SentryLevel.DEBUG -> SentryLogLevel.DEBUG
            SentryLevel.INFO -> SentryLogLevel.INFO
            SentryLevel.WARNING -> SentryLogLevel.WARN
            SentryLevel.ERROR -> SentryLogLevel.ERROR
            SentryLevel.FATAL -> SentryLogLevel.FATAL
        }

        // Dispatch off the calling thread so Sentry.logger().log() (which acquires a
        // scope lock) cannot stall the main/broadcast thread and trigger a background ANR.
        logScope.launch {
            // Use the official Sentry Logs API (v8.12.0+)
            // This ensures logs are sent to the "Logs" explorer in both debug and release.
            if (throwable != null) {
                Sentry.logger().log(logLevel, "$fullMessage: ${throwable.message}", throwable)
            } else {
                Sentry.logger().log(logLevel, fullMessage)
            }
        }
    }

    /**
     * Automatically extracts the class name from the stack trace to use as a tag.
     */
    @PublishedApi
    internal fun getAutoTag(): String {
        val stackTrace = Thread.currentThread().stackTrace
        return if (stackTrace.size > CALL_STACK_INDEX) {
            val element = stackTrace[CALL_STACK_INDEX]
            val className = element.className.substringAfterLast('.')
            // If the caller is an anonymous class or lambda, cleanup the name
            className.substringBefore('$')
        } else {
            DEFAULT_TAG
        }
    }
}
