package com.rrimal.notetaker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rrimal.notetaker.data.auth.AuthManager
import com.rrimal.notetaker.data.storage.StorageConfigManager
import com.rrimal.notetaker.ui.screens.AuthScreen
import com.rrimal.notetaker.ui.screens.BrowseScreen
import com.rrimal.notetaker.ui.screens.MainScreen
import com.rrimal.notetaker.ui.screens.OnboardingScreen
import com.rrimal.notetaker.ui.screens.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable object OnboardingRoute
@Serializable object AuthRoute
@Serializable object MainRoute  // Swipeable main screen (Dictation | Agenda | Inbox)
@Serializable object SettingsRoute
@Serializable object BrowseRoute

@Composable
fun AppNavGraph(
    authManager: AuthManager,
    storageConfigManager: StorageConfigManager,
    initialRoute: String? = null
) {
    // Use null initial to avoid flash — don't render until DataStore emits
    val isAuthenticated by authManager.isAuthenticated.collectAsState(initial = null)
    val hasRepo by authManager.hasRepo.collectAsState(initial = null)
    val onboardingComplete by storageConfigManager.onboardingComplete.collectAsState(initial = null)

    // Wait for DataStore to emit before rendering
    if (isAuthenticated == null || hasRepo == null || onboardingComplete == null) return

    val authed = isAuthenticated == true && hasRepo == true

    val navController = rememberNavController()

    // Decision tree for start destination
    val startDestination: Any = when {
        onboardingComplete == false -> OnboardingRoute  // First launch - show onboarding
        authed -> MainRoute                              // GitHub mode configured and authenticated
        else -> MainRoute                                // Local mode or not configured - go to main
    }

    // Handle initial route from intent extras (e.g. from NoteCaptureActivity)
    LaunchedEffect(initialRoute) {
        if (initialRoute != null && authed) {
            when (initialRoute) {
                "settings" -> navController.navigate(SettingsRoute)
                "browse" -> navController.navigate(BrowseRoute)
            }
        }
    }

    // Always return to MainRoute when app comes back from background
    var isFirstStart by rememberSaveable { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (isFirstStart) {
            isFirstStart = false
        } else if (authed) {
            navController.popBackStack(MainRoute, inclusive = false)
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(MainRoute) {
                        popUpTo<OnboardingRoute> { inclusive = true }
                    }
                }
            )
        }

        composable<AuthRoute> {
            AuthScreen(
                onAuthComplete = {
                    navController.navigate(MainRoute) {
                        popUpTo<AuthRoute> { inclusive = true }
                    }
                }
            )
        }

        composable<MainRoute> {
            MainScreen(
                onSettingsClick = {
                    navController.navigate(SettingsRoute)
                },
                onBrowseClick = {
                    navController.navigate(BrowseRoute)
                }
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(AuthRoute) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable<BrowseRoute> {
            BrowseScreen(
                onBack = { navController.popBackStack() },
                onSettingsClick = { navController.navigate(SettingsRoute) }
            )
        }
    }
}
