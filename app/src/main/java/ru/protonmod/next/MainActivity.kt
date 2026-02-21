package ru.protonmod.next

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.protonmod.next.ui.screens.LoginScreen
import ru.protonmod.next.ui.screens.WelcomeScreen
import ru.protonmod.next.ui.theme.ProtonNextTheme
import dagger.hilt.android.AndroidEntryPoint

// Make sure to add @HiltAndroidApp to your Application class later!
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
                    ProtonNextNavigation()
                }
            }
        }
    }
}

/**
 * Defines the navigation graph for the application.
 * Currently supports routing between Welcome and Login screens.
 */
@Composable
fun ProtonNextNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "welcome"
    ) {
        composable("welcome") {
            WelcomeScreen(
                onNavigateToLogin = {
                    navController.navigate("login")
                },
                onNavigateToRegister = {
                    // TODO: Navigate to Registration flow
                }
            )
        }

        composable("login") {
            LoginScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onLoginSuccess = {
                    // TODO: Clear backstack and navigate to main VPN dashboard
                    // navController.navigate("dashboard") { popUpTo(0) }
                }
            )
        }
    }
}