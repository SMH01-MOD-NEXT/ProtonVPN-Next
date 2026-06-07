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

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import ru.protonmod.next.data.local.SetupStep
import ru.protonmod.next.ui.theme.ProtonNextTheme

/**
 * A fluid, animated background inspired by Material 3 Expressive and Google Pixel Setup Wizard.
 * It features transparent morphing shapes with white outlines.
 */
@Composable
fun ExpressiveBackground(
    modifier: Modifier = Modifier,
    alpha: Float = 0.8f,
    step: SetupStep = SetupStep.WELCOME
) {
    val colors = ProtonNextTheme.colors

    // Only run infinite animations when the lifecycle is RESUMED. This prevents
    // OnDrawListener accumulation in ViewTreeObserver during background/foreground
    // transitions on Android 16, which causes IndexOutOfBoundsException in dispatchOnDraw.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val isResumed by remember {
        derivedStateOf { lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
    }

    // Pass isRunning=isResumed so the infinite transition stops registering draw callbacks
    // when the app is backgrounded or the activity is not yet fully resumed.
    val infiniteTransition = rememberInfiniteTransition(label = "expressive_bg", isRunning = isResumed)

    // --- Dynamic Background Positions based on Step ---
    val blob1TargetPosition = remember(step) {
        when (step) {
            SetupStep.WELCOME -> Offset(0.8f, 0.2f)
            SetupStep.LOGIN_EMAIL, SetupStep.LOGIN_PASSWORD, SetupStep.LOGIN_2FA, SetupStep.CAPTCHA -> Offset(0.2f, 0.15f)
            SetupStep.LOADING -> Offset(0.5f, 0.3f)
            SetupStep.CONFIG_PORT -> Offset(0.9f, 0.1f)
            SetupStep.CONFIG_OBFUSCATION -> Offset(0.1f, 0.4f)
            SetupStep.CONFIG_SERVER_LOAD -> Offset(0.8f, 0.5f)
            SetupStep.CONFIG_THEME -> Offset(0.2f, 0.8f)
            SetupStep.COMPLETE -> Offset(0.5f, 0.5f)
        }
    }

    val blob2TargetPosition = remember(step) {
        when (step) {
            SetupStep.WELCOME -> Offset(0.1f, 0.8f)
            SetupStep.LOGIN_EMAIL, SetupStep.LOGIN_PASSWORD, SetupStep.LOGIN_2FA, SetupStep.CAPTCHA -> Offset(0.85f, 0.75f)
            SetupStep.LOADING -> Offset(0.5f, 0.7f)
            SetupStep.CONFIG_PORT -> Offset(0.1f, 0.9f)
            SetupStep.CONFIG_OBFUSCATION -> Offset(0.9f, 0.6f)
            SetupStep.CONFIG_SERVER_LOAD -> Offset(0.2f, 0.4f)
            SetupStep.CONFIG_THEME -> Offset(0.8f, 0.2f)
            SetupStep.COMPLETE -> Offset(0.5f, 0.5f)
        }
    }

    val blob1Pos by animateOffsetAsState(
        targetValue = blob1TargetPosition,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "blob1_pos"
    )

    val blob2Pos by animateOffsetAsState(
        targetValue = blob2TargetPosition,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "blob2_pos"
    )

    val blobScale by animateFloatAsState(
        targetValue = if (step == SetupStep.LOADING || step == SetupStep.COMPLETE) 1.5f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
        label = "blob_scale"
    )

    // Morph Progress Animation
    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "morph"
    )

    // Slow rotation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(60000, easing = LinearEasing)),
        label = "global_rot"
    )

    // Define Shapes for Morphing (More organic/bloby)
    val blob1 = remember {
        RoundedPolygon.star(
            numVerticesPerRadius = 12,
            innerRadius = 0.8f,
            rounding = CornerRounding(0.5f)
        )
    }
    val blob2 = remember {
        RoundedPolygon.circle(numVertices = 12)
    }
    val morph = remember { Morph(blob1, blob2) }

    Box(modifier = modifier.fillMaxSize()) {
        // Deep Background Glows (Purple/Brand)
        Canvas(modifier = Modifier.fillMaxSize().blur(120.dp)) {
            val baseRadius = size.minDimension * 0.7f * blobScale
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.brandNorm.copy(alpha = 0.25f), Color.Transparent),
                    center = Offset(size.width * blob1Pos.x, size.height * blob1Pos.y),
                    radius = baseRadius
                )
            )
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.brandNorm.copy(alpha = 0.2f), Color.Transparent),
                    center = Offset(size.width * blob2Pos.x, size.height * blob2Pos.y),
                    radius = baseRadius * 1.2f
                )
            )
        }

        // Outlined Morphing Shapes (Moving slowly)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha)
                .blur(4.dp)
        ) {
            val width = size.width
            val height = size.height
            val baseSize = size.minDimension * 0.9f * blobScale

            withTransform({
                translate(width * blob1Pos.x, height * blob1Pos.y)
                rotate(rotation)
                scale(baseSize, baseSize)
            }) {
                val path = morph.toPath(morphProgress).asComposePath()
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.15f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            
            withTransform({
                translate(width * blob2Pos.x, height * blob2Pos.y)
                rotate(-rotation * 1.2f)
                scale(baseSize * 1.1f, baseSize * 1.1f)
            }) {
                val path = morph.toPath(1f - morphProgress).asComposePath()
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.1f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}
