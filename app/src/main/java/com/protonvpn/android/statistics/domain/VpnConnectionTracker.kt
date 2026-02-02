package com.protonvpn.android.statistics.domain

import com.protonvpn.android.core.featureflags.FeatureFlagManager
import com.protonvpn.android.statistics.StatisticsDao
import com.protonvpn.android.statistics.VpnSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnConnectionTracker @Inject constructor(
    private val statisticsDao: StatisticsDao,
    private val featureFlagManager: FeatureFlagManager // Inject Feature Manager
) {
    private var startTime: Long? = null
    private var currentServerName: String = ""
    private var currentServerCountry: String = ""
    private var currentProtocol: String = ""
    private var downloadBytes: Long = 0
    private var uploadBytes: Long = 0
    private var connectionEstablished = false

    fun onConnecting(
        serverName: String = "",
        serverCountry: String = "",
        protocol: String = ""
    ) {
        // Feature Flag Check: If disabled, do not track connection start
        if (!featureFlagManager.isStatisticsEnabled()) return

        startTime = System.currentTimeMillis()
        currentServerName = serverName
        currentServerCountry = serverCountry
        currentProtocol = protocol
        downloadBytes = 0
        uploadBytes = 0
        connectionEstablished = false
    }

    fun onConnected() {
        // Feature Flag Check
        if (!featureFlagManager.isStatisticsEnabled()) return

        startTime = System.currentTimeMillis()
        connectionEstablished = true
    }

    fun updateTraffic(download: Long, upload: Long) {
        // Feature Flag Check
        if (!featureFlagManager.isStatisticsEnabled()) return

        downloadBytes = download
        uploadBytes = upload
    }

    fun onDisconnected() {
        // Feature Flag Check: If disabled, exit early
        if (!featureFlagManager.isStatisticsEnabled()) {
            resetState()
            return
        }

        val start = startTime ?: return
        val end = System.currentTimeMillis()
        val duration = (end - start) / 1000

        // Ignore disconnections without successful connection (if user cancelled during connecting)
        if (!connectionEstablished) {
            resetState()
            return
        }

        if (duration > 0) {
            CoroutineScope(Dispatchers.IO).launch {
                statisticsDao.insertSession(
                    VpnSessionEntity(
                        startTime = start,
                        endTime = end,
                        durationSeconds = duration,
                        serverName = currentServerName,
                        serverCountry = currentServerCountry,
                        protocol = currentProtocol,
                        isConnectionError = false,
                        downloadBytes = downloadBytes,
                        uploadBytes = uploadBytes
                    )
                )
            }
        }
        resetState()
    }

    fun onConnectionError(errorMessage: String) {
        // Feature Flag Check
        if (!featureFlagManager.isStatisticsEnabled()) {
            resetState()
            return
        }

        val start = startTime ?: System.currentTimeMillis()
        val end = System.currentTimeMillis()
        val duration = (end - start) / 1000

        CoroutineScope(Dispatchers.IO).launch {
            statisticsDao.insertSession(
                VpnSessionEntity(
                    startTime = start,
                    endTime = end,
                    durationSeconds = duration,
                    serverName = currentServerName,
                    serverCountry = currentServerCountry,
                    protocol = currentProtocol,
                    isConnectionError = true,
                    errorMessage = errorMessage
                )
            )
        }
        resetState()
    }

    private fun resetState() {
        startTime = null
        currentServerName = ""
        currentServerCountry = ""
        currentProtocol = ""
        downloadBytes = 0
        uploadBytes = 0
        connectionEstablished = false
    }
}