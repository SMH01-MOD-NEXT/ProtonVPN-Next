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

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.protonmod.next.BuildConfig
import ru.protonmod.next.MainViewModel
import ru.protonmod.next.ui.screens.dashboard.DashboardScreen
import ru.protonmod.next.ui.screens.WelcomeScreen
import ru.protonmod.next.ui.screens.countries.CountriesScreen
import ru.protonmod.next.ui.screens.LoginViewModel
import ru.protonmod.next.ui.screens.LoginUiState
import ru.protonmod.next.ui.screens.profiles.*
import ru.protonmod.next.ui.screens.settings.*
import ru.protonmod.next.ui.screens.netshield.NetShieldSettingsScreen
import ru.protonmod.next.ui.screens.ai.AiSettingsScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object ApiBypass : Screen("api_bypass")
    data object CountrySpoofing : Screen("country_spoofing")
    data object ByeDpiTest : Screen("byedpi_test")
    data object Settings : Screen("settings")
    data object AiSettings : Screen("ai_settings")
    data object NetShield : Screen("netshield")
    data object ConnectionVerification : Screen("connection_verification")
    data object Profiles : Screen("profiles")
    data object EditProfile : Screen("edit_profile?profileId={profileId}") {
        fun createRoute(profileId: String? = null) = if (profileId != null) "edit_profile?profileId=$profileId" else "edit_profile"
    }
    data object Countries : Screen("countries")

    // Split Tunneling Screens
    data object SplitTunnelingMain : Screen("split_tunneling_main")
    data object SplitTunnelingApps : Screen("split_tunneling_apps")
    data object SplitTunnelingIps : Screen("split_tunneling_ips")
    data object SplitTunnelingDomains : Screen("split_tunneling_domains")

    // Connection Screens
    data object Protocol : Screen("protocol")
    data object ObfuscationSettings : Screen("obfuscation_settings")
    data object KillSwitch : Screen("kill_switch")
    data object ErrorReporting : Screen("error_reporting")
    data object CertSettings : Screen("cert_settings")

    data object ThemeSelection : Screen("theme_selection")
    data object LoadDisplayModeSelection : Screen("load_display_mode_selection")
    data object DebugSettings : Screen("debug_settings")
    data object BackupSettings : Screen("backup_settings")

    data object CustomDns : Screen("custom_dns")
    data object PortSelection : Screen("port_selection?currentPort={currentPort}&isGlobal={isGlobal}") {
        fun createRoute(currentPort: Int, isGlobal: Boolean) = "port_selection?currentPort=$currentPort&isGlobal=$isGlobal"
    }
    data object ProtocolSelection : Screen("protocol_selection?currentProtocol={currentProtocol}") {
        fun createRoute(currentProtocol: String) = "protocol_selection?currentProtocol=$currentProtocol"
    }
    data object AutoOpenUrl : Screen("auto_open_url?currentUrl={currentUrl}") {
        fun createRoute(currentUrl: String) = "auto_open_url?currentUrl=$currentUrl"
    }

    data object AboutApp : Screen("about_app")
    data object Licenses : Screen("licenses")
    data object PrivacyPolicy : Screen("privacy_policy")
    data object PolicyAcceptance : Screen("policy_acceptance")
}

// Enum representing the bottom navigation targets, matching Proton Next style
enum class MainTarget {
    Home, Countries, Profiles, Settings
}

