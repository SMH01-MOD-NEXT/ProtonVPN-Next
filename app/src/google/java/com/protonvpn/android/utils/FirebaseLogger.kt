package com.protonvpn.android.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.protonvpn.android.BuildConfig

/**
 * Logger for Firebase.
 *
 * Logic:
 * 1. DEBUG:
 * - Captures Fatal Crashes.
 * - Adds detailed Device Info as Custom Keys.
 *
 * 2. RELEASE:
 * - Captures Fatal Crashes (Stacktrace).
 * - No custom keys, no logs, minimal data for privacy.
 *
 * 3. Non-fatal errors & Info logs:
 * - Local Logcat only.
 */
object FirebaseLogger {

    private const val TAG = "FirebaseLogger"

    fun init(context: Context) {
        // Crashlytics initializes automatically via ContentProvider.

        if (BuildConfig.DEBUG) {
            // In DEBUG, we add explicit device info to the crash report context.
            // This replaces the old "collectDeviceInfo()" from the Telegram bot.
            val crashlytics = FirebaseCrashlytics.getInstance()

            crashlytics.setCustomKey("Manufacturer", Build.MANUFACTURER)
            crashlytics.setCustomKey("Model", Build.MODEL)
            crashlytics.setCustomKey("Device", Build.DEVICE)
            crashlytics.setCustomKey("Product", Build.PRODUCT)
            crashlytics.setCustomKey("Board", Build.BOARD)
            crashlytics.setCustomKey("Bootloader", Build.BOOTLOADER)
            crashlytics.setCustomKey("Fingerprint", Build.FINGERPRINT)
            crashlytics.setCustomKey("Android_SDK", Build.VERSION.SDK_INT)

            Log.d(TAG, "Debug keys added to Crashlytics")
        }
        // In RELEASE: Do nothing extra.
        // Crashlytics will just send the stacktrace and basic standard metadata.
    }

    /**
     * Logs error to local Android Logcat only.
     * Does NOT send anything to Firebase to keep Release builds clean.
     */
    fun logError(throwable: Throwable, message: String? = null) {
        val msg = message ?: throwable.message ?: "Unknown Error"
        Log.e(TAG, "Non-fatal error (Ignored by Firebase): $msg", throwable)
    }

    /**
     * Logs info to local Android Logcat only.
     */
    fun logInfo(message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "Info (Ignored by Firebase): $message")
        }
    }
}