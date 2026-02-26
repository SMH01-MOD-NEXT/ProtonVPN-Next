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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.protonmod.next.ui.nav.MainTarget

@Composable
fun LiquidGlassBottomBar(
    selectedTarget: MainTarget?,
    showCountries: Boolean = true,
    showGateways: Boolean = true,
    notificationDots: Set<MainTarget> = emptySet(),
    navigateTo: (MainTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    // Styling matching the exact visual design of Proton Next Liquid Glass
    val glassShape = RoundedCornerShape(32.dp)
    val glassBackgroundColor = Color(0xFF1C1C1E).copy(alpha = 0.75f)

    val glassBorderBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.3f),
            Color.White.copy(alpha = 0.05f)
        )
    )

    Box(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .shadow(
                elevation = 15.dp,
                shape = glassShape,
                spotColor = Color.Black.copy(alpha = 0.5f)
            )
            .clip(glassShape)
            .background(glassBackgroundColor)
            .border(
                width = 1.dp,
                brush = glassBorderBrush,
                shape = glassShape
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val targets = mutableListOf(MainTarget.Home)
            if (showCountries) targets.add(MainTarget.Countries)
            // TODO: Add a toggle in user settings to show/hide profiles if needed
            targets.add(MainTarget.Profiles)
            targets.add(MainTarget.Settings)

            targets.forEach { target ->
                val isSelected = target == selectedTarget

                // Fallback to Material colors since we removed ProtonTheme
                val activeColor = MaterialTheme.colorScheme.primary
                val inactiveColor = Color.Gray

                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) activeColor else inactiveColor,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "iconColor"
                )

                val iconVector = getMaterialIconForTarget(target)


                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { navigateTo(target) }
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

                        if (notificationDots.contains(target)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Replaced proprietary Proton drawables with Material Icons
private fun getMaterialIconForTarget(target: MainTarget): ImageVector {
    return when (target) {
        MainTarget.Home -> Icons.Rounded.Home
        MainTarget.Profiles -> Icons.Rounded.Terminal
        MainTarget.Countries -> Icons.Rounded.Public
        MainTarget.Settings -> Icons.Rounded.Settings
    }
}