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

package com.protonvpn.android.redesign.main_screen.ui.nav

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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import me.proton.core.compose.theme.ProtonTheme
import java.util.EnumSet
import me.proton.core.presentation.R as CoreR

@Composable
fun LiquidGlassBottomBar(
    selectedTarget: MainTarget?,
    showCountries: Boolean,
    showGateways: Boolean,
    notificationDots: EnumSet<MainTarget>,
    navigateTo: (MainTarget) -> Unit,
    modifier: Modifier = Modifier
) {
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
            if (showGateways) targets.add(MainTarget.Gateways)
            targets.add(MainTarget.Profiles)
            targets.add(MainTarget.Settings)

            targets.forEach { target ->
                val isSelected = target == selectedTarget

                val activeColor = ProtonTheme.colors.textAccent
                val inactiveColor = ProtonTheme.colors.textWeak

                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) activeColor else inactiveColor,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "iconColor"
                )

                val iconRes = getIconResForTarget(target, isSelected)

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
                            painter = painterResource(id = iconRes),
                            contentDescription = target.name,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )

                        if (notificationDots.contains(target)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(ProtonTheme.colors.notificationError, CircleShape)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getIconResForTarget(target: MainTarget, isSelected: Boolean): Int {
    return when (target) {
        MainTarget.Home ->
            if (isSelected) CoreR.drawable.ic_proton_house_filled else CoreR.drawable.ic_proton_house
        MainTarget.Profiles ->
            if (isSelected) R.drawable.ic_proton_window_terminal_filled else CoreR.drawable.ic_proton_window_terminal
        MainTarget.Countries ->
            if (isSelected) CoreR.drawable.ic_proton_earth_filled else CoreR.drawable.ic_proton_earth
        MainTarget.Settings ->
            if (isSelected) CoreR.drawable.ic_proton_cog_wheel_filled else CoreR.drawable.ic_proton_cog_wheel
        MainTarget.Gateways ->
            if (isSelected) CoreR.drawable.ic_proton_servers_filled else CoreR.drawable.ic_proton_servers
    }
}