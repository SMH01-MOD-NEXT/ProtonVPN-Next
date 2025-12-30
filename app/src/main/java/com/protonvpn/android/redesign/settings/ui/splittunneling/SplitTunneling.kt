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

package com.protonvpn.android.redesign.settings.ui.splittunneling

import android.widget.ImageView
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.protonvpn.android.R
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.redesign.settings.ui.BasicSubSetting
import com.protonvpn.android.redesign.settings.ui.LocalThemeType
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.theme.ThemeType
import com.protonvpn.android.ui.settings.LabeledItem
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultSmallWeak
import me.proton.core.compose.theme.defaultWeak
import me.proton.core.presentation.R as CoreR

@Composable
fun SplitTunnelingScreen(
    onClose: () -> Unit,
    viewModel: SplitTunnelingViewModel = hiltViewModel()
) {
    val viewState by viewModel.viewState.collectAsState()
    val context = LocalContext.current

    // Dialog States
    var showRemoveIpDialog by remember { mutableStateOf<String?>(null) }
    var showIPv6Dialog by remember { mutableStateOf(false) }

    // Effects
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                SplitTunnelingViewModel.Event.IpAdded -> { /* Input clears via state */ }
                SplitTunnelingViewModel.Event.ShowIPv6EnableDialog -> showIPv6Dialog = true
                SplitTunnelingViewModel.Event.ShowIPv6EnabledToast ->
                    Toast.makeText(context, R.string.settings_ipv6_enabled_toast, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Dialogs
    if (showIPv6Dialog) {
        AlertDialog(
            onDismissRequest = { showIPv6Dialog = false },
            title = { Text(stringResource(R.string.settings_split_tunneling_ipv6_disabled_dialog_title)) },
            text = { Text(stringResource(R.string.settings_split_tunneling_ipv6_disabled_dialog_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onEnableIPv6(); showIPv6Dialog = false }) {
                    Text(stringResource(R.string.setting_ipv6_disabled_dialog_action_enable))
                }
            },
            dismissButton = {
                TextButton(onClick = { showIPv6Dialog = false }) { Text(stringResource(R.string.ok)) }
            },
            containerColor = ProtonTheme.colors.backgroundSecondary,
            titleContentColor = ProtonTheme.colors.textNorm,
            textContentColor = ProtonTheme.colors.textNorm
        )
    }

    if (showRemoveIpDialog != null) {
        AlertDialog(
            onDismissRequest = { showRemoveIpDialog = null },
            text = { Text(stringResource(R.string.settings_split_tunneling_remove_ip_dialog_message)) },
            confirmButton = {
                TextButton(onClick = { showRemoveIpDialog?.let { viewModel.removeIp(it) }; showRemoveIpDialog = null }) {
                    Text(stringResource(R.string.remove), color = ProtonTheme.colors.notificationError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveIpDialog = null }) { Text(stringResource(R.string.cancel)) }
            },
            containerColor = ProtonTheme.colors.backgroundSecondary,
            titleContentColor = ProtonTheme.colors.textNorm,
            textContentColor = ProtonTheme.colors.textNorm
        )
    }

    // UI Structure
    val themeType = LocalThemeType.current
    val isLight = themeType == ThemeType.Light || themeType == ThemeType.NewYearLight ||
            (themeType == ThemeType.System && !isSystemInDarkTheme())
    val cardColor = if (isLight) Color(0xFFF0F0F0) else ProtonTheme.colors.backgroundSecondary
    val isAmoled = themeType == ThemeType.Amoled || themeType == ThemeType.NewYearAmoled
    val border = if (isAmoled) BorderStroke(1.dp, Color.White) else null

    BasicSubSetting(
        title = stringResource(id = R.string.settings_split_tunneling_title),
        onClose = onClose
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .largeScreenContentPadding()
                .padding(horizontal = 16.dp)
        ) {
            // 1. Tab Row
            SplitTunnelingTabs(
                currentTab = viewState.currentTab,
                onTabSelected = viewModel::onTabSelected,
                isLight = isLight
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Dynamic Header (Search for Apps, Input for IPs)
            Crossfade(targetState = viewState.currentTab, label = "HeaderCrossfade") { tab ->
                when(tab) {
                    SplitTunnelingTab.APPS -> {
                        SearchBar(
                            query = viewState.searchQuery,
                            onQueryChanged = viewModel::onSearchQueryChanged,
                            isLight = isLight
                        )
                    }
                    SplitTunnelingTab.IPS -> {
                        IpInput(
                            isLight = isLight,
                            error = viewState.ipsState.inputError,
                            onAdd = viewModel::validateAndAddIp,
                            onClearError = viewModel::clearIpError
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Main Content Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                border = border,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Content List
                // Removed ModeSelector here as per request
                Crossfade(targetState = viewState.currentTab, label = "ListCrossfade") { tab ->
                    when(tab) {
                        SplitTunnelingTab.APPS -> AppsListContent(viewState.appsState, viewState.mode, viewModel)
                        SplitTunnelingTab.IPS -> IpsListContent(viewState.ipsState, viewState.mode, onRemove = { showRemoveIpDialog = it })
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SplitTunnelingTabs(
    currentTab: SplitTunnelingTab,
    onTabSelected: (SplitTunnelingTab) -> Unit,
    isLight: Boolean
) {
    val backgroundColor = if (isLight) Color(0xFFE0E0E0) else Color(0xFF2E2E34)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TabItem(
            title = stringResource(R.string.settings_split_tunneling_tab_apps),
            selected = currentTab == SplitTunnelingTab.APPS,
            onClick = { onTabSelected(SplitTunnelingTab.APPS) },
            modifier = Modifier.weight(1f)
        )
        TabItem(
            title = stringResource(R.string.settings_split_tunneling_tab_ips),
            selected = currentTab == SplitTunnelingTab.IPS,
            onClick = { onTabSelected(SplitTunnelingTab.IPS) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun TabItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) ProtonTheme.colors.backgroundSecondary else Color.Transparent
    val textColor = if (selected) ProtonTheme.colors.textNorm else ProtonTheme.colors.textWeak

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = ProtonTheme.typography.defaultNorm.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            ),
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
fun AppsListContent(
    state: AppsUiState,
    mode: SplitTunnelingMode,
    viewModel: SplitTunnelingViewModel
) {
    when (state) {
        AppsUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ProtonTheme.colors.brandNorm)
            }
        }
        is AppsUiState.Content -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                // Selected Apps
                if (state.selectedApps.isNotEmpty()) {
                    item {
                        SectionHeader(
                            text = if (mode == SplitTunnelingMode.INCLUDE_ONLY)
                                stringResource(R.string.settingsIncludedAppsSelectedHeader, state.selectedApps.size)
                            else
                                stringResource(R.string.settingsExcludedAppsSelectedHeader, state.selectedApps.size)
                        )
                    }
                    items(state.selectedApps, key = { it.id }) { item ->
                        AppItem(item, isAdded = true, onClick = { viewModel.removeApp(item) })
                    }
                } else {
                    item {
                        EmptyState(
                            textRes = if (mode == SplitTunnelingMode.EXCLUDE_ONLY)
                                R.string.settingsExcludedAppsEmpty
                            else
                                R.string.settingsIncludedAppsEmpty
                        )
                    }
                }

                // Available Regular
                if (state.availableApps.isNotEmpty()) {
                    item {
                        SectionHeader(stringResource(R.string.settingsSplitTunnelingAvailableRegularHeader, state.availableApps.size))
                    }
                    items(state.availableApps, key = { it.id }) { item ->
                        AppItem(item, isAdded = false, onClick = { viewModel.addApp(item) })
                    }
                }

                // System Apps
                item { SectionHeader(stringResource(R.string.settings_split_tunneling_system_apps)) }

                when(val sys = state.systemAppsState) {
                    SplitTunnelingSystemAppsState.NotLoaded -> {
                        item { LoadSystemAppsButton(viewModel::toggleLoadSystemApps) }
                    }
                    SplitTunnelingSystemAppsState.Loading -> {
                        item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
                    }
                    is SplitTunnelingSystemAppsState.Loaded -> {
                        if (sys.apps.isEmpty()) {
                            item { EmptyState(R.string.settings_empty_search) }
                        } else {
                            items(sys.apps, key = { it.id }) { item ->
                                AppItem(item, isAdded = false, onClick = { viewModel.addApp(item) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IpsListContent(
    state: IpsUiState,
    mode: SplitTunnelingMode,
    onRemove: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        if (state.items.isNotEmpty()) {
            item {
                val headerRes = if (mode == SplitTunnelingMode.INCLUDE_ONLY)
                    R.string.settingsIncludedIPAddressesListHeader
                else
                    R.string.settingsExcludedIPAddressesListHeader
                SectionHeader(stringResource(headerRes, state.items.size))
            }
            items(state.items, key = { it }) { ip ->
                IpItem(ip, onRemove = { onRemove(ip) })
            }
        } else {
            item { EmptyState(R.string.settings_empty_search) }
        }
    }
}

// --- Shared Reusable Components ---

@Composable
private fun SearchBar(query: String, onQueryChanged: (String) -> Unit, isLight: Boolean) {
    val containerColor = if (isLight) Color(0xFFE0E0E0) else Color(0xFF2E2E34)
    TextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        placeholder = { Text(stringResource(R.string.settings_search_placeholder), style = ProtonTheme.typography.defaultWeak, color = ProtonTheme.colors.textWeak) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = ProtonTheme.colors.iconWeak) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = containerColor, unfocusedContainerColor = containerColor, disabledContainerColor = containerColor,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
            cursorColor = ProtonTheme.colors.brandNorm
        ),
        singleLine = true
    )
}

@Composable
private fun IpInput(isLight: Boolean, error: Int?, onAdd: (String) -> Unit, onClearError: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val containerColor = if (isLight) Color(0xFFE0E0E0) else Color(0xFF2E2E34)

    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = text,
                onValueChange = { text = it; if (error != null) onClearError() },
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)),
                placeholder = { Text(stringResource(R.string.inputIpAddressHelp), style = ProtonTheme.typography.defaultWeak, color = ProtonTheme.colors.textWeak) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Uri),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) { onAdd(text); text = ""; focusManager.clearFocus() } }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = containerColor, unfocusedContainerColor = containerColor, disabledContainerColor = containerColor,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
                    cursorColor = ProtonTheme.colors.brandNorm, errorContainerColor = containerColor, errorIndicatorColor = Color.Transparent
                ),
                singleLine = true,
                isError = error != null
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(ProtonTheme.colors.brandNorm).clickable(enabled = text.isNotBlank()) {
                if (text.isNotBlank()) { onAdd(text); text = ""; focusManager.clearFocus() }
            }, contentAlignment = Alignment.Center) {
                Icon(painterResource(CoreR.drawable.ic_proton_plus), null, tint = Color.White)
            }
        }
        if (error != null) {
            Text(stringResource(error), style = ProtonTheme.typography.defaultSmallWeak, color = ProtonTheme.colors.notificationError, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        }
    }
}

@Composable
private fun AppItem(item: LabeledItem, isAdded: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            AndroidView(
                modifier = Modifier.size(32.dp),
                factory = { context ->
                    ImageView(context).apply {
                        setImageDrawable(item.iconDrawable)
                    }
                },
                update = {
                    it.setImageDrawable(item.iconDrawable)
                }
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(item.label, style = ProtonTheme.typography.defaultNorm, color = ProtonTheme.colors.textNorm, modifier = Modifier.weight(1f), maxLines = 2)
        Icon(painterResource(if (isAdded) CoreR.drawable.ic_proton_cross else CoreR.drawable.ic_proton_plus), null, tint = if (isAdded) ProtonTheme.colors.textNorm else ProtonTheme.colors.brandNorm, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun IpItem(ip: String, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onRemove).padding(vertical = 12.dp, horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(CoreR.drawable.ic_proton_globe), null, tint = ProtonTheme.colors.iconWeak, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(ip, style = ProtonTheme.typography.defaultNorm, color = ProtonTheme.colors.textNorm, modifier = Modifier.weight(1f))
        Icon(painterResource(CoreR.drawable.ic_proton_cross), null, tint = ProtonTheme.colors.textNorm, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = ProtonTheme.typography.defaultSmallWeak.copy(fontWeight = FontWeight.Bold), color = ProtonTheme.colors.textWeak, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun EmptyState(textRes: Int) {
    Text(stringResource(textRes), style = ProtonTheme.typography.defaultWeak, color = ProtonTheme.colors.textWeak, modifier = Modifier.padding(16.dp))
}

@Composable
private fun LoadSystemAppsButton(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.settings_split_tunneling_load_system_apps), style = ProtonTheme.typography.defaultNorm.copy(fontWeight = FontWeight.Bold), color = ProtonTheme.colors.brandNorm)
    }
}