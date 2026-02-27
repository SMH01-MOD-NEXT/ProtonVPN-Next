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

package ru.protonmod.next.ui.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import ru.protonmod.next.BuildConfig
import ru.protonmod.next.ui.screens.countries.CountriesScreen
import ru.protonmod.next.ui.screens.dashboard.DashboardScreen
import ru.protonmod.next.ui.screens.map.MapScreen
import ru.protonmod.next.ui.screens.settings.*

// TODO: Add future screens here (Profiles, etc.)
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Map : Screen("map")
    data object Settings : Screen("settings")
    data object Profiles : Screen("profiles")
    data object Countries : Screen("countries")

    // Split Tunneling Screens
    data object SplitTunnelingMain : Screen("split_tunneling_main")
    data object SplitTunnelingApps : Screen("split_tunneling_apps")
    data object SplitTunnelingIps : Screen("split_tunneling_ips")

    // Connection Screens
    data object ObfuscationSettings : Screen("obfuscation_settings")

    data object AboutApp : Screen("about_app")
    data object Licenses : Screen("licenses")
}

// Enum representing the bottom navigation targets, matching Proton Next style
enum class MainTarget {
    Home, Countries, Profiles, Settings
}

fun NavGraphBuilder.appNavGraph(
    navController: NavHostController,
) {
    composable(Screen.Home.route) {
        DashboardScreen(
            onNavigateToMap = { navController.navigate(Screen.Map.route) },
            onNavigateToCountries = { navController.navigate(Screen.Countries.route) },
            onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
            onNavigateToProfiles = { navController.navigate(Screen.Profiles.route) }
        )
    }

    composable(Screen.Map.route) {
        MapScreen(
            onBack = { navController.popBackStack() }
        )
    }

    composable(Screen.Settings.route) {
        SettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToHome = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Home.route) { inclusive = false }
                    launchSingleTop = true
                }
            },
            onNavigateToCountries = {
                navController.navigate(Screen.Countries.route) {
                    popUpTo(Screen.Home.route)
                    launchSingleTop = true
                }
            },
            onNavigateToProfiles = {
                navController.navigate(Screen.Profiles.route) {
                    popUpTo(Screen.Home.route)
                    launchSingleTop = true
                }
            },
            onNavigateToSplitTunnelingMain = {
                navController.navigate(Screen.SplitTunnelingMain.route)
            },
            onNavigateToObfuscation = {
                navController.navigate(Screen.ObfuscationSettings.route)
            },
            onNavigateToAbout = {
                navController.navigate(Screen.AboutApp.route)
            }
        )
    }

    composable(Screen.ObfuscationSettings.route) {
        ObfuscationSettingsScreen(
            onBack = { navController.popBackStack() }
        )
    }

    composable(Screen.AboutApp.route) {
        AboutAppScreen(
            onBack = { navController.popBackStack() },
            appVersion = BuildConfig.VERSION_NAME,
            onNavigateToLicenses = { navController.navigate(Screen.Licenses.route) }
        )
    }

    composable(Screen.Licenses.route) {
        LicensesScreen(
            onBack = { navController.popBackStack() }
        )
    }

    // Main Split Tunneling Hub
    composable(Screen.SplitTunnelingMain.route) {
        SplitTunnelingMainScreen(
            onBack = { navController.popBackStack() },
            onNavigateToApps = { navController.navigate(Screen.SplitTunnelingApps.route) },
            onNavigateToIps = { navController.navigate(Screen.SplitTunnelingIps.route) }
        )
    }

    // Specific Apps and IPs screens
    composable(Screen.SplitTunnelingApps.route) {
        SplitTunnelingAppsScreen(
            onBack = { navController.popBackStack() }
        )
    }

    composable(Screen.SplitTunnelingIps.route) {
        SplitTunnelingIpsScreen(
            onBack = { navController.popBackStack() }
        )
    }

    composable(Screen.Countries.route) {
        CountriesScreen(
            onNavigateToHome = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Home.route) { inclusive = false }
                    launchSingleTop = true
                }
            },
            onNavigateToSettings = {
                navController.navigate(Screen.Settings.route) {
                    popUpTo(Screen.Home.route)
                    launchSingleTop = true
                }
            },
            onNavigateToProfiles = {
                navController.navigate(Screen.Profiles.route) {
                    popUpTo(Screen.Home.route)
                    launchSingleTop = true
                }
            },
            onBack = { navController.popBackStack() }
        )
    }

    // TODO: Implement ProfilesScreen
    composable(Screen.Profiles.route) {
        // ProfilesScreen()
    }
}
