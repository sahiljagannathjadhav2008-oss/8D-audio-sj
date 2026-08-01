package com.builtdifferent.audio8d

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.builtdifferent.audio8d.ui.navigation.Screen
import com.builtdifferent.audio8d.ui.screens.home.HomeScreen
import com.builtdifferent.audio8d.ui.screens.convert.ConvertScreen
import com.builtdifferent.audio8d.ui.screens.player.PlayerScreen
import com.builtdifferent.audio8d.ui.screens.library.LibraryScreen
import com.builtdifferent.audio8d.ui.screens.settings.SettingsScreen
import com.builtdifferent.audio8d.ui.theme.Audio8DTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            Audio8DTheme {
                Audio8DApp()
            }
        }
    }
}

private data class BottomDestination(val screen: Screen, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun Audio8DApp() {
    val navController = rememberNavController()
    val destinations = listOf(
        BottomDestination(Screen.Home, "Home", Icons.Filled.Home),
        BottomDestination(Screen.Library, "Library", Icons.Filled.LibraryMusic),
        BottomDestination(Screen.Settings, "Settings", Icons.Filled.Settings)
    )

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            if (currentRoute in destinations.map { it.screen.route }) {
                NavigationBar {
                    destinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.screen.route,
                            onClick = {
                                navController.navigate(dest.screen.route) {
                                    popUpTo(Screen.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onOpenConvert = { id -> navController.navigate(Screen.Convert.createRoute(id)) },
                    onOpenPlayer = { id -> navController.navigate(Screen.Player.createRoute(id)) }
                )
            }
            composable(Screen.Convert.route) { backStackEntry ->
                ConvertScreen(
                    conversionIdArg = backStackEntry.arguments?.getString("conversionId"),
                    onFinished = { finishedId -> navController.navigate(Screen.Player.createRoute(finishedId)) {
                        popUpTo(Screen.Home.route)
                    } }
                )
            }
            composable(Screen.Player.route) { backStackEntry ->
                PlayerScreen(conversionIdArg = backStackEntry.arguments?.getString("conversionId"))
            }
            composable(Screen.Library.route) {
                LibraryScreen(onOpenPlayer = { id -> navController.navigate(Screen.Player.createRoute(id)) })
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
