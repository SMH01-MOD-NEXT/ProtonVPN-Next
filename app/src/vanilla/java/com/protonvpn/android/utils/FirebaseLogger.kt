/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.android.utils

import android.content.Context
import android.util.Log
import com.protonvpn.android.BuildConfig

/**
 * Logger stub for FOSS builds (No Firebase dependency).
 *
 * This implementation replaces the Firebase version to allow compilation
 * without Google Services dependencies.
 */
object FirebaseLogger {

    private const val TAG = "FirebaseLogger"

    /**
     * Initializes the logger.
     * In the FOSS version, this does nothing as we do not use Firebase.
     */
    fun init(context: Context) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "FOSS Build: Firebase Crashlytics is disabled.")
        }
    }

    /**
     * Logs error to local Android Logcat only.
     * Does NOT send anything to Firebase (Dependencies are missing in FOSS build).
     */
    fun logError(throwable: Throwable, message: String? = null) {
        val msg = message ?: throwable.message ?: "Unknown Error"
        // Log to Logcat so we can still debug locally
        Log.e(TAG, "Local Error (FOSS): $msg", throwable)
    }

    /**
     * Logs info to local Android Logcat only.
     */
    fun logInfo(message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "Info (FOSS): $message")
        }
    }
}