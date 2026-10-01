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

package ru.protonmod.next

import android.content.Context
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.android.core.SentryAndroid
import retrofit2.HttpException
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.repository.ProtonApiException
import ru.protonmod.next.utils.PiiScrubber
import ru.protonmod.next.utils.ProtonLogger
import ru.protonmod.next.utils.SentryCrashReporter
import ru.protonmod.next.utils.SentryEventSanitizer
import ru.protonmod.next.vpn.SentryBridge
import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException

/**
 * Common initializer for the application (Sentry-enabled).
 */
object FlavorInitializer {
    @JvmStatic
    fun initializeOnMainThread(context: Context) {
        verifySecurityEnvironment(context)
    }

    @JvmStatic
    fun initialize(context: Context) {
        // Read settings synchronously
        val settingsManager = SettingsManager(context)
        val isAnalyticsEnabled = settingsManager.isAnalyticsEnabledSync()
        val isPerformanceEnabled = settingsManager.isPerformanceEnabledSync()
        val isSessionReplayEnabled = settingsManager.isSessionReplayEnabledSync()
        val isAnrEnabled = settingsManager.isAnrEnabledSync()
        val isMetricsEnabled = settingsManager.isMetricsEnabledSync()
        val isLogsEnabled = settingsManager.isLogsEnabledSync()

        // Sentry initialization
        SentryAndroid.init(context) { options ->
            options.dsn = SentryBridge.getSentryDsn()
            options.isDebug = BuildConfig.DEBUG

            options.isEnableNdk = false
            options.isEnableScopeSync = false
            options.isSendDefaultPii = false

            options.setBeforeSend { event, hint ->
                val currentCrashEnabled = settingsManager.isCrashReportsEnabledSync()
                val currentNonFatalEnabled = settingsManager.isNonFatalEnabledSync()
                
                if (event.isCrashed && !currentCrashEnabled) return@setBeforeSend null
                // Error reporting is independent from optional usage analytics.
                // Standard builds send crashes and handled errors by default while
                // breadcrumbs, logs, metrics, tracing and replay remain opt-in.
                if (!event.isCrashed && !currentNonFatalEnabled) return@setBeforeSend null

                // Filter out common network noise that is not actionable
                if (shouldFilterNetworkNoise(event, hint)) return@setBeforeSend null

                event.message?.let {
                    it.message = PiiScrubber.scrub(it.message)
                    it.formatted = PiiScrubber.scrub(it.formatted)
                    it.params = it.params?.map(PiiScrubber::scrub)
                }
                event.exceptions?.forEach { ex -> ex.value = PiiScrubber.scrub(ex.value) }
                SentryEventSanitizer.scrub(event)
                event.exceptions?.forEach { exception ->
                    exception.stacktrace?.frames?.forEach { frame -> frame.vars = null }
                }
                event
            }

            options.setBeforeBreadcrumb { breadcrumb, _ ->
                if (!settingsManager.isAnalyticsEnabledSync()) null
                else breadcrumb.apply { SentryEventSanitizer.scrubBreadcrumb(this) }
            }
            options.setBeforeSendTransaction { transaction, _ ->
                if (!settingsManager.isPerformanceEnabledSync() || PiiScrubber.scrub(transaction.transaction) != transaction.transaction) null
                else transaction.apply {
                    SentryEventSanitizer.scrub(this)
                    // Span descriptions are immutable in this SDK; discard sensitive spans.
                    transaction.spans.removeAll { it.description != null && PiiScrubber.scrub(it.description) != it.description }
                    transaction.spans.forEach { span ->
                        @Suppress("UNCHECKED_CAST")
                        val data = PiiScrubber.scrubValue(span.data) as? Map<String, Any>
                        span.data = data
                    }
                }
            }
            options.logs.setBeforeSend { log ->
                if (!settingsManager.isLogsEnabledSync()) null
                else log.apply { body = PiiScrubber.scrub(body); attributes = null }
            }

            options.tracesSampleRate = if (isPerformanceEnabled) 1.0 else 0.0
            options.profilesSampleRate = if (isPerformanceEnabled) 1.0 else 0.0
            options.isEnableAutoSessionTracking = isAnalyticsEnabled
            options.isAnrEnabled = isAnrEnabled
            options.isEnableAppStartProfiling = false
            options.isEnableUserInteractionTracing = isAnalyticsEnabled
            options.metrics.isEnabled = isMetricsEnabled
            options.logs.isEnabled = isLogsEnabled
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false

            if (isSessionReplayEnabled) {
                options.sessionReplay.sessionSampleRate = 1.0
                options.sessionReplay.onErrorSampleRate = 1.0
            } else {
                options.sessionReplay.sessionSampleRate = 0.0
                options.sessionReplay.onErrorSampleRate = 0.0
            }
        }

        // Set the Sentry reporter in ProtonLogger
        ProtonLogger.crashReporter = SentryCrashReporter()
    }

    /**
     * HTTP statuses the Proton API returns for situations the UI already handles and explains:
     * expired sessions, restricted access, wrong credentials, captcha/human-verification steps and
     * rate limiting. Reporting them buries real defects under user-driven noise.
     */
    private val EXPECTED_HTTP_CODES = setOf(400, 401, 403, 408, 409, 422, 429)

    /** Guards against a self-referencing cause chain. */
    private const val MAX_CAUSE_DEPTH = 16

    @JvmStatic
    private fun shouldFilterNetworkNoise(event: SentryEvent, hint: Hint): Boolean {
        var throwable = event.throwable ?: return false

        // Coroutines and Retrofit routinely wrap transport failures and cancellations, so the
        // interesting type is often a cause rather than the exception that was captured.
        var depth = 0
        while (depth++ < MAX_CAUSE_DEPTH) {
            if (isNetworkNoise(throwable)) return true
            throwable = throwable.cause ?: return false
        }
        return false
    }

    @JvmStatic
    private fun isNetworkNoise(throwable: Throwable): Boolean = when (throwable) {
        is HttpException -> throwable.code() in EXPECTED_HTTP_CODES
        is ProtonApiException -> throwable.code in EXPECTED_HTTP_CODES
        is SocketTimeoutException,
        is SocketException,
        is UnknownHostException,
        is SSLHandshakeException,
        is CancellationException,
        is EOFException -> true
        is IOException -> {
            val msg = throwable.message?.lowercase() ?: ""
            msg.contains("socket") || msg.contains("connection") || msg.contains("reset")
        }
        else -> false
    }

    @JvmStatic
    private fun verifySecurityEnvironment(context: Context) {
        // No-op honeypot
    }
}
