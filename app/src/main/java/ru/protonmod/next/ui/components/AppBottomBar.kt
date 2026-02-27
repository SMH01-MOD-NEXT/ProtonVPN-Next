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

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.protonmod.next.ui.nav.Screen
import ru.protonmod.next.ui.theme.ProtonNextTheme

data class NavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
fun AppBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    val colors = ProtonNextTheme.colors
    val navItems = listOf(
        NavItem(
            screen = Screen.Home,
            label = "Home",
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
        ),
        NavItem(
            screen = Screen.Map,
            label = "Map",
            selectedIcon = Icons.Filled.Map,
            unselectedIcon = Icons.Outlined.Map,
        ),
        NavItem(
            screen = Screen.Settings,
            label = "Settings",
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings,
        ),
    )

    NavigationBar(
        containerColor = colors.backgroundNorm,
        contentColor = colors.textNorm
    ) {
        navItems.forEach { item ->
            val isSelected = currentRoute == item.screen.route
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(item.screen.route) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) {
                            item.selectedIcon
                        } else {
                            item.unselectedIcon
                        },
                        contentDescription = item.label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.brandNorm,
                    selectedTextColor = colors.brandNorm,
                    indicatorColor = colors.brandNorm.copy(alpha = 0.1f),
                    unselectedIconColor = colors.iconWeak,
                    unselectedTextColor = colors.textWeak
                )
            )
        }
    }
}
