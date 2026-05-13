package com.yuval.podcasts.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.yuval.podcasts.R
import kotlinx.serialization.Serializable

// Bottom Navigation items
sealed class BottomNavItem(val route: Any, @StringRes val titleRes: Int, val icon: ImageVector) {
    object Queue : BottomNavItem(QueueScreenRoute, R.string.queue_title, Icons.AutoMirrored.Filled.List)
    object NewEpisodes : BottomNavItem(NewEpisodesScreenRoute, R.string.new_episodes_title, Icons.Default.Notifications)
    object Subscriptions : BottomNavItem(SubscriptionsScreenRoute, R.string.episodes_title, Icons.Default.Info)
    object Settings : BottomNavItem(SettingsScreenRoute, R.string.settings_title, Icons.Default.Settings)
}

val bottomNavItems = listOf(
    BottomNavItem.Queue,
    BottomNavItem.NewEpisodes,
    BottomNavItem.Subscriptions,
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
object SettingsScreenRoute

@Serializable
data class PodcastDetailScreenRoute(val feedUrl: String)

@Serializable
data class EpisodeDetailScreenRoute(val episodeId: String)
