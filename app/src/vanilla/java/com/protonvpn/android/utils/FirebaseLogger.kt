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