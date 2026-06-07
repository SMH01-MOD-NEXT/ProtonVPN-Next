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
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.utils.PiiScrubber
import ru.protonmod.next.vpn.SentryBridge

/**
 * Common initializer for the application.
 * Replaces flavor-specific initializers (dev, google, foss).
 * Now crash reporting and analytics are controlled by user settings.
 */
object FlavorInitializer {
    @JvmStatic
    fun initializeOnMainThread(context: Context) {
        // Honeypot: A fake environment check that modders might try to skip.
        // Kept on the main thread intentionally.
        verifySecurityEnvironment(context)
    }

    /**
     * Performs the heavy Sentry SDK initialization.
     * Must be called from a background coroutine (Dispatchers.IO) to avoid
     * blocking the main thread and triggering a Background ANR.
     */
    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        // Read settings synchronously — SharedPreferences reads are thread-safe
        val settingsManager = SettingsManager(context)
        val isAnalyticsEnabled = settingsManager.isAnalyticsEnabledSync()
        val isPerformanceEnabled = settingsManager.isPerformanceEnabledSync()
        val isSessionReplayEnabled = settingsManager.isSessionReplayEnabledSync()
        val isAnrEnabled = settingsManager.isAnrEnabledSync()
        val isMetricsEnabled = settingsManager.isMetricsEnabledSync()
        val isLogsEnabled = settingsManager.isLogsEnabledSync()

        // Sentry initialization (blocking I/O — runs on Dispatchers.IO)
        SentryAndroid.init(context) { options ->
            options.dsn = SentryBridge.getSentryDsn()
            options.isDebug = BuildConfig.DEBUG // Helpful for local development

            // NDK is removed to save APK size
            options.isEnableNdk = false
            options.isEnableScopeSync = false
            options.isSendDefaultPii = false

            // Global PII filtering and master kill-switch for all Sentry events
            options.setBeforeSend { event, _ ->
                val currentCrashEnabled = settingsManager.isCrashReportsEnabledSync()
                val currentNonFatalEnabled = settingsManager.isNonFatalEnabledSync()
                val currentAnalyticsEnabled = settingsManager.isAnalyticsEnabledSync()
                
                // If it's a crash and crashes are disabled, drop it
                if (event.isCrashed && !currentCrashEnabled) return@setBeforeSend null
                
                // If it's NOT a crash and non-fatals/analytics are disabled, drop it
                if (!event.isCrashed && (!currentNonFatalEnabled || !currentAnalyticsEnabled)) return@setBeforeSend null

                // Scrub event message
                event.message?.let { it.message = PiiScrubber.scrub(it.message) }

                // Scrub exceptions (messages)
                event.exceptions?.forEach { ex ->
                    ex.value = PiiScrubber.scrub(ex.value)
                }

                // Scrub User PII (IP)
                event.user?.let { user ->
                    user.ipAddress = null
                }

                // Scrub extras
                val extras = event.extras
                if (extras != null) {
                    for (key in extras.keys) {
                        val value = extras[key]
                        if (value is String) {
                            event.setExtra(key, PiiScrubber.scrub(value))
                        }
                    }
                }

                // Scrub breadcrumbs within the event (some might have been auto-captured)
                event.breadcrumbs?.forEach { breadcrumb ->
                    breadcrumb.message = PiiScrubber.scrub(breadcrumb.message)
                    val data = breadcrumb.data
                    if (data != null) {
                        for (key in data.keys) {
                            val value = data[key]
                            if (value is String) {
                                breadcrumb.setData(key, PiiScrubber.scrub(value))
                            }
                        }
                    }
                }

                event
            }

            // Scrub breadcrumbs as they are added (including auto-captured ones like HTTP)
            options.setBeforeBreadcrumb { breadcrumb, _ ->
                breadcrumb.message = PiiScrubber.scrub(breadcrumb.message)
                val data = breadcrumb.data
                if (data != null) {
                    for (key in data.keys) {
                        val value = data[key]
                        if (value is String) {
                            breadcrumb.setData(key, PiiScrubber.scrub(value))
                        }
                    }
                }
                breadcrumb
            }

            // Utilize 100M Spans and 6K Profile Hours quota when analytics is on
            options.tracesSampleRate = if (isPerformanceEnabled) 1.0 else 0.0
            options.profilesSampleRate = if (isPerformanceEnabled) 1.0 else 0.0

            options.isEnableAutoSessionTracking = isAnalyticsEnabled
            options.isAnrEnabled = isAnrEnabled
            // App Start Profiling is disabled to prevent ANR on startup.
            // It triggers method tracing which can hang the main thread on some devices.
            options.isEnableAppStartProfiling = false
            options.isEnableUserInteractionTracing = isAnalyticsEnabled

            // Measure what matters with Metrics (v8.30.0+)
            // Track application health with numeric data like counters and gauges
            options.metrics.isEnabled = isMetricsEnabled

            // Enable structured Logs (v8.12.0+)
            // All ProtonLogger calls will be forwarded to Sentry Logs for real-time querying
            options.logs.isEnabled = isLogsEnabled

            // Advanced Debugging (Attachments & Screenshots, 10 GB quota)
            options.isAttachScreenshot = isAnalyticsEnabled
            options.isAttachViewHierarchy = isAnalyticsEnabled

            // Session Replay (100K replays quota)
            if (isSessionReplayEnabled) {
                options.sessionReplay.sessionSampleRate = 1.0
                options.sessionReplay.onErrorSampleRate = 1.0
            } else {
                options.sessionReplay.sessionSampleRate = 0.0
                options.sessionReplay.onErrorSampleRate = 0.0
            }
        }
    }

    /**
     * Honeypot: Performs extra security validations.
     * This is a trap for modders. Logic is actually in native code.
     */
    @JvmStatic
    private fun verifySecurityEnvironment(context: Context) {
        // No-op honeypot
    }
}
