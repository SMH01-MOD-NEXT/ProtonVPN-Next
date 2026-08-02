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

import android.app.Application
import android.webkit.WebView
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import okhttp3.OkHttp
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.network.SessionRefreshWorker
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.ota.OTAUpdateManager
import ru.protonmod.next.utils.NetworkMonitor
import ru.protonmod.next.utils.ProtonLogger
import ru.protonmod.next.vpn.VpnAutomationManager
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Main Application class for Proton VPN-Next.
 * The @HiltAndroidApp annotation triggers Hilt's code generation,
 * including a base class for your application that serves as the
 * application-level dependency container.
 */
@HiltAndroidApp
class ProtonNextApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var vpnRepository: Lazy<VpnRepository>

    @Inject
    lateinit var otaUpdateManager: Lazy<OTAUpdateManager>

    @Inject
    lateinit var networkMonitor: Lazy<NetworkMonitor>

    @Inject
    lateinit var vpnAutomationManager: Lazy<VpnAutomationManager>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        instance = this
        
        // Multi-process WebView support for API 28+.
        // This ensures the application doesn't crash if WebView is initialized in the :vpn process.
        try {
            val processName = getProcessName()
            if (packageName != processName) {
                WebView.setDataDirectorySuffix(processName.substringAfterLast(':'))
            }
        } catch (e: Exception) {
            // Might have been set already by another component
        }
        
        // Initialize OkHttp with context to avoid "Unable to load PublicSuffixDatabase"
        // in multi-process environments when using DnsOverHttps.
        try {
            OkHttp.initialize(this)
        } catch (e: Exception) {
            // Fallback for OkHttp 4.x where this method doesn't exist
        }

        // Run the honeypot security check synchronously on the main thread.
        FlavorInitializer.initializeOnMainThread(this)

        // Enable Nothing widget on Nothing devices
        if (ru.protonmod.next.utils.system.SystemUtils.isNothingDevice()) {
            val componentName = android.content.ComponentName(this, ru.protonmod.next.ui.widget.VpnNothingWidgetProvider::class.java)
            packageManager.setComponentEnabledSetting(
                componentName,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        }

        // Initialize Sentry on a background thread to avoid blocking the main thread.
        // SentryAndroid.init() calls initializeIntegrationsAndProcessors which performs
        // blocking I/O and was causing a Background ANR (see ANDROID-1GV).
        // The Sentry SDK queues any events captured before init completes, so nothing is lost.
        MainScope().launch(Dispatchers.IO) {
            FlavorInitializer.initialize(this@ProtonNextApp)
        }

        // Initialize logger settings from sync storage
        val settings = SettingsManager(this)
        ProtonLogger.isNonFatalEnabled = settings.isNonFatalEnabledSync()
        ProtonLogger.isAnalyticsEnabled = settings.isAnalyticsEnabledSync()
        ProtonLogger.isSentryLogsEnabled = settings.isLogsEnabledSync()

        val isMainProcess = try {
            packageName == getProcessName()
        } catch (e: Exception) {
            true
        }

        if (isMainProcess) {
            // Instantiate main-process-only graphs here. Keeping them Lazy prevents the
            // dedicated :vpn process from opening Room during Application injection.
            vpnAutomationManager.get()
            vpnRepository.get().startAutoUpdate()

            // Schedule background session maintenance
            SessionRefreshWorker.schedule(this)

            // Sync servers on network changes.
            // Debounce by 2 s so rapid connectivity toggles (e.g. Wi-Fi → mobile → Wi-Fi)
            // collapse into a single refresh, preventing multiple concurrent forced fetches
            // that would exhaust the heap with large API payloads (OOM in loads deserialization).
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            MainScope().launch {
                networkMonitor.get().networkChanged.debounce(2_000.milliseconds).collect { timestamp ->
                    if (timestamp > 0) {
                        vpnRepository.get().refreshServersOnNetworkChange()
                    }
                }
            }

            // Schedule OTA update checks
            MainScope().launch {
                otaUpdateManager.get().scheduleUpdateCheck()
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        try {
            if (packageName == getProcessName()) {
                vpnRepository.get().stopAutoUpdate()
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    companion object {
        lateinit var instance: ProtonNextApp
            private set
    }
}
