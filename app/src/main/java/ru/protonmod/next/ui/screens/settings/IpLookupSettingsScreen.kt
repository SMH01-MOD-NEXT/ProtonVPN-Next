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
    val tablet = isTablet()
    val services = listOf(
        Triple(setOf("proton"), R.string.ip_lookup_proton, R.string.ip_lookup_proton_desc),
        Triple(setOf("cloudflare", "cloudflare-site"), R.string.ip_lookup_cloudflare, R.string.ip_lookup_own_desc),
        Triple(setOf("deno"), R.string.ip_lookup_deno, R.string.ip_lookup_own_desc),
        Triple(setOf("vercel"), R.string.ip_lookup_vercel, R.string.ip_lookup_own_desc),
        Triple(setOf("event"), R.string.ip_lookup_event, R.string.ip_lookup_event_desc),
        Triple(setOf("api.myip.com"), R.string.ip_lookup_myip, R.string.ip_lookup_public_desc),
        Triple(setOf("freeipapi"), R.string.ip_lookup_freeipapi, R.string.ip_lookup_public_desc),
    )
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
                item {
                    SettingsCategory(stringResource(R.string.ip_lookup_services), content.padding(horizontal = 16.dp)) {
                        services.forEach { (ids, title, description) ->
                            SettingToggleRow(
                                icon = ProtonIcons.Earth, title = stringResource(title),
                                subtitle = stringResource(description), checked = ids.any { it in enabled },
                                onCheckedChange = { viewModel.setEnabled(ids, it) },
                            )
                        }
                    }
                }
                item {
                    SettingsCategory("", content.padding(horizontal = 16.dp)) {
                        SettingRowWithIcon(
                            icon = ProtonIcons.ShieldFilled,
                            title = stringResource(R.string.ip_lookup_disable_all),
                            subtitle = stringResource(if (enabled.isEmpty()) R.string.ip_lookup_disabled else R.string.ip_lookup_disable_desc),
                            onClick = viewModel::disableAll,
                        )
                    }
                }
            }
        }
    }
}
