/*
 * Copyright (c) 2025 Proton AG
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
package com.protonvpn.android.redesign.settings.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.SettingsFeatureToggle
import com.protonvpn.android.proxy.VlessManager
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.theme.ThemeType
import kotlinx.coroutines.launch
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultSmallWeak
import me.proton.core.compose.theme.defaultWeak
import me.proton.core.presentation.R as CoreR
import org.json.JSONObject

@Composable
fun ProxySubSetting(
    onClose: () -> Unit,
    setting: SettingsViewModel.SettingViewState.Proxy,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val vlessManager = remember { VlessManager.getInstance(context) }

    // State for Custom Configs
    var newConfigJson by remember { mutableStateOf("") }
    var customConfigs by remember { mutableStateOf(vlessManager.getCustomConfigs()) }

    // State for Server Configs
    var serverConfigs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var isLoadingServerConfigs by remember { mutableStateOf(true) }

    // Ping results map
    var pingResults by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var pendingPings by remember { mutableStateOf<Set<String>>(emptySet()) }

    val themeType = LocalThemeType.current

    val isAmoled = themeType == ThemeType.Amoled || themeType == ThemeType.NewYearAmoled
    val border = if (isAmoled) BorderStroke(1.dp, Color.White) else null

    val isLight = themeType == ThemeType.Light || themeType == ThemeType.NewYearLight ||
            (themeType == ThemeType.System && !isSystemInDarkTheme())
    val cardColor = if (isLight) Color(0xFFF0F0F0) else ProtonTheme.colors.backgroundSecondary

    // Strings
    val configAddedToast = stringResource(R.string.proxy_config_added_toast)
    val invalidJsonToast = stringResource(R.string.proxy_invalid_json_toast)
    val pingTimeoutStr = stringResource(R.string.proxy_ping_timeout)

    // Fetch server configs on open
    LaunchedEffect(Unit) {
        isLoadingServerConfigs = true
        serverConfigs = vlessManager.getGithubConfigs()
        isLoadingServerConfigs = false
    }

    fun pingConfig(config: JSONObject) {
        val key = config.toString()
        if (pendingPings.contains(key)) return

        pendingPings = pendingPings + key
        scope.launch {
            val latency = vlessManager.pingServer(config)
            val result = if (latency >= 0) "${latency}ms" else pingTimeoutStr
            pingResults = pingResults.toMutableMap().apply { put(key, result) }
            pendingPings = pendingPings - key
        }
    }

    FeatureSubSettingScaffold(
        title = stringResource(id = setting.titleRes),
        onClose = onClose,
        listState = listState,
        titleInListIndex = 0,
    ) { contentPadding ->
        val horizontalItemPaddingModifier = Modifier
            .largeScreenContentPadding()
            .padding(horizontal = 16.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier.padding(contentPadding)
        ) {
            addFeatureSettingItems(
                itemModifier = horizontalItemPaddingModifier,
                setting = setting,
                imageRes = setting.iconRes ?: CoreR.drawable.ic_proton_globe,
                onLearnMore = {},
            )

            item {
                Card(
                    modifier = horizontalItemPaddingModifier.padding(top = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    border = border,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    SettingsFeatureToggle(
                        label = stringResource(setting.titleRes),
                        checked = setting.value,
                        onCheckedChange = { _ -> onToggle() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }

            // --- Custom Configs Section ---
            item {
                Text(
                    text = stringResource(R.string.proxy_custom_configs_title),
                    style = ProtonTheme.typography.defaultNorm.copy(fontWeight = FontWeight.Bold),
                    modifier = horizontalItemPaddingModifier.padding(top = 24.dp, bottom = 8.dp)
                )
            }

            item {
                Card(
                    modifier = horizontalItemPaddingModifier.padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    border = border,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = newConfigJson,
                            onValueChange = { newConfigJson = it },
                            label = { Text(stringResource(R.string.proxy_add_config_input_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (newConfigJson.isBlank()) return@Button
                                try {
                                    vlessManager.addCustomConfig(newConfigJson)
                                    newConfigJson = ""
                                    customConfigs = vlessManager.getCustomConfigs()
                                    Toast.makeText(context, configAddedToast, Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, invalidJsonToast, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.align(Alignment.End),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ProtonTheme.colors.interactionNorm
                            )
                        ) {
                            Text(stringResource(R.string.proxy_add_config_button))
                        }
                    }
                }
            }

            itemsIndexed(customConfigs) { index, config ->
                ConfigItemCard(
                    config = config,
                    isCustom = true,
                    pingResult = pingResults[config.toString()],
                    isPinging = pendingPings.contains(config.toString()),
                    onPing = { pingConfig(config) },
                    onDelete = {
                        vlessManager.removeCustomConfig(index)
                        customConfigs = vlessManager.getCustomConfigs()
                    },
                    modifier = horizontalItemPaddingModifier,
                    cardColor = cardColor,
                    border = border
                )
            }

            // --- Server Configs Section ---
            item {
                Text(
                    text = stringResource(R.string.proxy_server_configs_title),
                    style = ProtonTheme.typography.defaultNorm.copy(fontWeight = FontWeight.Bold),
                    modifier = horizontalItemPaddingModifier.padding(top = 24.dp, bottom = 8.dp)
                )
            }

            if (isLoadingServerConfigs) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = ProtonTheme.colors.interactionNorm
                        )
                    }
                }
            } else if (serverConfigs.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.proxy_no_server_configs),
                        style = ProtonTheme.typography.defaultWeak,
                        modifier = horizontalItemPaddingModifier.padding(bottom = 8.dp)
                    )
                }
            } else {
                itemsIndexed(serverConfigs) { _, config ->
                    ConfigItemCard(
                        config = config,
                        isCustom = false,
                        pingResult = pingResults[config.toString()],
                        isPinging = pendingPings.contains(config.toString()),
                        onPing = { pingConfig(config) },
                        onDelete = {},
                        modifier = horizontalItemPaddingModifier,
                        cardColor = cardColor,
                        border = border
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ConfigItemCard(
    config: JSONObject,
    isCustom: Boolean,
    pingResult: String?,
    isPinging: Boolean,
    onPing: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
    cardColor: Color,
    border: BorderStroke?
) {
    Card(
        modifier = modifier.padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = border
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.optString("ps", stringResource(R.string.proxy_unnamed_config)),
                    style = ProtonTheme.typography.defaultNorm.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1
                )
                val host = try {
                    config.getJSONArray("outbounds").getJSONObject(0)
                        .getJSONObject("settings").getJSONArray("vnext")
                        .getJSONObject(0).getString("address")
                } catch (e: Exception) { stringResource(R.string.proxy_unknown_host) }

                Text(
                    text = host,
                    style = ProtonTheme.typography.defaultSmallWeak,
                    fontSize = 12.sp,
                    maxLines = 1
                )

                if (pingResult != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.proxy_ping_label, pingResult),
                        style = ProtonTheme.typography.defaultSmallWeak.copy(
                            color = if (pingResult == stringResource(R.string.proxy_ping_timeout)) MaterialTheme.colorScheme.error else ProtonTheme.colors.interactionNorm
                        ),
                        fontSize = 12.sp
                    )
                }
            }

            // Ping Button
            IconButton(onClick = onPing, enabled = !isPinging) {
                if (isPinging) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = ProtonTheme.colors.interactionNorm
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.proxy_ping_content_description),
                        tint = ProtonTheme.colors.iconNorm
                    )
                }
            }

            // Delete Button (only for custom)
            if (isCustom) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.proxy_delete_content_description),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}