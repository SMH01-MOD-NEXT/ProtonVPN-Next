package ru.protonmod.next

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import ru.protonmod.next.ui.screens.LoginScreen
import ru.protonmod.next.ui.screens.WelcomeScreen
import ru.protonmod.next.ui.screens.dashboard.DashboardScreen
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
                _startDestination.value = "dashboard"
            } else {
                _startDestination.value = "welcome"
            }
        }
    }
}

// --- Main Activity ---

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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
                    ProtonNextAppNavHost()
                }
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
                    // Clear the entire backstack and navigate to dashboard
                    navController.navigate("dashboard") {
                        popUpTo(0)
                    }
                }
            )
        }

        composable("dashboard") {
            // No need to pass tokens here anymore, ViewModel gets them from Room
            DashboardScreen(
                onNavigateToSettings = { /* TODO: Create Settings Screen */ }
            )
        }
    }
}