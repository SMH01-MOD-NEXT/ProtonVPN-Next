/*
 * Copyright (c) 2024. Proton AG
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

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.AnnotatedClickableText
import com.protonvpn.android.base.ui.LabelBadge
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.redesign.base.ui.ProtonAlert
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.theme.ThemeType
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.openUrl
import com.protonvpn.android.vpn.ProtocolSelection
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultWeak

@Composable
fun ProtocolSettings(
    onClose: () -> Unit,
    protocolViewState: SettingsViewModel.SettingViewState.Protocol,
    onLearnMore: () -> Unit,
    onProtocolSelected: (ProtocolSelection) -> Unit,
) {
    // Theme logic for Card appearance
    val themeType = LocalThemeType.current
    val isAmoled = themeType == ThemeType.Amoled || themeType == ThemeType.NewYearAmoled
    val border = if (isAmoled) BorderStroke(1.dp, Color.White) else null

    val isLight = themeType == ThemeType.Light || themeType == ThemeType.NewYearLight ||
            (themeType == ThemeType.System && !isSystemInDarkTheme())
    val cardColor = if (isLight) Color(0xFFF0F0F0) else ProtonTheme.colors.backgroundSecondary

    val listState = rememberLazyListState()

    BasicSubSetting(
        title = "", // Title is handled inside the list header
        onClose = onClose
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .largeScreenContentPadding()
                .padding(horizontal = 16.dp)
        ) {
            // 1. Header Section (Title & Description)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = stringResource(protocolViewState.titleRes),
                        style = ProtonTheme.typography.defaultNorm.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = ProtonTheme.colors.textNorm,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Description text with clickable annotation
                    protocolViewState.descriptionText()?.let { descriptionText ->
                        if (protocolViewState.annotationRes != null) {
                            AnnotatedClickableText(
                                fullText = descriptionText,
                                annotatedPart = stringResource(protocolViewState.annotationRes),
                                onAnnotatedClick = onLearnMore,
                                style = ProtonTheme.typography.body2Regular,
                                annotatedStyle = ProtonTheme.typography.body2Medium.copy(
                                    textDecoration = TextDecoration.Underline,
                                    color = ProtonTheme.colors.brandNorm
                                ),
                                color = ProtonTheme.colors.textWeak,
                            )
                        } else {
                            Text(
                                text = descriptionText,
                                style = ProtonTheme.typography.body2Regular,
                                color = ProtonTheme.colors.textWeak,
                            )
                        }
                    }
                }
            }

            // 2. Protocols Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    border = border,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) {
                    ProtocolSettingsList(
                        currentProtocol = protocolViewState.value,
                        onProtocolSelected = onProtocolSelected,
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalContentPadding = 16.dp
                    )
                }
            }
        }
    }
}

@Composable
fun ProtocolSettingsList(
    currentProtocol: ProtocolSelection,
    onProtocolSelected: (ProtocolSelection) -> Unit,
    modifier: Modifier = Modifier,
    horizontalContentPadding: Dp = 16.dp,
) {
    // State for OpenVPN deprecation dialog (inside the list so it works in modals too)
    val openVpnDeprecationDialogForTransmission =
        rememberSaveable(currentProtocol) { mutableStateOf<TransmissionProtocol?>(null) }

    openVpnDeprecationDialogForTransmission.value?.let { transmission ->
        OpenVpnDeprecationDialog(
            transmission,
            onProtocolSelected,
            dismissDialog = { openVpnDeprecationDialogForTransmission.value = null }
        )
    }

    Column(modifier = modifier) {
        // -- Smart Protocol --
        ProtocolItem(
            itemProtocol = ProtocolSelection.SMART,
            title = R.string.settings_protocol_smart_title,
            description = R.string.settings_protocol_smart_description,
            onProtocolSelected = onProtocolSelected,
            selectedProtocol = currentProtocol,
            horizontalContentPadding = horizontalContentPadding,
            trailingTitleContent = {
                LabelBadge(stringResource(R.string.settings_protocol_badge_recommended))
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = horizontalContentPadding),
            color = ProtonTheme.colors.separatorNorm
        )

        // -- Speed Section --
        ProtocolSectionHeader(
            text = stringResource(R.string.settings_protocol_section_speed),
            paddingStart = horizontalContentPadding
        )

        ProtocolItem(
            itemProtocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.UDP),
            title = R.string.settings_protocol_wireguard_title,
            description = R.string.settings_protocol_wireguard_udp_description,
            onProtocolSelected = onProtocolSelected,
            selectedProtocol = currentProtocol,
            horizontalContentPadding = horizontalContentPadding
        )
        ProtocolItem(
            itemProtocol = ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.UDP),
            title = R.string.settings_protocol_openvpn_title,
            description = R.string.settings_protocol_openvpn_udp_description,
            onProtocolSelected = { openVpnDeprecationDialogForTransmission.value = TransmissionProtocol.UDP },
            selectedProtocol = currentProtocol,
            horizontalContentPadding = horizontalContentPadding
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = horizontalContentPadding),
            color = ProtonTheme.colors.separatorNorm
        )

        // -- Reliability Section --
        ProtocolSectionHeader(
            text = stringResource(R.string.settings_protocol_section_reliability),
            paddingStart = horizontalContentPadding
        )

        ProtocolItem(
            itemProtocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TCP),
            title = R.string.settings_protocol_wireguard_title,
            description = R.string.settings_protocol_wireguard_tcp_description,
            onProtocolSelected = onProtocolSelected,
            selectedProtocol = currentProtocol,
            horizontalContentPadding = horizontalContentPadding
        )
        ProtocolItem(
            itemProtocol = ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.TCP),
            title = R.string.settings_protocol_openvpn_title,
            description = R.string.settings_protocol_openvpn_tcp_description,
            onProtocolSelected = { openVpnDeprecationDialogForTransmission.value = TransmissionProtocol.TCP },
            selectedProtocol = currentProtocol,
            horizontalContentPadding = horizontalContentPadding
        )
        ProtocolItem(
            itemProtocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS),
            title = R.string.settings_protocol_stealth_title,
            description = R.string.settings_protocol_stealth_description,
            onProtocolSelected = onProtocolSelected,
            selectedProtocol = currentProtocol,
            horizontalContentPadding = horizontalContentPadding
        )
    }
}

@Composable
fun OpenVpnDeprecationDialog(
    transmission: TransmissionProtocol,
    selectProtocol: (ProtocolSelection) -> Unit,
    dismissDialog: () -> Unit,
) {
    val context = LocalContext.current
    val textHtml = stringResource(R.string.settings_protocol_openvpn_deprecation_dialog_description)
    val text = AnnotatedString.fromHtml(
        textHtml,
        linkStyles = TextLinkStyles(
            style = ProtonTheme.typography.body2Regular.copy(
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline
            ).toSpanStyle(),
        ),
        linkInteractionListener = {
            context.openUrl(Constants.URL_OPENVPN_DEPRECATION)
        }
    )
    ProtonAlert(
        title = stringResource(R.string.settings_protocol_openvpn_deprecation_dialog_title),
        text = text,
        textColor = ProtonTheme.colors.textWeak,
        onDismissRequest = dismissDialog,
        confirmLabel = stringResource(R.string.settings_protocol_openvpn_change_dialog_continue_openvpn),
        dismissLabel = stringResource(R.string.settings_protocol_openvpn_change_dialog_enable_smart),
        onConfirm = {
            selectProtocol(ProtocolSelection(VpnProtocol.OpenVPN, transmission))
            dismissDialog()
        },
        onDismissButton = {
            selectProtocol(ProtocolSelection.SMART)
            dismissDialog()
        }
    )
}

@Composable
fun ProtocolItem(
    itemProtocol: ProtocolSelection,
    @StringRes title: Int,
    @StringRes description: Int,
    onProtocolSelected: (ProtocolSelection) -> Unit,
    selectedProtocol: ProtocolSelection,
    modifier: Modifier = Modifier,
    horizontalContentPadding: Dp = 16.dp,
    trailingTitleContent: (@Composable () -> Unit)? = null,
) {
    ProtocolOption(
        title = stringResource(id = title),
        description = stringResource(id = description),
        isSelected = itemProtocol == selectedProtocol,
        onClick = { onProtocolSelected(itemProtocol) },
        horizontalPadding = horizontalContentPadding,
        trailingTitleContent = trailingTitleContent
    )
}

@Composable
private fun ProtocolOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    horizontalPadding: Dp = 16.dp,
    trailingTitleContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = ProtonTheme.typography.defaultNorm,
                )
                if (trailingTitleContent != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailingTitleContent()
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = ProtonTheme.typography.defaultWeak,
                color = ProtonTheme.colors.textWeak
            )
        }
        RadioButton(
            selected = isSelected,
            onClick = null, // Handled by Row click
            colors = RadioButtonDefaults.colors(
                selectedColor = ProtonTheme.colors.interactionNorm,
                unselectedColor = ProtonTheme.colors.iconWeak
            ),
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
private fun ProtocolSectionHeader(
    text: String,
    paddingStart: Dp = 16.dp
) {
    Text(
        text = text,
        style = ProtonTheme.typography.defaultNorm.copy(fontWeight = FontWeight.Bold),
        color = ProtonTheme.colors.textNorm,
        modifier = Modifier
            .padding(start = paddingStart, end = 16.dp, top = 12.dp, bottom = 4.dp)
            .fillMaxWidth()
    )
}