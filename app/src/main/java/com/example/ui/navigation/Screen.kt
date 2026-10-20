package com.example.ui.navigation

sealed class Screen(val route: String, val title: String) {
    data object Dashboard : Screen("dashboard", "Dashboard")
    data object Timeline : Screen("timeline", "Timeline")
    data object MapView : Screen("map_view", "Map")
    data object History : Screen("history", "History")
    data object Settings : Screen("settings", "Settings")
    data class PlaceDetails(val visitId: Long) : Screen("place_details/$visitId", "Place Details")
}
