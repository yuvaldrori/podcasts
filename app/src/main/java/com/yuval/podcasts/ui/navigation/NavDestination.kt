package com.yuval.podcasts.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Feeds : Screen("feeds", "Feeds", Icons.Default.Refresh)
    object Queue : Screen("queue", "Queue", Icons.Default.List)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Feeds,
    Screen.Queue,
    Screen.Settings
)
