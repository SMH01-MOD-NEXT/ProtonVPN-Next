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
import android.content.Intent
import android.widget.Toast
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.R
import com.protonvpn.android.ui.settings.ProtonModUpdateActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object ProtonModUpdateManager {

    private const val UPDATE_JSON_URL = "https://protonvpn-next.pages.dev/update.json"

    fun check(context: Context, manualCheck: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonString = fetchJson(UPDATE_JSON_URL)
                if (jsonString == null) {
                    if (manualCheck) {
                        showToast(context, context.getString(R.string.proton_mod_update_check_failed))
                    }
                    return@launch
                }

                val json = JSONObject(jsonString)
                val remoteVersionCode = json.optInt("versionCode", 0)
                val remoteVersionName = json.optString("versionName", context.getString(R.string.proton_mod_update_unknown_version))
                val changelog = json.optString("changelog", context.getString(R.string.proton_mod_update_default_changelog))

                val downloads = json.optJSONObject("downloads")
                val downloadUrl = if (downloads != null) {
                    getCorrectDownloadUrl(downloads)
                } else {
                    json.optString("downloadUrl", "")
                }

                val currentVersionCode = BuildConfig.VERSION_CODE

                if (remoteVersionCode > currentVersionCode) {
                    withContext(Dispatchers.Main) {
                        // Launch the new Modern Update UI
                        val intent = Intent(context, ProtonModUpdateActivity::class.java).apply {
                            putExtra("EXTRA_VERSION", remoteVersionName)
                            putExtra("EXTRA_CHANGELOG", changelog)
                            putExtra("EXTRA_URL", downloadUrl)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                } else {
                    if (manualCheck) {
                        showToast(context, context.getString(R.string.proton_mod_update_latest_version, remoteVersionName))
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                if (manualCheck) {
                    showToast(context, context.getString(R.string.proton_mod_update_error_checking, e.localizedMessage))
                }
            }
        }
    }

    private fun getCorrectDownloadUrl(downloads: JSONObject): String {
        val isFoss = BuildConfig.FLAVOR.contains("OpenSource", ignoreCase = true)
        val isDebug = BuildConfig.DEBUG

        return when {
            isFoss && isDebug -> downloads.optString("fossDebug")
            isFoss && !isDebug -> downloads.optString("fossRelease")
            !isFoss && isDebug -> downloads.optString("googleDebug")
            else -> downloads.optString("googleRelease")
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

    private suspend fun showToast(context: Context, message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}