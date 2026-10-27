package com.driverping.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.driverping.app.data.SettingsRepository
import com.driverping.app.ui.dashboard.DashboardScreen
import com.driverping.app.ui.onboarding.OnboardingScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
}

/**
 * NavHost raíz que decide entre el Onboarding (primera ejecución)
 * y el Dashboard principal.
 */
@Composable
fun AppNavHost(
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    // Muestra Onboarding la primera vez y Dashboard en posteriores aperturas
    val start = if (settingsRepository.isOnboardingDone()) Routes.DASHBOARD else Routes.ONBOARDING

    NavHost(
        navController = navController,
        startDestination = start,
        modifier = modifier
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    settingsRepository.setOnboardingDone(true)
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen()
        }
    }
}
