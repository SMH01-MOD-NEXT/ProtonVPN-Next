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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.SettingsFeatureToggle
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.theme.ThemeType
import com.protonvpn.android.vpn.DnsOverride
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun NetShieldSetting(
    onClose: () -> Unit,
    netShield: SettingsViewModel.SettingViewState.NetShield,
    onLearnMore: () -> Unit,
    onNetShieldToggle: () -> Unit,
    onDisableCustomDns: () -> Unit,
    onCustomDnsLearnMore: () -> Unit,
    onPrivateDnsLearnMore: () -> Unit,
    onOpenPrivateDnsSettings: () -> Unit,
) {
    val listState = rememberLazyListState()

    val themeType = LocalThemeType.current

    val isAmoled = themeType == ThemeType.Amoled || themeType == ThemeType.NewYearAmoled
    val border = if (isAmoled) BorderStroke(1.dp, Color.White) else null

    val isLight = themeType == ThemeType.Light || themeType == ThemeType.NewYearLight ||
            (themeType == ThemeType.System && !isSystemInDarkTheme())
    val cardColor = if (isLight) Color(0xFFF0F0F0) else ProtonTheme.colors.backgroundSecondary

    FeatureSubSettingScaffold(
        title = stringResource(id = netShield.titleRes),
        onClose = onClose,
        listState = listState,
        titleInListIndex = 1,
    ) { contentPadding ->
        val horizontalItemPaddingModifier = Modifier
            .padding(horizontal = 16.dp)
            .largeScreenContentPadding()

        LazyColumn(
            state = listState,
            modifier = Modifier.padding(contentPadding)
        ) {
            addFeatureSettingItems(
                setting = netShield,
                imageRes = R.drawable.setting_netshield,
                onLearnMore = onLearnMore,
                itemModifier = horizontalItemPaddingModifier,
            )
            item {
                Card(
                    modifier = horizontalItemPaddingModifier.padding(top = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    border = border,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        when (netShield.dnsOverride) {
                            DnsOverride.None -> SettingsFeatureToggle(
                                label = stringResource(netShield.titleRes),
                                checked = netShield.value,
                                onCheckedChange = { _ -> onNetShieldToggle() },
                                modifier = Modifier.fillMaxWidth()
                            )

                            DnsOverride.CustomDns -> DnsConflictBanner(
                                titleRes = R.string.custom_dns_conflict_banner_netshield_title,
                                descriptionRes = R.string.custom_dns_conflict_banner_netshield_description,
                                buttonRes = R.string.custom_dns_conflict_banner_disable_custom_dns_button,
                                onLearnMore = onCustomDnsLearnMore,
                                onButtonClicked = onDisableCustomDns,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            DnsOverride.SystemPrivateDns -> DnsConflictBanner(
                                titleRes = R.string.private_dns_conflict_banner_netshield_title,
                                descriptionRes = R.string.private_dns_conflict_banner_netshield_description,
                                buttonRes = R.string.private_dns_conflict_banner_network_settings_button,
                                onLearnMore = onPrivateDnsLearnMore,
                                onButtonClicked = onOpenPrivateDnsSettings,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = stringResource(id = R.string.netshield_setting_warning),
                    style = ProtonTheme.typography.body2Regular,
                    color =  ProtonTheme.colors.textWeak,
                    modifier = horizontalItemPaddingModifier.padding(top = 16.dp, bottom = 16.dp),
                )
            }
        }
    }
}