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

package ru.protonmod.next

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.ota.OTAUpdateScreen
import ru.protonmod.next.ui.components.LiquidGlassBottomBar
import ru.protonmod.next.ui.components.ReconnectRequiredDialog
import ru.protonmod.next.ui.nav.MainTarget
import ru.protonmod.next.ui.nav.Screen
import ru.protonmod.next.ui.nav.appNavGraph
import ru.protonmod.next.ui.screens.WelcomeScreen
import ru.protonmod.next.ui.screens.settings.PolicyAcceptanceScreen
import ru.protonmod.next.ui.screens.ai.AiOverlay
import ru.protonmod.next.ui.theme.AppTheme
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.utils.ProvideDeviceType
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val viewModel: MainViewModel = hiltViewModel()
            val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
            val context = LocalContext.current

            ProtonNextTheme(appTheme = appTheme) {
                ProvideDeviceType(
                    windowWidthSizeClass = windowSizeClass.widthSizeClass
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ProtonNextTheme.colors.backgroundNorm)
                    ) {
                        LaunchedEffect(Unit) {
                            checkAndRequestNotificationPermission()
                        }
                        
                        val launchPrefs = remember { context.getSharedPreferences("next_launch_prefs", android.content.Context.MODE_PRIVATE) }
                        val launchCount = remember { launchPrefs.getInt("launch_count", 0) }

                        LaunchedEffect(Unit) {
                            launchPrefs.edit().putInt("launch_count", launchCount + 1).apply()
                        }

                        ProtonNextAppNavHost(
                            viewModel = viewModel,
                            onNavControllerReady = {}
                        )
                        
                        AiOverlay()

                        val otaViewModel: ru.protonmod.next.ota.OTAUpdateViewModel = hiltViewModel()
                        OTAUpdateOverlay(viewModel = otaViewModel)

                        val reconnectPrompt by viewModel.reconnectPrompt.collectAsStateWithLifecycle()
                        if (reconnectPrompt.isVisible) {
                            ReconnectRequiredDialog(
                                canReconnect = reconnectPrompt.canReconnect,
                                onPostpone = viewModel::postponeReconnect,
                                onReconnect = viewModel::reconnectNow,
                                onDisablePrompt = viewModel::disableReconnectPrompt
                            )
                        }
                    }
                }
            }
        }
    }

    // Intercept KEYCODE_CALL events to prevent the Android framework's
    // PhoneFallbackEventHandler from broadcasting ACTION_CLOSE_SYSTEM_DIALOGS,
    // which requires a signature-level permission on Android 12+ (API 31+) and
    // causes a SecurityException in third-party apps.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_CALL) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun OTAUpdateOverlay(
    viewModel: ru.protonmod.next.ota.OTAUpdateViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(modifier = modifier) {
        LaunchedEffect(viewModel) {
            viewModel.checkForUpdates()
        }

        if (uiState.updateInfo != null) {
            OTAUpdateScreen(
                uiState = uiState,
                onInstall = { viewModel.installUpdate(context) },
                onDownload = { info -> viewModel.startDownload(context, info) },
                onDismiss = { viewModel.dismissUpdate() }
            )
        }
    }
}

@Composable
fun ProtonNextAppNavHost(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
    onNavControllerReady: (NavHostController) -> Unit = {}
) {
    val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()

    if (startDestination.isEmpty()) return

    Box(modifier = modifier.fillMaxSize()) {
        val currentOnNavControllerReady by rememberUpdatedState(onNavControllerReady)
        val navController = rememberNavController()

        LaunchedEffect(navController) {
            currentOnNavControllerReady(navController)
        }

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        var lastKnownTarget by remember { mutableStateOf<MainTarget?>(null) }
        val currentTarget = remember(currentRoute) {
            val target = when (currentRoute) {
                Screen.Home.route -> MainTarget.Home
                Screen.Countries.route -> MainTarget.Countries
                Screen.Profiles.route -> MainTarget.Profiles
                Screen.Settings.route -> MainTarget.Settings
                else -> null
            }
            if (target != null) {
                lastKnownTarget = target
            }
            // If current route is null (transitioning), use last known target to avoid jump
            target ?: if (currentRoute == null) lastKnownTarget else null
        }

        // Track previous session state to detect real logouts
        var previousSessionWasNotNull by remember { mutableStateOf(session != null) }

        LaunchedEffect(session) {
            val isLoggingOut = previousSessionWasNotNull && session == null
            previousSessionWasNotNull = session != null

            if (isLoggingOut && startDestination.isNotEmpty() && startDestination != Screen.PolicyAcceptance.route) {
                ru.protonmod.next.utils.ProtonLogger.d("MainActivity", "User logged out, navigating to welcome. Current route: $currentRoute")
                // Only navigate if we're not already on a public screen
                if (currentRoute != "welcome" && currentRoute != "login" && currentRoute != Screen.ApiBypass.route) {
                    navController.navigate("welcome") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }

        LaunchedEffect(startDestination) {
            // Only navigate if startDestination actually changed after the NavHost was already initialized.
            // When NavHost is first created, it handles startDestination itself.
            // Guard strictly against self-navigation (currentRoute == startDestination) which can occur
            // after a low-memory background/foreground cycle causing the activity to restart and the
            // startDestination StateFlow to re-emit the same value. Self-navigation to /welcome
            // triggers duplicate OnDrawListener registrations in ViewTreeObserver on Android 16,
            // leading to IndexOutOfBoundsException in dispatchOnDraw.
            if (startDestination.isNotEmpty() && currentRoute != null && currentRoute != startDestination) {
                ru.protonmod.next.utils.ProtonLogger.d("MainActivity", "startDestination changed to: $startDestination, navigating. Current route: $currentRoute")
                navController.navigate(startDestination) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { fadeIn(animationSpec = tween(220)) },
            exitTransition = { fadeOut(animationSpec = tween(180)) },
            popEnterTransition = { fadeIn(animationSpec = tween(220)) },
            popExitTransition = { fadeOut(animationSpec = tween(180)) }
        ) {
            composable(Screen.PolicyAcceptance.route) {
                PolicyAcceptanceScreen(
                    onAccept = { viewModel.acceptPolicy() }
                )
            }

            composable("welcome") {
                WelcomeScreen(
                    onNavigateToHome = {
                        // Clear the entire backstack and navigate to home (dashboard)
                        navController.navigate(Screen.Home.route) {
                            popUpTo(0)
                        }
                    },
                    onNavigateToRegister = { /* TODO: Registration flow */ },
                    onNavigateToPrivacyPolicy = {
                        navController.navigate(Screen.PrivacyPolicy.route)
                    },
                    onNavigateToApiBypass = {
                        navController.navigate(Screen.ApiBypass.route)
                    }
                )
            }

            appNavGraph(navController = navController)
        }

        AnimatedVisibility(
            visible = currentTarget != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            LiquidGlassBottomBar(
                selectedTarget = currentTarget,
                navigateTo = { target ->
                    val route = when (target) {
                        MainTarget.Home -> Screen.Home.route
                        MainTarget.Countries -> Screen.Countries.route
                        MainTarget.Profiles -> Screen.Profiles.route
                        MainTarget.Settings -> Screen.Settings.route
                    }
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            )
        }
    }
}
