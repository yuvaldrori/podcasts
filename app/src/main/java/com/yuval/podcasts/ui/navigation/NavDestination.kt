package com.yuval.podcasts.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.History
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// Bottom Navigation items
sealed class BottomNavItem(val route: Any, val title: String, val icon: ImageVector) {
    object Queue : BottomNavItem(QueueScreenRoute, "Queue", Icons.AutoMirrored.Filled.List)
    object NewEpisodes : BottomNavItem(NewEpisodesScreenRoute, "New", Icons.Default.Notifications)
    object Subscriptions : BottomNavItem(SubscriptionsScreenRoute, "Podcasts", Icons.Default.Info)
    object History : BottomNavItem(HistoryScreenRoute, "History", Icons.Default.History)
    object Settings : BottomNavItem(SettingsScreenRoute, "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    BottomNavItem.Queue,
    BottomNavItem.NewEpisodes,
    BottomNavItem.Subscriptions,
    BottomNavItem.History,
    BottomNavItem.Settings
)

// Type-Safe Compose Navigation Routes
@Serializable
object QueueScreenRoute

@Serializable
object NewEpisodesScreenRoute

@Serializable
object SubscriptionsScreenRoute

@Serializable
object HistoryScreenRoute

@Serializable
object SettingsScreenRoute

@Serializable
data class PodcastDetailScreenRoute(val feedUrl: String)

@Serializable
data class EpisodeDetailScreenRoute(val episodeId: String)
