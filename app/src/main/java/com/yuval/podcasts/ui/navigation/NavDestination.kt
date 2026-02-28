package com.yuval.podcasts.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Queue : Screen("queue", "Queue", Icons.AutoMirrored.Filled.List)
    object NewEpisodes : Screen("new_episodes", "New", Icons.Default.Notifications)
    object Subscriptions : Screen("subscriptions", "Podcasts", Icons.Default.Info)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object PodcastDetail : Screen("podcast_detail/{feedUrl}", "Episodes", Icons.AutoMirrored.Filled.List) {
        fun createRoute(feedUrl: String) = "podcast_detail/${java.net.URLEncoder.encode(feedUrl, "UTF-8")}"
    }
    object EpisodeDetail : Screen("episode_detail/{episodeId}", "Episode Info", Icons.Default.Info) {
        fun createRoute(episodeId: String) = "episode_detail/${java.net.URLEncoder.encode(episodeId, "UTF-8")}"
    }
}

val bottomNavItems = listOf(
    Screen.Queue,
    Screen.NewEpisodes,
    Screen.Subscriptions,
    Screen.Settings
)
