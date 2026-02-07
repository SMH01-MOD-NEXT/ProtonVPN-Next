/*
 * Copyright (c) 2024 Proton AG
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

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun AdvancedSettings(
    onClose: () -> Unit,
    profileOverrideInfo: SettingsViewModel.ProfileOverrideInfo?,
    altRouting: SettingsViewModel.SettingViewState.AltRouting,
    allowLan: SettingsViewModel.SettingViewState.LanConnections,
    ipV6: SettingsViewModel.SettingViewState.IPv6?,
    natType: SettingsViewModel.SettingViewState.Nat,
    customDns: SettingsViewModel.SettingViewState.CustomDns?,
    onAltRoutingChange: () -> Unit,
    onNavigateToLan: () -> Unit,
    onIPv6Toggle: () -> Unit,
    onNatTypeLearnMore: () -> Unit,
    onNavigateToNatType: () -> Unit,
    onNavigateToCustomDns: () -> Unit,
    onNavigateToCountrySpoofing: () -> Unit,
    onCustomDnsLearnMore: () -> Unit,
    onCustomDnsRestricted: () -> Unit,
    onAllowLanRestricted: () -> Unit,
    onNatTypeRestricted: () -> Unit,
    onIPv6InfoClick: () -> Unit,
) {
    SubSetting(
        title = stringResource(id = R.string.settings_advanced_settings_title),
        onClose = onClose
    ) {
        profileOverrideInfo?.let {
            ProfileOverrideView(
                modifier = Modifier.padding(bottom = 8.dp),
                profileOverrideInfo = it
            )
        }

        // Alt Routing (Toggle)
        AdvancedToggleRow(
            title = stringResource(id = altRouting.titleRes),
            icon = CoreR.drawable.ic_proton_arrow_in_to_rectangle,
            checked = altRouting.value,
            onToggle = onAltRoutingChange
        )

        // LAN Connections (Navigation)
        SettingRowWithIcon(
            title = stringResource(id = allowLan.titleRes),
            icon = R.drawable.ic_lan,
            settingValue = allowLan.settingValueView,
            onClick = if (allowLan.isRestricted) onAllowLanRestricted else onNavigateToLan,
            trailingIcon = if (allowLan.isRestricted) R.drawable.vpn_plus_badge else null,
            trailingIconTint = false
        )

        SettingRowWithIcon(
            icon = CoreR.drawable.ic_proton_globe, // Используем иконку глобуса или другую подходящую
            title = stringResource(R.string.settings_country_spoofing_title),
            onClick = onNavigateToCountrySpoofing,
            trailingIcon = null // Стрелочка будет добавлена автоматически внутри SettingRowWithIcon, если onClick != null
        )

        // NAT Type (Navigation)
        SettingRowWithIcon(
            title = stringResource(id = natType.titleRes),
            icon = CoreR.drawable.ic_proton_shield,
            settingValue = natType.settingValueView,
            onClick = if (natType.isRestricted) onNatTypeRestricted else onNavigateToNatType,
            trailingIcon = if (natType.isRestricted) R.drawable.vpn_plus_badge else null,
            trailingIconTint = false
        )

        // Custom DNS (Navigation)
        if (customDns != null) {
            SettingRowWithIcon(
                title = stringResource(id = customDns.titleRes),
                icon = R.drawable.ic_dns,
                settingValue = customDns.settingValueView,
                onClick = if (customDns.isRestricted) onCustomDnsRestricted else onNavigateToCustomDns,
                trailingIcon = if (customDns.isRestricted) R.drawable.vpn_plus_badge else null,
                trailingIconTint = false
            )
        }

        // IPv6 (Toggle)
        if (ipV6 != null) {
            AdvancedToggleRow(
                title = stringResource(id = ipV6.titleRes),
                icon = R.drawable.ic_ipv6,
                checked = ipV6.value,
                onToggle = onIPv6Toggle
            )
        }
    }
}

@Composable
private fun AdvancedToggleRow(
    title: String,
    @DrawableRes icon: Int,
    checked: Boolean,
    onToggle: () -> Unit
) {
    SettingRow(
        title = title,
        leadingComposable = {
            // Replicating the Icon style from SettingsView
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ProtonTheme.colors.interactionNorm.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = null,
                    tint = ProtonTheme.colors.interactionNorm,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        trailingComposable = {
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() }
            )
        },
        onClick = onToggle
    )
}