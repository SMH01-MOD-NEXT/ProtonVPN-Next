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

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        // The official ProtonVPN client version we are spoofing
        const val SPOOFED_APP_VERSION = "5.21.57.0"

        /**
         * Generates a User-Agent string identical to the official Proton client.
         * Placed in companion object so it can be easily accessed by NetworkModule and CaptchaScreen
         * without needing to inject Context.
         */
        fun getSpoofedUserAgent(): String {
            val androidVersion = Build.VERSION.RELEASE ?: "12"
            val manufacturer = Build.MANUFACTURER ?: "Unknown"
            val model = Build.MODEL ?: "Device"

            val capManufacturer = manufacturer.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
            }

            val deviceName = if (model.lowercase(Locale.US).startsWith(manufacturer.lowercase(Locale.US))) {
                model.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            } else {
                "$capManufacturer $model"
            }

            val safeDeviceName = deviceName.replace(Regex("[^\\x20-\\x7E]"), "").trim()
            val safeAndroidVersion = androidVersion.replace(Regex("[^\\x20-\\x7E]"), "").trim()

            return "ProtonVPN/$SPOOFED_APP_VERSION (Android $safeAndroidVersion; $safeDeviceName)"
        }
    }

    fun getAppLanguage(): String {
        return context.resources.configuration.locales[0].language
    }

    fun getTimezone(): String {
        return TimeZone.getDefault().id
    }

    /**
     * The original value was a hardcoded Long.
     * A stable long identifier is required. ANDROID_ID is a good candidate.
     */
    @SuppressLint("HardwareIds")
    fun getDeviceHash(): Long {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return androidId?.hashCode()?.toLong() ?: 53319294142L
    }

    fun getRegionCode(): String {
        return context.resources.configuration.locales[0].country.ifEmpty { "US" }
    }

    fun getTimezoneOffset(): Int {
        // Proton expects offset in minutes, usually negative for GMT+
        return -(TimeZone.getDefault().rawOffset / (1000 * 60))
    }

    // Basic root check to avoid constant return value
    fun isJailbreak(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }

    fun getPreferredContentSize(): String {
        return String.format(Locale.US, "%.1f", context.resources.configuration.fontScale)
    }

    fun getStorageCapacity(): Double {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            totalBytes / (1024.0 * 1024.0 * 1024.0) // GB
        } catch (e: Exception) {
            128.0
        }
    }

    fun isDarkModeOn(): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun getInstalledKeyboards(): List<String> {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.enabledInputMethodList.map { it.packageName }
        } catch (e: Exception) {
            listOf("com.google.android.inputmethod.latin")
        }
    }
}
