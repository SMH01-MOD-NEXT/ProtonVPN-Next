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
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.ui.nav.Screen
import ru.protonmod.next.ui.nav.appNavGraph
import ru.protonmod.next.ui.screens.LoginScreen
import ru.protonmod.next.ui.screens.WelcomeScreen
import ru.protonmod.next.ui.theme.ProtonNextTheme
import javax.inject.Inject

// --- Main ViewModel for Auth State Routing ---

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sessionDao: SessionDao
) : ViewModel() {
    private val _startDestination = MutableStateFlow<String>("")
    val startDestination: StateFlow<String> = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            // Check for active session in Room DB
            val session = sessionDao.getSession()
            if (session != null && session.accessToken.isNotEmpty()) {
                _startDestination.value = Screen.Home.route
            } else {
                _startDestination.value = "welcome"
            }
        }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable Edge-To-Edge for immersive UI experience
        enableEdgeToEdge()

        setContent {
            ProtonNextTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LaunchedEffect(Unit) {
                        checkAndRequestNotificationPermission()
                    }
                    ProtonNextAppNavHost()
                }
            }
        }
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
fun ProtonNextAppNavHost(viewModel: MainViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val startDestination by viewModel.startDestination.collectAsState()

    // Show blank screen (or splash) until we know where to route
    if (startDestination.isEmpty()) return

    NavHost(navController = navController, startDestination = startDestination) {
        composable("welcome") {
            WelcomeScreen(
                onNavigateToLogin = { navController.navigate("login") },
                onNavigateToRegister = { /* TODO: Registration flow */ }
            )
        }

        composable("login") {
            LoginScreen(
                onBackClick = { navController.popBackStack() },
                onLoginSuccess = {
                    // Clear the entire backstack and navigate to home (dashboard)
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0)
                    }
                }
            )
        }

        // Add all app navigation routes
        appNavGraph(navController = navController)
    }
}
