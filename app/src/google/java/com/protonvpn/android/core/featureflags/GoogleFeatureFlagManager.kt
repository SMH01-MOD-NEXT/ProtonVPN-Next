/*
 * Copyright (c) 2026 Proton VPN NOD-Next.
 */

package com.protonvpn.android.core.featureflags

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await
import me.proton.core.network.domain.NetworkManager
import javax.inject.Inject
import javax.inject.Singleton
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Singleton
class GoogleFeatureFlagManager @Inject constructor(
    private val networkManager: NetworkManager
) : FeatureFlagManager {

    private val remoteConfig = FirebaseRemoteConfig.getInstance()

    init {
        // Use Builder pattern for settings
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600) // Production value: 1 hour
            .build()

        remoteConfig.setConfigSettingsAsync(configSettings)

        // Set defaults (used if no cache and no internet)
        remoteConfig.setDefaultsAsync(mapOf(
            KEY_STATISTICS_ENABLED to false
        ))
    }

    override suspend fun fetchAndActivate() {
        // Check internet connection using Proton's NetworkManager
        if (!networkManager.isConnectedToNetwork()) {
            return
        }

        try {
            // Fetch and Activate
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch remote config", e)
        }
    }

    override fun isStatisticsEnabled(): Boolean {
        // This method automatically reads from the active config (cache)
        // If nothing was fetched, it returns the default value (false)
        return remoteConfig.getBoolean(KEY_STATISTICS_ENABLED)
    }

    companion object {
        private const val TAG = "FeatureFlagManager"
        private const val KEY_STATISTICS_ENABLED = "feature_statistics_enabled"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GoogleFeatureFlagModule {
    @Binds
    abstract fun bindFeatureFlagManager(impl: GoogleFeatureFlagManager): FeatureFlagManager
}