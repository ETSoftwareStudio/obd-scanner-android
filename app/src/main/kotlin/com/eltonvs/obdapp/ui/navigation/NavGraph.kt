package com.eltonvs.obdapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.eltonvs.obdapp.ui.feature.connection.ConnectionScreen
import com.eltonvs.obdapp.ui.feature.dashboard.DashboardScreen
import com.eltonvs.obdapp.ui.feature.diagnostics.DiagnosticsScreen
import com.eltonvs.obdapp.ui.feature.settings.SettingsScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Dashboard.route,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Screen.Connection.route) {
            ConnectionScreen(
                onDeviceConnected = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Connection.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onConnectClick = {
                    navController.navigate(Screen.Connection.route)
                },
            )
        }

        composable(Screen.Diagnostics.route) {
            DiagnosticsScreen(
                onConnectClick = {
                    navController.navigate(Screen.Connection.route)
                },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
