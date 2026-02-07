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

package com.protonvpn.android.redesign.settings.ui.countryspoofing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.protonvpn.android.R
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.redesign.settings.ui.BasicSubSetting
import com.protonvpn.android.redesign.settings.ui.LocalThemeType
import com.protonvpn.android.theme.ThemeType
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultWeak

@Composable
fun CountrySpoofingScreen(
    onClose: () -> Unit,
    viewModel: CountrySpoofingViewModel = hiltViewModel()
) {
    val state by viewModel.viewState.collectAsState()
    val themeType = LocalThemeType.current

    // Theme logic matched with LanSetting/SplitTunneling
    val isAmoled = themeType == ThemeType.Amoled || themeType == ThemeType.NewYearAmoled
    val border = if (isAmoled) BorderStroke(1.dp, Color.White) else null
    val isLight = themeType == ThemeType.Light || themeType == ThemeType.NewYearLight ||
            (themeType == ThemeType.System && !isSystemInDarkTheme())
    val cardColor = if (isLight) Color(0xFFF0F0F0) else ProtonTheme.colors.backgroundSecondary

    BasicSubSetting(
        title = stringResource(R.string.settings_country_spoofing_title),
        onClose = {
            // Check for changes and trigger update only if necessary before closing
            viewModel.onScreenExit()
            onClose()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .largeScreenContentPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Description
            Text(
                text = stringResource(R.string.settings_country_spoofing_description),
                style = ProtonTheme.typography.defaultWeak,
                color = ProtonTheme.colors.textNorm,
                modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                border = border,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    // Main Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleEnabled(!state.isEnabled) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (state.isEnabled) stringResource(R.string.split_tunneling_state_on) else stringResource(R.string.split_tunneling_state_off),
                            style = ProtonTheme.typography.defaultNorm.copy(fontWeight = FontWeight.Bold),
                            color = ProtonTheme.colors.textNorm,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = state.isEnabled,
                            onCheckedChange = { viewModel.toggleEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ProtonTheme.colors.interactionNorm,
                                checkedTrackColor = ProtonTheme.colors.interactionNorm.copy(alpha = 0.3f),
                                uncheckedThumbColor = ProtonTheme.colors.iconWeak,
                                uncheckedTrackColor = ProtonTheme.colors.backgroundSecondary
                            )
                        )
                    }

                    if (state.isEnabled) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = ProtonTheme.colors.separatorNorm
                        )

                        // Null Spoof Checkbox
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleNullSpoof(!state.isNullSpoof) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_country_spoofing_null_label),
                                    style = ProtonTheme.typography.defaultNorm.copy(fontWeight = FontWeight.Medium),
                                    color = ProtonTheme.colors.textNorm
                                )
                                Text(
                                    text = stringResource(R.string.settings_country_spoofing_null_desc),
                                    style = ProtonTheme.typography.defaultWeak,
                                    color = ProtonTheme.colors.textWeak
                                )
                            }
                            Checkbox(
                                checked = state.isNullSpoof,
                                onCheckedChange = { viewModel.toggleNullSpoof(it) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = ProtonTheme.colors.interactionNorm,
                                    uncheckedColor = ProtonTheme.colors.iconWeak
                                )
                            )
                        }

                        // Country Code Input (Hidden if Null Spoof is active)
                        if (!state.isNullSpoof) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = ProtonTheme.colors.separatorNorm
                            )

                            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp)) {
                                val focusManager = LocalFocusManager.current
                                // Local state to prevent stuttering while typing, updated by flow collection
                                var text by remember(state.countryCode) { mutableStateOf(state.countryCode) }

                                OutlinedTextField(
                                    value = text,
                                    onValueChange = {
                                        if (it.length <= 2) {
                                            text = it.uppercase()
                                            viewModel.setCountryCode(it)
                                        }
                                    },
                                    label = { Text(stringResource(R.string.settings_country_spoofing_code_label)) },
                                    placeholder = { Text(stringResource(R.string.settings_country_spoofing_code_hint)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = ProtonTheme.typography.defaultNorm,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ProtonTheme.colors.interactionNorm,
                                        unfocusedBorderColor = ProtonTheme.colors.iconWeak,
                                        focusedLabelColor = ProtonTheme.colors.interactionNorm,
                                        unfocusedLabelColor = ProtonTheme.colors.textWeak,
                                        cursorColor = ProtonTheme.colors.interactionNorm,
                                        focusedTextColor = ProtonTheme.colors.textNorm,
                                        unfocusedTextColor = ProtonTheme.colors.textNorm
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}