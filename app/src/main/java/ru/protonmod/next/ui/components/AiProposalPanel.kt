/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import ru.protonmod.next.R
import ru.protonmod.next.data.ai.AiProfilePreview
import ru.protonmod.next.data.ai.AiProposal
import ru.protonmod.next.data.ai.AiProposedAction
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.screens.profiles.FeatureBadge
import ru.protonmod.next.ui.screens.profiles.getProfileAccent
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.CountryUtils
import ru.protonmod.next.ui.utils.isTablet

@Composable
fun AiProposalPanel(
    proposal: AiProposal?,
    isProcessing: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = proposal != null,
        enter = slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut(),
        modifier = modifier,
    ) {
        proposal?.let { ProposalContent(it, isProcessing, onApply, onDismiss) }
    }
}

@Composable
private fun ProposalContent(
    proposal: AiProposal,
    isProcessing: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ProtonNextTheme.colors
    val tablet = isTablet()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(if (tablet) 0.56f else 0.63f)
            .widthIn(max = if (tablet) 720.dp else 640.dp)
            .padding(horizontal = 16.dp)
            .liquidGlass(shape = RoundedCornerShape(28.dp), alpha = 0.95f, shadowElevation = 16.dp)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(colors.brandNorm.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ProtonIcons.MagicProtonWand, null, tint = colors.brandNorm, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.ai_proposal_preview_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.brandNorm,
                )
                Text(
                    proposal.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textNorm,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDismiss, enabled = !isProcessing) {
                Icon(ProtonIcons.Cross, stringResource(R.string.ai_proposal_close), tint = colors.iconWeak)
            }
        }

        Text(
            proposal.summary,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textWeak,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 54.dp, end = 8.dp, bottom = 12.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 10.dp),
        ) {
            items(proposal.actions, key = { it.id }) { action ->
                if (action.profilePreview != null) {
                    AiProfileProposalCard(
                        profile = action.profilePreview,
                        isUpdate = action.type == "update_profile",
                    )
                } else {
                    GenericProposalAction(action)
                }
            }
        }

        Text(
            stringResource(R.string.ai_refine_bottom_hint),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textWeak,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isProcessing,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.ai_proposal_cancel))
            }
            Button(
                onClick = onApply,
                enabled = !isProcessing,
                modifier = Modifier.weight(1.45f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm),
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.textInverted)
                } else {
                    Icon(ProtonIcons.Checkmark, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ai_proposal_apply))
                }
            }
        }
    }
}

@Composable
private fun AiProfileProposalCard(profile: AiProfilePreview, isUpdate: Boolean) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current
    val accent = remember(profile.serverId, profile.city, profile.country, colors.brandNorm) {
        getProfileAccent(profile.serverId, profile.city, profile.country, colors.brandNorm)
    }
    val countryName = profile.country?.let { CountryUtils.getCountryName(context, it) }
    val serverValue = profile.serverName ?: profile.serverId ?: stringResource(R.string.ai_profile_fastest)
    val cityValue = profile.city ?: stringResource(R.string.ai_profile_any_city)
    val portValue = if (profile.port == 0) stringResource(R.string.settings_port_auto) else profile.port.toString()
    val obfuscationValue = if (profile.obfuscationEnabled) {
        profile.obfuscationProfileName ?: profile.obfuscationProfileId ?: stringResource(R.string.ai_profile_standard_obfuscation)
    } else stringResource(R.string.settings_off)
    val connectGoValue = profile.connectAndGoUrl ?: stringResource(R.string.settings_off)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(shape = RoundedCornerShape(24.dp), alpha = 0.42f, shadowElevation = 0.dp)
            .background(
                Brush.linearGradient(
                    listOf(accent.start.copy(alpha = 0.24f), Color.Transparent, accent.end.copy(alpha = 0.14f))
                ),
                RoundedCornerShape(24.dp),
            )
            .padding(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp, 44.dp).clip(RoundedCornerShape(11.dp))
                        .background(accent.start.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    val flag = profile.country?.let { CountryUtils.getFlagResource(context, it) } ?: 0
                    if (flag != 0) {
                        FlagIcon(countryFlag = flag, size = DpSize(64.dp, 44.dp))
                    } else if (profile.country != null) {
                        Text(CountryUtils.getFlagForCountry(profile.country), style = MaterialTheme.typography.titleLarge)
                    } else {
                        FlagIcon(countryFlag = R.drawable.flag_fastest, size = DpSize(64.dp, 44.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        profile.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textNorm,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            profile.serverId != null -> serverValue
                            profile.city != null -> listOfNotNull(profile.city, countryName).joinToString(", ")
                            countryName != null -> countryName
                            else -> stringResource(R.string.location_fastest)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textWeak,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FeatureBadge(
                    text = stringResource(if (isUpdate) R.string.ai_profile_edit_badge else R.string.ai_profile_new_badge),
                    accent = accent.start,
                )
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = colors.separatorNorm.copy(alpha = 0.55f))
            Spacer(Modifier.height(12.dp))

            ProfileDetailPair(
                leftLabel = stringResource(R.string.ai_profile_country),
                leftValue = countryName ?: stringResource(R.string.ai_profile_any_country),
                rightLabel = stringResource(R.string.ai_profile_city),
                rightValue = cityValue,
            )
            Spacer(Modifier.height(10.dp))
            ProfileDetailPair(
                leftLabel = stringResource(R.string.ai_profile_server),
                leftValue = serverValue,
                rightLabel = stringResource(R.string.ai_profile_port),
                rightValue = portValue,
            )
            Spacer(Modifier.height(10.dp))
            ProfileDetailPair(
                leftLabel = stringResource(R.string.ai_profile_protocol),
                leftValue = profile.protocol,
                rightLabel = stringResource(R.string.ai_profile_obfuscation),
                rightValue = obfuscationValue,
                rightEnabled = profile.obfuscationEnabled,
            )
            Spacer(Modifier.height(10.dp))
            ProfileDetail(
                label = stringResource(R.string.ai_profile_connect_go),
                value = connectGoValue,
                enabled = profile.connectAndGoUrl != null,
            )
        }
    }
}

@Composable
private fun ProfileDetailPair(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
    rightEnabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileDetail(leftLabel, leftValue, modifier = Modifier.weight(1f))
        ProfileDetail(rightLabel, rightValue, modifier = Modifier.weight(1f), enabled = rightEnabled)
    }
}

@Composable
private fun ProfileDetail(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = ProtonNextTheme.colors
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textWeak)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(7.dp).background(
                    if (enabled) colors.notificationSuccess else colors.iconWeak.copy(alpha = 0.6f),
                    CircleShape,
                )
            )
            Spacer(Modifier.width(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.textNorm,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GenericProposalAction(action: AiProposedAction) {
    val colors = ProtonNextTheme.colors
    val accent = if (action.destructive) colors.notificationError else colors.brandNorm
    Surface(color = colors.backgroundSecondary.copy(alpha = 0.58f), shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (action.type) {
                    "delete_profile" -> ProtonIcons.Trash
                    "refresh_servers" -> ProtonIcons.ArrowsRotate
                    "set_obfuscation", "set_awg_params" -> ProtonIcons.Shield
                    else -> ProtonIcons.Sliders
                },
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(action.title, color = colors.textNorm, fontWeight = FontWeight.SemiBold)
                Text(action.description, style = MaterialTheme.typography.bodySmall, color = colors.textWeak)
            }
        }
    }
}
