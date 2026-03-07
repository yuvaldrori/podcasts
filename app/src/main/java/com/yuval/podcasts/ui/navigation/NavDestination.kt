package com.yuval.podcasts.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.History
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// Bottom Navigation Routes (String-based for NavController hierarchy comparisons)
sealed class BottomNavRoute(val route: String, val title: String, val icon: ImageVector) {
    object Queue : BottomNavRoute("queue_route", "Queue", Icons.AutoMirrored.Filled.List)
    object NewEpisodes : BottomNavRoute("new_episodes_route", "New", Icons.Default.Notifications)
    object Subscriptions : BottomNavRoute("subscriptions_route", "Podcasts", Icons.Default.Info)
    object History : BottomNavRoute("history_route", "History", Icons.Default.History)
    object Settings : BottomNavRoute("settings_route", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    BottomNavRoute.Queue,
    BottomNavRoute.NewEpisodes,
    BottomNavRoute.Subscriptions,
    BottomNavRoute.History,
    BottomNavRoute.Settings
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
