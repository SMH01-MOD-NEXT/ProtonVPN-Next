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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.AnnotatedClickableText
import com.protonvpn.android.base.ui.LabelBadge
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.redesign.base.ui.ProtonAlert
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
    SubSetting(
        title = stringResource(protocolViewState.titleRes),
        onClose = onClose
    ) {
        ProtocolSettingsList(
            currentProtocol = protocolViewState.value,
            onProtocolSelected = onProtocolSelected,
        )

        // Footer description
        val footerPadding = Modifier.padding(top = 12.dp, bottom = 8.dp, start = 16.dp, end = 16.dp)
        protocolViewState.descriptionText()?.let { descriptionText ->
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = ProtonTheme.colors.separatorNorm
            )

            if (protocolViewState.annotationRes != null) {
                AnnotatedClickableText(
                    fullText = descriptionText,
                    annotatedPart = stringResource(protocolViewState.annotationRes),
                    onAnnotatedClick = onLearnMore,
                    style = ProtonTheme.typography.body2Regular,
                    annotatedStyle = ProtonTheme.typography.body2Medium,
                    color = ProtonTheme.colors.textWeak,
                    modifier = footerPadding,
                )
            } else {
                Text(
                    text = descriptionText,
                    style = ProtonTheme.typography.body2Regular,
                    color = ProtonTheme.colors.textWeak,
                    modifier = footerPadding,
                )
            }
        }
    }
}

@Composable
fun ProtocolSettingsList(
    currentProtocol: ProtocolSelection,
    onProtocolSelected: (ProtocolSelection) -> Unit,
    modifier: Modifier = Modifier,
    horizontalContentPadding: Dp = 16.dp, // Kept for API compatibility, but handled internally by ProtocolOption padding
) {
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
        // Smart Protocol
        ProtocolItem(
            itemProtocol = ProtocolSelection.SMART,
            title = R.string.settings_protocol_smart_title,
            description = R.string.settings_protocol_smart_description,
            onProtocolSelected = onProtocolSelected,
            selectedProtocol = currentProtocol,
            trailingTitleContent = {
                LabelBadge(stringResource(R.string.settings_protocol_badge_recommended))
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = ProtonTheme.colors.separatorNorm
        )

        // Speed Section
        ProtocolSectionHeader(text = stringResource(R.string.settings_protocol_section_speed))
        ProtocolItem(
            itemProtocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.UDP),
            title = R.string.settings_protocol_wireguard_title,
            description = R.string.settings_protocol_wireguard_udp_description,
            onProtocolSelected = onProtocolSelected,
            selectedProtocol = currentProtocol,
        )
        ProtocolItem(
            itemProtocol = ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.UDP),
            title = R.string.settings_protocol_openvpn_title,
            description = R.string.settings_protocol_openvpn_udp_description,
            onProtocolSelected = { openVpnDeprecationDialogForTransmission.value = TransmissionProtocol.UDP },
            selectedProtocol = currentProtocol,
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = ProtonTheme.colors.separatorNorm
        )

        // Reliability Section
        ProtocolSectionHeader(text = stringResource(R.string.settings_protocol_section_reliability))
        ProtocolItem(
            itemProtocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TCP),
            title = R.string.settings_protocol_wireguard_title,
            description = R.string.settings_protocol_wireguard_tcp_description,
            onProtocolSelected = onProtocolSelected,
            selectedProtocol = currentProtocol,
        )
        ProtocolItem(
            itemProtocol = ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.TCP),
            title = R.string.settings_protocol_openvpn_title,
            description = R.string.settings_protocol_openvpn_tcp_description,
            onProtocolSelected = { openVpnDeprecationDialogForTransmission.value = TransmissionProtocol.TCP },
            selectedProtocol = currentProtocol,
        )
        ProtocolItem(
            itemProtocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS),
            title = R.string.settings_protocol_stealth_title,
            description = R.string.settings_protocol_stealth_description,
            onProtocolSelected = onProtocolSelected,
            selectedProtocol = currentProtocol,
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
    horizontalContentPadding: Dp = 16.dp, // Unused but kept for compatibility
    trailingTitleContent: (@Composable () -> Unit)? = null,
) {
    ProtocolOption(
        title = stringResource(id = title),
        description = stringResource(id = description),
        isSelected = itemProtocol == selectedProtocol,
        onClick = { onProtocolSelected(itemProtocol) },
        trailingTitleContent = trailingTitleContent
    )
}

@Composable
private fun ProtocolOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    trailingTitleContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
private fun ProtocolSectionHeader(text: String) {
    Text(
        text = text,
        style = ProtonTheme.typography.defaultNorm.copy(fontWeight = FontWeight.Bold),
        color = ProtonTheme.colors.textNorm,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
    )
}