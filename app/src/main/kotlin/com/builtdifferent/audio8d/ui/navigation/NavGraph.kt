package com.builtdifferent.audio8d.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Convert : Screen("convert/{conversionId}") {
        fun createRoute(conversionId: Long) = "convert/$conversionId"
    }
    data object Player : Screen("player/{conversionId}") {
        fun createRoute(conversionId: Long) = "player/$conversionId"
    }
    data object Library : Screen("library")
    data object Settings : Screen("settings")
}
