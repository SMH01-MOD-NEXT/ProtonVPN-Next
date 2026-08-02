/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.protonmod.next.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.delay
import ru.protonmod.next.R
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.nav.MainTarget
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.isTablet

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiquidGlassBottomBar(
    selectedTarget: MainTarget?,
    navigateTo: (MainTarget) -> Unit,
    modifier: Modifier = Modifier,
    showCountries: Boolean = true,
    showGateways: Boolean = true,
    notificationDots: ImmutableSet<MainTarget> = persistentSetOf(),
    aiEnabled: Boolean = false,
    aiModeActive: Boolean = false,
    isAiProcessing: Boolean = false,
    aiStatusMessage: String? = null,
    onAiModeToggle: (Boolean) -> Unit = {},
    onAiSubmit: (String) -> Unit = {}
) {
    val isTablet = isTablet()
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val glassShape = RoundedCornerShape(32.dp)
    var aiQuery by remember { mutableStateOf("") }

    val targets = mutableListOf(MainTarget.Home)
    if (showCountries) targets.add(MainTarget.Countries)
    targets.add(MainTarget.Profiles)
    targets.add(MainTarget.Settings)

    // Gemini-style intro glow shown briefly when AI mode opens.
    var showGeminiAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(aiModeActive) {
        if (aiModeActive) {
            showGeminiAnimation = true
            delay(1200)
            showGeminiAnimation = false
        } else {
            // Closing AI mode (e.g. tapping the close button right after a long press)
            // cancels the coroutine above before its reset runs, so clear it here too;
            // otherwise the border keeps shimmering indefinitely.
            showGeminiAnimation = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = if (isTablet) 400.dp else 600.dp)
                .padding(horizontal = 24.dp)
                .geminiBorder(
                    shape = glassShape,
                    isEnabled = showGeminiAnimation || isAiProcessing
                )
                .liquidGlass(
                    shape = glassShape,
                    alpha = 0.85f,
                    shadowElevation = 15.dp
                )
        ) {
            AnimatedContent(
                targetState = aiModeActive,
                transitionSpec = {
                    (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                },
                label = "barContent"
            ) { active ->
                if (active) {
                    AiInputRow(
                        query = aiQuery,
                        onQueryChange = { aiQuery = it },
                        isProcessing = isAiProcessing,
                        statusMessage = aiStatusMessage,
                        onSubmit = {
                            onAiSubmit(it)
                            aiQuery = ""
                            keyboardController?.hide()
                        },
                        onClose = { onAiModeToggle(false) }
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(70.dp)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                                onLongClick = {
                                    if (aiEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onAiModeToggle(true)
                                    }
                                }
                            ),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        targets.forEach { target ->
                            NavigationItem(
                                target = target,
                                isSelected = target == selectedTarget,
                                hasNotification = notificationDots.contains(target),
                                onNavigate = { navigateTo(target) },
                                aiEnabled = aiEnabled,
                                onAiToggle = { onAiModeToggle(true) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiInputRow(
    query: String,
    onQueryChange: (String) -> Unit,
    isProcessing: Boolean,
    statusMessage: String?,
    onSubmit: (String) -> Unit,
    onClose: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    val displayMessage = when (statusMessage) {
        "ai_success" -> stringResource(R.string.ai_success)
        "ai_error_no_key" -> stringResource(R.string.ai_error_no_key)
        else -> statusMessage
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 82.dp)
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(colors.brandNorm.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ProtonIcons.MagicProtonWand,
                contentDescription = null,
                tint = colors.brandNorm,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.ai_assistant_label),
                style = MaterialTheme.typography.labelSmall,
                color = colors.brandNorm
            )
            Spacer(Modifier.height(2.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                if (query.isEmpty()) {
                    Text(
                        text = displayMessage ?: stringResource(R.string.ai_input_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (statusMessage) {
                            "ai_success" -> colors.notificationSuccess
                            null -> colors.textWeak
                            else -> colors.notificationError
                        },
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textNorm),
                    cursorBrush = Brush.verticalGradient(listOf(colors.brandNorm, colors.brandNorm)),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (query.isNotBlank()) onSubmit(query) })
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        FilledIconButton(
            onClick = { if (query.isNotBlank()) onSubmit(query) else onClose() },
            enabled = !isProcessing,
            modifier = Modifier.size(42.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (query.isNotBlank()) colors.brandNorm else colors.backgroundSecondary,
                contentColor = if (query.isNotBlank()) colors.textInverted else colors.iconWeak
            )
        ) {
            if (isProcessing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.brandNorm)
            } else {
                Icon(
                    imageVector = if (query.isNotBlank()) ProtonIcons.PaperPlane else ProtonIcons.Cross,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

private fun Modifier.geminiBorder(
    shape: Shape,
    isEnabled: Boolean,
    strokeWidth: Dp = 2.5.dp
): Modifier = composed {
    // Fade the glow in and out instead of snapping it on/off. The fade-out is a bit
    // slower so the border eases away gently rather than blinking off.
    val intensity by animateFloatAsState(
        targetValue = if (isEnabled) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isEnabled) 500 else 850,
            easing = FastOutSlowInEasing
        ),
        label = "geminiIntensity"
    )

    // Nothing visible: skip the infinite animation entirely so it can't keep running.
    if (intensity <= 0.001f) return@composed this

    val infiniteTransition = rememberInfiniteTransition(label = "gemini")
    // The sweep must loop seamlessly. With TileMode.Mirror the gradient pattern only
    // repeats identically every full period, which is 2x the 600px gradient vector = 1200px.
    // Wrapping the offset at anything other than a multiple of 1200 lands mid-pattern and
    // the colors visibly jump on restart. LinearEasing keeps the speed constant across the seam.
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    drawWithContent {
        drawContent()
        val colors = listOf(
            Color(0xFF4285F4),
            Color(0xFF9B72F3),
            Color(0xFF34A853),
            Color(0xFFFBBC05),
            Color(0xFFEA4335),
            Color(0xFF4285F4)
        )

        drawOutline(
            outline = shape.createOutline(size, layoutDirection, this),
            brush = Brush.linearGradient(
                colors = colors,
                start = Offset(offset, offset),
                end = Offset(offset + 600f, offset + 600f),
                tileMode = TileMode.Mirror
            ),
            style = Stroke(width = strokeWidth.toPx()),
            alpha = intensity
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NavigationItem(
    target: MainTarget,
    isSelected: Boolean,
    hasNotification: Boolean,
    onNavigate: () -> Unit,
    aiEnabled: Boolean,
    onAiToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    val haptic = LocalHapticFeedback.current
    val activeColor = colors.navigationActive
    val inactiveColor = colors.iconWeak

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else inactiveColor,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "iconColor"
    )

    val iconVector = getProtonIconForTarget(target)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onNavigate() },
                onLongClick = {
                    if (aiEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAiToggle()
                    }
                }
            )
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = iconColor.copy(alpha = 0.15f),
                        shape = CircleShape
                    )
            )
        }

        Box {
            Icon(
                imageVector = iconVector,
                contentDescription = target.name,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )

            if (hasNotification) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(colors.notificationError, CircleShape)
                        .align(Alignment.TopEnd)
                )
            }
        }
    }
}

@Composable
private fun getProtonIconForTarget(target: MainTarget): ImageVector {
    return when (target) {
        MainTarget.Home -> ProtonIcons.House
        MainTarget.Profiles -> ProtonIcons.WindowTerminal
        MainTarget.Countries -> ProtonIcons.Globe
        MainTarget.Settings -> ProtonIcons.CogWheel
    }
}
