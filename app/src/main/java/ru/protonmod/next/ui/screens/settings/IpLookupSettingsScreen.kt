/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.R
import ru.protonmod.next.data.network.ip.IpEchoSources
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.utils.isTablet

@Composable
fun IpLookupSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IpLookupSettingsViewModel = hiltViewModel(),
) {
    val colors = ProtonNextTheme.colors
    val enabled by viewModel.enabledIds.collectAsStateWithLifecycle()
    val updateFailed by viewModel.updateFailed.collectAsStateWithLifecycle()
    val tablet = isTablet()
    val services by viewModel.catalogue.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier.fillMaxSize(), containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                colors.brandNorm.copy(alpha = 0.25f),
                colors.backgroundNorm.copy(alpha = 0.1f), colors.backgroundNorm,
            ))))
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                horizontalAlignment = if (tablet) Alignment.CenterHorizontally else Alignment.Start,
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val content = if (tablet) Modifier.widthIn(max = 600.dp) else Modifier.fillMaxWidth()
                item { NavigationHeader(stringResource(R.string.ip_lookup_title), onBack) }
                item {
                    Text(stringResource(R.string.ip_lookup_explanation),
                        color = colors.textWeak, style = MaterialTheme.typography.bodyMedium,
                        modifier = content.padding(horizontal = 24.dp))
                }
                if (updateFailed) {
                    item {
                        Text(stringResource(R.string.ip_lookup_update_failed),
                            color = colors.notificationWarning, modifier = content.padding(horizontal = 24.dp))
                    }
                }
                item {
                    SettingsCategory(stringResource(R.string.ip_lookup_services), content.padding(horizontal = 16.dp)) {
                        services.forEach { service ->
                            key(service.id) {
                                val title = if (service.kind == IpEchoSources.Kind.EVENT)
                                    stringResource(R.string.ip_lookup_event_named, service.name)
                                else stringResource(service.titleRes)
                                val description = when (service.kind) {
                                    IpEchoSources.Kind.PROTON -> R.string.ip_lookup_proton_desc
                                    IpEchoSources.Kind.EVENT -> R.string.ip_lookup_dynamic_event_desc
                                    IpEchoSources.Kind.PUBLIC -> R.string.ip_lookup_public_desc
                                    IpEchoSources.Kind.BUILT_IN -> R.string.ip_lookup_own_desc
                                }
                                SettingToggleRow(
                                    icon = ProtonIcons.Earth, title = title,
                                    subtitle = stringResource(description), checked = service.id in enabled,
                                    onCheckedChange = { viewModel.setEnabled(setOf(service.id), it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