fun NavGraphBuilder.appNavGraph(
    navController: NavHostController,
) {
    composable(Screen.Home.route) {
        DashboardScreen()
    }

    composable(Screen.Settings.route) {
        SettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToSplitTunnelingMain = {
                navController.navigate(Screen.SplitTunnelingMain.route)
            },
            onNavigateToProtocol = {
                navController.navigate(Screen.Protocol.route)
            },
            onNavigateToKillSwitch = {
                navController.navigate(Screen.KillSwitch.route)
            },
            onNavigateToAbout = {
                navController.navigate(Screen.AboutApp.route)
            },
            onNavigateToErrorReporting = {
                navController.navigate(Screen.ErrorReporting.route)
            },
            onNavigateToApiBypass = {
                navController.navigate(Screen.ApiBypass.route)
            },
            onNavigateToThemeSelection = {
                navController.navigate(Screen.ThemeSelection.route)
            },
            onNavigateToLoadDisplayMode = {
                navController.navigate(Screen.LoadDisplayModeSelection.route)
            },
            onNavigateToDebug = {
                navController.navigate(Screen.DebugSettings.route)
            },
            onNavigateToBackup = {
                navController.navigate(Screen.BackupSettings.route)
            },
            onNavigateToCustomDns = {
                navController.navigate(Screen.CustomDns.route)
            },
            onNavigateToCountrySpoofing = {
                navController.navigate(Screen.CountrySpoofing.route)
            },
            onNavigateToPortSelection = { currentPort ->
                navController.navigate(Screen.PortSelection.createRoute(currentPort, true))
            },
            onNavigateToCertSettings = {
                navController.navigate(Screen.CertSettings.route)
            },
            onNavigateToNetShield = {
                navController.navigate(Screen.NetShield.route)
            },
            onNavigateToAiSettings = {
                navController.navigate(Screen.AiSettings.route)
            },
            onNavigateToConnectionVerification = {
                navController.navigate(Screen.ConnectionVerification.route)
            }
        )
    }

    composable(Screen.NetShield.route) {
        NetShieldSettingsScreen(onBack = { navController.popBackStack() })
    }

    composable(Screen.AiSettings.route) {
        AiSettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToApiBypass = { navController.navigate(Screen.ApiBypass.route) }
        )
    }

    composable(Screen.ConnectionVerification.route) {
        ConnectionVerificationSettingsScreen(onBack = { navController.popBackStack() })
    }

    composable(Screen.CertSettings.route) {
        CertSettingsScreen(
            onBack = { navController.popBackStack() }
        )
    }

    composable(Screen.ApiBypass.route) {
        ApiBypassScreen(
            onBack = { navController.popBackStack() },
            onNavigateToByeDpiTest = { navController.navigate(Screen.ByeDpiTest.route) }
        )
    }

    composable(Screen.ByeDpiTest.route) {
        ByeDpiTestScreen(
            onBack = { navController.popBackStack() }
        )
    }

    composable(Screen.CountrySpoofing.route) {
        CountrySpoofingScreen(
            onBack = { navController.popBackStack() }
        )
    }

    composable(Screen.Protocol.route) {
        ProtocolScreen(
            onBack = { navController.popBackStack() },
            onNavigateToObfuscation = { navController.navigate(Screen.ObfuscationSettings.route) }
        )
    }

    composable(Screen.ObfuscationSettings.route) {
        ObfuscationSettingsScreen(
            onBack = { navController.popBackStack() }
        )
    }

    composable(Screen.KillSwitch.route) {
        KillSwitchScreen(
            onBack = { navController.popBackStack() }
        )
    }

    composable(Screen.ErrorReporting.route) {
        ErrorReportingScreen(
            onBack = { navController.popBackStack() }
        )
    }

    composable(Screen.ThemeSelection.route) {
        ThemeSelectionScreen(
            onBack = { navController.popBackStack() }
        )
    }

    composable(Screen.LoadDisplayModeSelection.route) {
        ServerLoadDisplayModeScreen(
            onBack = { navController.popBackStack() }
        )
    }

    composable(Screen.DebugSettings.route) {
        DebugSettingsScreen(
            onBack = { navController.popBackStack() }
        )
    }

    composable(Screen.BackupSettings.route) {
        BackupScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(Screen.AboutApp.route) {
        AboutAppScreen(
            onBack = { navController.popBackStack() },
            appVersion = BuildConfig.VERSION_NAME,
            onNavigateToLicenses = { navController.navigate(Screen.Licenses.route) },
            onNavigateToPrivacyPolicy = { navController.navigate(Screen.PrivacyPolicy.route) }
        )
    }

    composable(Screen.Licenses.route) {
        LicensesScreen(
            onBack = { navController.popBackStack() }
        )
    }

    composable(Screen.PrivacyPolicy.route) {
        PrivacyPolicyScreen(
            onBack = { navController.popBackStack() }
        )
    }

    composable(Screen.CustomDns.route) {
        DnsSettingsScreen(
            onBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Screen.PortSelection.route,
        arguments = listOf(
            navArgument("currentPort") { type = NavType.IntType },
            navArgument("isGlobal") { type = NavType.BoolType }
        )
    ) { backStackEntry ->
        val currentPort = backStackEntry.arguments?.getInt("currentPort") ?: 0
        val isGlobal = backStackEntry.arguments?.getBoolean("isGlobal") ?: false
        val viewModel: SettingsViewModel = hiltViewModel()

        PortSelectionScreen(
            currentPort = currentPort,
            onBack = { navController.popBackStack() },
            onPortSelect = { port ->
                if (isGlobal) {
                    viewModel.setVpnPort(port)
                } else {
                    // For profile editing, we send the result back
                    navController.previousBackStackEntry?.savedStateHandle?.set("selectedPort", port)
                }
                navController.popBackStack()
            }
        )
    }

    composable(
        route = Screen.ProtocolSelection.route,
        arguments = listOf(navArgument("currentProtocol") { type = NavType.StringType })
    ) { backStackEntry ->
        val currentProtocol = backStackEntry.arguments?.getString("currentProtocol") ?: "AmneziaWG"
        
        ProtocolSelectionScreen(
            currentProtocol = currentProtocol,
            onBack = { navController.popBackStack() },
            onProtocolSelect = { protocol ->
                navController.previousBackStackEntry?.savedStateHandle?.set("selectedProtocol", protocol)
                navController.popBackStack()
            }
        )
    }

    composable(
        route = Screen.AutoOpenUrl.route,
        arguments = listOf(navArgument("currentUrl") { type = NavType.StringType })
    ) { backStackEntry ->
        val currentUrl = backStackEntry.arguments?.getString("currentUrl") ?: ""
        
        AutoOpenUrlScreen(
            currentUrl = currentUrl,
            onBack = { navController.popBackStack() },
            onUrlSave = { url ->
                navController.previousBackStackEntry?.savedStateHandle?.set("selectedUrl", url)
                navController.popBackStack()
            }
        )
    }

    // Main Split Tunneling Hub
    composable(Screen.SplitTunnelingMain.route) {
        SplitTunnelingMainScreen(
            onBack = { navController.popBackStack() },
            onNavigateToApps = { navController.navigate(Screen.SplitTunnelingApps.route) },
            onNavigateToIps = { navController.navigate(Screen.SplitTunnelingIps.route) },
            onNavigateToDomains = { navController.navigate(Screen.SplitTunnelingDomains.route) }
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

    composable(Screen.SplitTunnelingDomains.route) {
        SplitTunnelingDomainsScreen(
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
            onBack = { navController.popBackStack() }
        )
    }

    composable(Screen.Profiles.route) {
        // Keep one activity-scoped instance for the list and editor so the
        // selected profile is already in memory on the editor's first frame.
        val profilesViewModel: ProfilesViewModel = hiltViewModel(LocalContext.current as ComponentActivity)
        ProfilesScreen(
            viewModel = profilesViewModel,
            onNavigateToHome = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Home.route) { inclusive = false }
                    launchSingleTop = true
                }
            },
            onCreateNewProfile = {
                navController.navigate(Screen.EditProfile.createRoute())
            },
            onEditProfile = { profileId ->
                navController.navigate(Screen.EditProfile.createRoute(profileId))
            }
        )
    }

    composable(
        route = Screen.EditProfile.route,
        arguments = listOf(navArgument("profileId") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        })
    ) { backStackEntry ->
        val profileId = backStackEntry.arguments?.getString("profileId")
        val profilesViewModel: ProfilesViewModel = hiltViewModel(LocalContext.current as ComponentActivity)
        EditProfileScreen(
            profileId = profileId,
            viewModel = profilesViewModel,
            onNavigateToPortSelection = { port ->
                navController.navigate(Screen.PortSelection.createRoute(port, false))
            },
            onNavigateToProtocolSelection = { protocol ->
                navController.navigate(Screen.ProtocolSelection.createRoute(protocol))
            },
            onNavigateToUrlSelection = { url ->
                navController.navigate(Screen.AutoOpenUrl.createRoute(url))
            },
            navController = navController,
            onNavigateBack = { navController.popBackStack() }
        )
    }
}
