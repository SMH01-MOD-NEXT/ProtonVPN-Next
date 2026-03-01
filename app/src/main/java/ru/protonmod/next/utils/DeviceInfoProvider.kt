package ru.protonmod.next.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "2.0.7"
        } catch (e: PackageManager.NameNotFoundException) {
            "2.0.7" // Fallback
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
    fun getDeviceName(): Long {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return androidId?.hashCode()?.toLong() ?: 53319294142L
    }

    fun getRegionCode(): String {
        return context.resources.configuration.locales[0].country
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
        return String.format("%.1f", context.resources.configuration.fontScale)
    }

    fun getStorageCapacity(): Double {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            totalBytes / (1024.0 * 1024.0 * 1024.0) // GB
        } catch (e: Exception) {
            256.0
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
            emptyList()
        }
    }
}
