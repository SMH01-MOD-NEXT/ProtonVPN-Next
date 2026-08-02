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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.protonmod.next.R
import ru.protonmod.next.data.ai.AiProposal
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.isTablet

@Composable
fun AiProposalPanel(
    proposal: AiProposal?,
    isProcessing: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onRefine: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = proposal != null,
        enter = slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut(),
        modifier = modifier,
    ) {
        proposal?.let {
            ProposalContent(it, isProcessing, onApply, onDismiss, onRefine)
        }
    }
}

@Composable
private fun ProposalContent(
    proposal: AiProposal,
    isProcessing: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onRefine: (String) -> Unit,
) {
    val colors = ProtonNextTheme.colors
    val tablet = isTablet()
    var refinement by remember(proposal.id) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(if (tablet) 0.48f else 0.55f)
            .widthIn(max = if (tablet) 720.dp else 640.dp)
            .padding(horizontal = 16.dp)
            .liquidGlass(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                alpha = 0.94f,
                shadowElevation = 16.dp,
            )
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(colors.brandNorm.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = colors.brandNorm, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    proposal.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textNorm,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    proposal.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textWeak,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDismiss, enabled = !isProcessing) {
                Icon(Icons.Rounded.Close, stringResource(R.string.ai_proposal_close), tint = colors.iconWeak)
            }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(proposal.actions, key = { it.id }) { action ->
                val accent = if (action.destructive) colors.notificationError else colors.brandNorm
                Surface(
                    color = colors.backgroundSecondary.copy(alpha = 0.58f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = when (action.type) {
                                "create_profile" -> Icons.Rounded.AddCircle
                                "update_profile" -> Icons.Rounded.Edit
                                "delete_profile" -> Icons.Rounded.Delete
                                "refresh_servers" -> Icons.Rounded.Sync
                                "set_obfuscation", "set_awg_params" -> Icons.Rounded.Security
                                else -> Icons.Rounded.Tune
                            },
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(action.title, color = colors.textNorm, fontWeight = FontWeight.SemiBold)
                            Text(
                                action.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textWeak,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Surface(
            color = colors.backgroundSecondary.copy(alpha = 0.46f),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.separatorNorm.copy(alpha = 0.55f)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = refinement,
                    onValueChange = { refinement = it },
                    placeholder = { Text(stringResource(R.string.ai_refine_hint), color = colors.textWeak) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !isProcessing,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (refinement.isNotBlank()) {
                            onRefine(refinement)
                            refinement = ""
                        }
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedTextColor = colors.textNorm,
                        unfocusedTextColor = colors.textNorm,
                        cursorColor = colors.brandNorm,
                    ),
                )
                IconButton(
                    onClick = {
                        onRefine(refinement)
                        refinement = ""
                    },
                    enabled = refinement.isNotBlank() && !isProcessing,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.ai_refine_action), tint = colors.brandNorm)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

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
                modifier = Modifier.weight(1.35f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm),
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.textInverted)
                } else {
                    Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ai_proposal_apply))
                }
            }
        }
    }
}
