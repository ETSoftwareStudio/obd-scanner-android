package studio.etsoftware.obdapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import studio.etsoftware.obdapp.ui.feature.connection.ConnectionScreen
import studio.etsoftware.obdapp.ui.feature.dashboard.DashboardScreen
import studio.etsoftware.obdapp.ui.feature.diagnostics.DiagnosticsScreen
import studio.etsoftware.obdapp.ui.feature.settings.SettingsScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    fun navigateToConnectionScreen() {
        navController.navigate(Screen.Connection.route) {
            popUpTo(Screen.MAIN_TABS_ROUTE) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Connection.route,
        modifier = modifier,
    ) {
        composable(Screen.Connection.route) {
            ConnectionScreen(
                onDeviceConnected = {
                    navController.navigate(Screen.MAIN_TABS_ROUTE) {
                        popUpTo(Screen.Connection.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        navigation(
            route = Screen.MAIN_TABS_ROUTE,
            startDestination = Screen.Dashboard.route,
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onConnectClick = ::navigateToConnectionScreen,
                    onDisconnected = ::navigateToConnectionScreen,
                )
            }

            composable(Screen.Diagnostics.route) {
                DiagnosticsScreen(
                    onConnectClick = ::navigateToConnectionScreen,
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
