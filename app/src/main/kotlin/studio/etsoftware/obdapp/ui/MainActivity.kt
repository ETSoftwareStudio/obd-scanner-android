package studio.etsoftware.obdapp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import studio.etsoftware.obdapp.ui.feature.settings.SettingsViewModel
import studio.etsoftware.obdapp.ui.navigation.NavGraph
import studio.etsoftware.obdapp.ui.navigation.Screen
import studio.etsoftware.obdapp.ui.theme.ConnectionStatusConnected
import studio.etsoftware.obdapp.ui.theme.ObdScannerAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MainApp()
        }
    }
}

@Composable
fun MainApp(settingsViewModel: SettingsViewModel = hiltViewModel()) {
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.hierarchy?.any { it.route == Screen.MAIN_TABS_ROUTE } == true

    val isDarkTheme =
        when (settingsState.theme) {
            "light" -> false
            "dark" -> true
            "system" -> isSystemInDarkTheme()
            else -> isSystemInDarkTheme()
        }

    ObdScannerAppTheme(darkTheme = isDarkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(
                bottomBar = {
                    if (showBottomBar) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.background,
                        ) {
                            Screen.bottomNavItems.forEach { screen ->
                                val selected =
                                    currentDestination?.hierarchy?.any {
                                        it.route == screen.route
                                    } == true

                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                            contentDescription = screen.title,
                                        )
                                    },
                                    label = { Text(screen.title) },
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(Screen.MAIN_TABS_ROUTE) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    colors =
                                        NavigationBarItemDefaults.colors(
                                            selectedIconColor = ConnectionStatusConnected,
                                            selectedTextColor = ConnectionStatusConnected,
                                            indicatorColor = ConnectionStatusConnected.copy(alpha = 0.2f),
                                        ),
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                NavGraph(
                    navController = navController,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}
