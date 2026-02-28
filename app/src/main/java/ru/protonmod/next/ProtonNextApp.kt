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
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttp
import ru.protonmod.next.data.cache.ServersCacheManager
import javax.inject.Inject

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
    lateinit var serversCacheManager: ServersCacheManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        // Initialize OkHttp with context to avoid "Unable to load PublicSuffixDatabase"
        // in multi-process environments when using DnsOverHttps.
        try {
            OkHttp.initialize(this)
        } catch (e: Exception) {
            // Fallback for OkHttp 4.x where this method doesn't exist
        }

        // Initialize flavor-specific components (e.g., Firebase for Google flavor)
        FlavorInitializer.initialize(this)

        // Start background server load updates
        serversCacheManager.startAutoUpdate()
    }

    override fun onTerminate() {
        super.onTerminate()
        serversCacheManager.stopAutoUpdate()
    }
}
