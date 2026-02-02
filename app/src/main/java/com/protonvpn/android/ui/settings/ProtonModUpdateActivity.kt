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

package com.protonvpn.android.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.core.compose.theme.ProtonTheme
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Modern Update Screen using Jetpack Compose.
 * Handles downloading and installing the APK internally.
 * Uses Proton Design System tokens (Typography and Colors).
 */
class ProtonModUpdateActivity : ComponentActivity() {

    private var downloadUrl: String = ""
    private var version: String = ""
    private var changelog: String = ""
    private var apkFile: File? = null

    // Helper to request install permission
    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Upon returning from settings, try installing again if file exists
        apkFile?.let { file -> installApk(file) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extract data passed from Manager
        downloadUrl = intent.getStringExtra("EXTRA_URL") ?: ""
        version = intent.getStringExtra("EXTRA_VERSION") ?: ""
        changelog = intent.getStringExtra("EXTRA_CHANGELOG") ?: ""

        if (downloadUrl.isEmpty()) {
            finish()
            return
        }

        setContent {
            ProtonTheme {
                UpdateScreen(
                    version = version,
                    changelog = changelog,
                    onDownload = { startDownload() },
                    onInstall = { apkFile?.let { installApk(it) } },
                    onDismiss = { finish() }
                )
            }
        }
    }

    @Composable
    fun UpdateScreen(
        version: String,
        changelog: String,
        onDownload: () -> Unit,
        onInstall: () -> Unit,
        onDismiss: () -> Unit
    ) {
        var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
        var progress by remember { mutableFloatStateOf(0f) }

        fun doDownload() {
            downloadState = DownloadState.Downloading
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val file = downloadApkInternal(downloadUrl) { p ->
                        progress = p
                    }
                    if (file != null) {
                        apkFile = file
                        downloadState = DownloadState.ReadyToInstall
                        withContext(Dispatchers.Main) {
                            installApk(file)
                        }
                    } else {
                        downloadState = DownloadState.Error
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    downloadState = DownloadState.Error
                }
            }
        }

        // Full screen translucent box acting as a dialog overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = ProtonTheme.colors.backgroundSecondary
                ),
                elevation = CardDefaults.cardElevation(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Icon
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown, // Replaced Download with KeyboardArrowDown (Core)
                        contentDescription = null,
                        tint = ProtonTheme.colors.brandNorm,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                ProtonTheme.colors.brandNorm.copy(alpha = 0.1f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title
                    Text(
                        text = stringResource(R.string.proton_mod_update_screen_title),
                        style = ProtonTheme.typography.headline,
                        color = ProtonTheme.colors.textNorm,
                    )

                    // Version Subtitle
                    Text(
                        text = stringResource(R.string.proton_mod_update_version_label, version),
                        style = ProtonTheme.typography.body1Regular,
                        color = ProtonTheme.colors.textWeak,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Changelog Area
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .background(
                                ProtonTheme.colors.backgroundNorm,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = stringResource(R.string.proton_mod_update_changelog_title),
                            style = ProtonTheme.typography.body1Bold,
                            color = ProtonTheme.colors.textNorm,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = changelog,
                            style = ProtonTheme.typography.body1Regular,
                            color = ProtonTheme.colors.textNorm
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action Buttons / Progress
                    when (downloadState) {
                        DownloadState.Idle, DownloadState.Error -> {
                            if (downloadState == DownloadState.Error) {
                                Text(
                                    text = stringResource(R.string.proton_mod_update_error_download),
                                    color = ProtonTheme.colors.notificationError,
                                    style = ProtonTheme.typography.body2Regular,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            Button(
                                onClick = { doDownload() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ProtonTheme.colors.brandNorm
                                ),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.proton_mod_update_btn_download),
                                    style = ProtonTheme.typography.body1Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.proton_mod_update_btn_later),
                                    color = ProtonTheme.colors.textWeak,
                                    style = ProtonTheme.typography.body1Medium
                                )
                            }
                        }

                        DownloadState.Downloading -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = ProtonTheme.colors.brandNorm,
                                    trackColor = ProtonTheme.colors.backgroundNorm,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.proton_mod_update_btn_downloading, (progress * 100).toInt()),
                                    style = ProtonTheme.typography.body2Regular,
                                    color = ProtonTheme.colors.textWeak
                                )
                            }
                        }

                        DownloadState.ReadyToInstall -> {
                            Button(
                                onClick = { onInstall() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ProtonTheme.colors.notificationSuccess
                                ),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.proton_mod_update_btn_install),
                                    style = ProtonTheme.typography.body1Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startDownload() {
        // Triggered by UI
    }

    private fun downloadApkInternal(url: String, onProgress: (Float) -> Unit): File? {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body ?: return null
            val contentLength = body.contentLength()
            val source = body.byteStream()

            val cacheDir = File(cacheDir, "updates")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            // Clean old updates
            cacheDir.listFiles()?.forEach { it.delete() }

            val file = File(cacheDir, "update.apk")
            val output = FileOutputStream(file)

            val data = ByteArray(4096)
            var count: Int
            var total: Long = 0

            while (source.read(data).also { count = it } != -1) {
                total += count
                output.write(data, 0, count)
                if (contentLength > 0) {
                    onProgress(total.toFloat() / contentLength.toFloat())
                }
            }

            output.flush()
            output.close()
            source.close()

            return file

        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }

    private fun installApk(file: File) {
        if (!file.exists()) {
            Toast.makeText(this, "Update file missing", Toast.LENGTH_SHORT).show()
            return
        }

        // Check for Android 8+ Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                Toast.makeText(this, R.string.proton_mod_update_permission_rationale, Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                intent.data = Uri.parse("package:$packageName")
                installPermissionLauncher.launch(intent)
                return
            }
        }

        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider", // Changed to direct packageName for clarity
                file
            )

            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK

            // Find all apps that can handle this APK (Package Installer, etc.)
            val resInfoList = packageManager.queryIntentActivities(intent, 0)

            // Loop through candidates to find one that IS NOT US
            for (resolveInfo in resInfoList) {
                val pkgName = resolveInfo.activityInfo.packageName
                val clsName = resolveInfo.activityInfo.name // Get the exact Activity Class Name

                // Skip our own package to avoid the non-exported MainActivity conflict
                if (pkgName != packageName) {
                    // Set both Package AND Class to be explicitly sure what we launch
                    // This fixes "Unable to find explicit activity" errors
                    intent.setClassName(pkgName, clsName)
                    break
                }
            }

            startActivity(intent)
        } catch (e: Exception) {
            Log.e("Update", "Install failed", e)
            Toast.makeText(this, "Install failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    enum class DownloadState {
        Idle, Downloading, ReadyToInstall, Error
    }
}