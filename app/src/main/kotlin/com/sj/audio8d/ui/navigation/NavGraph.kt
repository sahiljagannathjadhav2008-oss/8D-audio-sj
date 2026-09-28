package com.sj.audio8d.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.LaunchedEffect
import com.sj.audio8d.ui.screens.*
import com.sj.audio8d.viewmodel.LibraryViewModel
import com.sj.audio8d.viewmodel.PlayerViewModel

object Routes {
    const val PERMISSION = "permission"
    const val HOME = "home"
    const val ALL_SONGS = "all_songs"
    const val SEARCH = "search"
    const val ARTISTS = "artists"
    const val ALBUMS = "albums"
    const val NOW_PLAYING = "now_playing"
    const val EIGHT_D_CONTROLS = "eight_d_controls"
    const val QUEUE = "queue"
    const val FAVORITES = "favorites"
    const val RECENTLY_PLAYED = "recently_played"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}

private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, "Home", Icons.Default.Home),
    BottomTab(Routes.ALL_SONGS, "Library", Icons.Default.LibraryMusic),
    BottomTab(Routes.QUEUE, "Queue", Icons.Default.QueueMusic),
    BottomTab(Routes.SETTINGS, "More", Icons.Default.MoreHoriz)
)

@Composable
fun Audio8DNavHost(
    hasLibraryPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val navController = rememberNavController()
    val libraryViewModel: LibraryViewModel = viewModel()
    val playerViewModel: PlayerViewModel = viewModel()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomTabs.any { it.route == currentRoute }

    LaunchedEffect(hasLibraryPermission) {
        if (hasLibraryPermission) libraryViewModel.loadLibrary()
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (hasLibraryPermission) Routes.HOME else Routes.PERMISSION,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.PERMISSION) {
                PermissionScreen(
                    onRequestPermission = onRequestPermission,
                    onOpenAppSettings = onOpenAppSettings,
                    onGranted = {
                        libraryViewModel.loadLibrary()
                        navController.navigate(Routes.HOME) { popUpTo(Routes.PERMISSION) { inclusive = true } }
                    },
                    hasPermission = hasLibraryPermission
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    libraryViewModel = libraryViewModel,
                    playerViewModel = playerViewModel,
                    onOpenAllSongs = { navController.navigate(Routes.ALL_SONGS) },
                    onOpenArtists = { navController.navigate(Routes.ARTISTS) },
                    onOpenAlbums = { navController.navigate(Routes.ALBUMS) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenRecentlyPlayed = { navController.navigate(Routes.RECENTLY_PLAYED) },
                    onSongClick = { navController.navigate(Routes.NOW_PLAYING) }
                )
            }
            composable(Routes.ALL_SONGS) {
                AllSongsScreen(
                    libraryViewModel = libraryViewModel,
                    playerViewModel = playerViewModel,
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onSongClick = { navController.navigate(Routes.NOW_PLAYING) }
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    libraryViewModel = libraryViewModel,
                    playerViewModel = playerViewModel,
                    onBack = { navController.popBackStack() },
                    onSongClick = { navController.navigate(Routes.NOW_PLAYING) }
                )
            }
            composable(Routes.ARTISTS) {
                ArtistsScreen(libraryViewModel = libraryViewModel, onBack = { navController.popBackStack() })
            }
            composable(Routes.ALBUMS) {
                AlbumsScreen(libraryViewModel = libraryViewModel, onBack = { navController.popBackStack() })
            }
            composable(Routes.NOW_PLAYING) {
                NowPlayingScreen(
                    playerViewModel = playerViewModel,
                    onBack = { navController.popBackStack() },
                    onOpen8DControls = { navController.navigate(Routes.EIGHT_D_CONTROLS) },
                    onOpenQueue = { navController.navigate(Routes.QUEUE) }
                )
            }
            composable(Routes.EIGHT_D_CONTROLS) {
                EightDControlsScreen(playerViewModel = playerViewModel, onClose = { navController.popBackStack() })
            }
            composable(Routes.QUEUE) {
                QueueScreen(playerViewModel = playerViewModel, onBack = { navController.popBackStack() })
            }
            composable(Routes.FAVORITES) {
                FavoritesScreen(
                    libraryViewModel = libraryViewModel,
                    playerViewModel = playerViewModel,
                    onBack = { navController.popBackStack() },
                    onSongClick = { navController.navigate(Routes.NOW_PLAYING) }
                )
            }
            composable(Routes.RECENTLY_PLAYED) {
                RecentlyPlayedScreen(
                    libraryViewModel = libraryViewModel,
                    playerViewModel = playerViewModel,
                    onBack = { navController.popBackStack() },
                    onSongClick = { navController.navigate(Routes.NOW_PLAYING) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    playerViewModel = playerViewModel,
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    onOpenFavorites = { navController.navigate(Routes.FAVORITES) }
                )
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
