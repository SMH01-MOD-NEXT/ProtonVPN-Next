package com.protonvpn.android.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.protonvpn.android.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles OTA updates for ProtonMOD-Next.
 * Supports multiple build variants (FOSS/Google, Debug/Release).
 */
object ProtonModUpdateManager {

    private const val UPDATE_JSON_URL = "https://protonvpn-next.pages.dev/update.json"

    fun check(context: Context, manualCheck: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonString = fetchJson(UPDATE_JSON_URL)
                if (jsonString == null) {
                    if (manualCheck) showToast(context, "Failed to check for updates (Network error)")
                    return@launch
                }

                val json = JSONObject(jsonString)
                val remoteVersionCode = json.optInt("versionCode", 0)
                val remoteVersionName = json.optString("versionName", "Unknown")
                val changelog = json.optString("changelog", "Bug fixes and improvements.")

                // --- SMART DOWNLOAD LINK SELECTION ---
                val downloads = json.optJSONObject("downloads")
                val downloadUrl = if (downloads != null) {
                    getCorrectDownloadUrl(downloads)
                } else {
                    json.optString("downloadUrl", "")
                }
                // -------------------------------------

                val currentVersionCode = BuildConfig.VERSION_CODE

                if (remoteVersionCode > currentVersionCode) {
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(context, remoteVersionName, changelog, downloadUrl)
                    }
                } else {
                    if (manualCheck) {
                        showToast(context, "You have the latest version ($remoteVersionName)")
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                if (manualCheck) showToast(context, "Error checking updates: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Determines which URL to use based on the current app variant.
     */
    private fun getCorrectDownloadUrl(downloads: JSONObject): String {
        val isFoss = BuildConfig.FLAVOR.contains("OpenSource", ignoreCase = true)
        val isDebug = BuildConfig.DEBUG

        return when {
            isFoss && isDebug -> downloads.optString("fossDebug")
            isFoss && !isDebug -> downloads.optString("fossRelease")
            !isFoss && isDebug -> downloads.optString("googleDebug")
            else -> downloads.optString("googleRelease") // Default standard release
        }
    }

    private fun fetchJson(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            conn.connect()

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()
                sb.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun showUpdateDialog(context: Context, version: String, changelog: String, url: String) {
        if (context !is Activity || context.isFinishing) return

        if (url.isEmpty()) {
            Toast.makeText(context, "Update detected but no URL found for this variant.", Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(context)
            .setTitle("Update Available: $version")
            .setMessage(changelog)
            .setPositiveButton("Download") { _, _ ->
                openUrlInBrowser(context, url)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    /**
     * Safely opens URL in browser, ensuring we don't accidentally resolve to our own
     * non-exported activity (which causes a crash on Android 12+).
     */
    private fun openUrlInBrowser(context: Context, url: String) {
        try {
            val uri = Uri.parse(url)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

            // Explicitly try to find a handler that is NOT this app.
            // This prevents the "matches the intent filter of a non-exported component" crash.
            val pm = context.packageManager
            val handlers = pm.queryIntentActivities(intent, 0)

            // Find first app that is NOT us
            val otherApp = handlers.firstOrNull {
                it.activityInfo.packageName != context.packageName
            }

            if (otherApp != null) {
                // If we found a browser/other app, explicit target it
                intent.setPackage(otherApp.activityInfo.packageName)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open browser: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun showToast(context: Context, message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}