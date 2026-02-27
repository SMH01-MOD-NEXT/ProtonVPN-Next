package ru.protonmod.next.ui.screens.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.ui.theme.ProtonNextTheme

/**
 * Maps country codes to relative X, Y coordinates on a standard equirectangular/Mercator world map.
 * X and Y are values between 0.0f (top/left) and 1.0f (bottom/right).
 * Add more countries here as needed.
 */
object MapCoordinates {
    fun get(countryCode: String?): Pair<Float, Float> {
        return when (countryCode?.uppercase()) {
            "US" -> 0.22f to 0.40f
            "CA" -> 0.25f to 0.25f
            "BR" -> 0.32f to 0.65f
            "UK", "GB" -> 0.47f to 0.30f
            "NL" -> 0.50f to 0.30f
            "DE" -> 0.51f to 0.31f
            "CH" -> 0.51f to 0.33f
            "FR" -> 0.49f to 0.33f
            "RU" -> 0.65f to 0.25f
            "JP" -> 0.85f to 0.38f
            "AU" -> 0.85f to 0.80f
            "SG" -> 0.78f to 0.60f
            "ZA" -> 0.55f to 0.75f
            else -> 0.5f to 0.5f // Default to center if unknown
        }
    }
}

@Composable
fun HomeMap(
    modifier: Modifier = Modifier,
    connectedServer: LogicalServer?,
    isConnecting: Boolean
) {
    val colors = ProtonNextTheme.colors
    // Determine the state of the connection to set the appropriate colors
    val isConnected = connectedServer != null && !isConnecting

    val pinColor by animateColorAsState(
        targetValue = when {
            isConnected -> colors.notificationSuccess // VPN Green
            isConnecting -> colors.brandNorm // Connecting color
            else -> Color.Transparent // Hidden when disconnected
        },
        animationSpec = tween(durationMillis = 500),
        label = "pinColor"
    )

    // Infinite transition for the pulsing effect of the outer ring
    val infiniteTransition = rememberInfiniteTransition(label = "pinPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    // Get coordinates for the current server
    val (relX, relY) = MapCoordinates.get(connectedServer?.exitCountry)

    // Animated coordinates to smoothly move the pin when switching servers
    val animatedX by animateFloatAsState(targetValue = relX, animationSpec = spring(), label = "animX")
    val animatedY by animateFloatAsState(targetValue = relY, animationSpec = spring(), label = "animY")

    Box(
        modifier = modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    colors.brandNorm.copy(alpha = 0.2f), // Subtle brand color glow
                    colors.backgroundNorm
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        // TODO: For a complete look, set a world map vector drawable here.
        // Image(
        //     painter = painterResource(id = R.drawable.ic_world_map),
        //     contentDescription = null,
        //     modifier = Modifier.fillMaxSize(),
        //     contentScale = ContentScale.Fit,
        //     alpha = 0.3f
        // )

        // Draw the pins
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (connectedServer == null) return@Canvas

            // Calculate exact pixel coordinates based on the canvas size
            val pxX = size.width * animatedX
            val pxY = size.height * animatedY
            val center = Offset(pxX, pxY)

            val baseRadius = 6.dp.toPx()

            // Draw pulsing outer ring
            if (isConnecting || isConnected) {
                drawCircle(
                    color = pinColor.copy(alpha = pulseAlpha),
                    radius = baseRadius * pulseScale,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Draw solid inner pin
            drawCircle(
                color = pinColor,
                radius = baseRadius,
                center = center
            )

            // Draw a tiny white dot in the middle for style
            drawCircle(
                color = Color.White,
                radius = baseRadius * 0.4f,
                center = center
            )
        }
    }
}
