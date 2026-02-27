package com.yuval.podcasts.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yuval.podcasts.ui.components.MiniPlayer
import com.yuval.podcasts.ui.navigation.Screen
import com.yuval.podcasts.ui.navigation.bottomNavItems
import com.yuval.podcasts.ui.screens.NewEpisodesScreen
import com.yuval.podcasts.ui.screens.PodcastDetailScreen
import com.yuval.podcasts.ui.screens.QueueScreen
import com.yuval.podcasts.ui.screens.SettingsScreen
import com.yuval.podcasts.ui.screens.SubscriptionsScreen
import com.yuval.podcasts.ui.viewmodel.QueueViewModel

@Composable
fun MainScreen(
    queueViewModel: QueueViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            Column {
                MiniPlayer(viewModel = queueViewModel) {
                    // TODO: Expand to full screen player
                }
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Queue.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Queue.route) { 
                QueueScreen(onEpisodeClick = { episodeId -> 
                    navController.navigate(Screen.EpisodeDetail.createRoute(episodeId))
                }) 
            }
            composable(Screen.NewEpisodes.route) { 
                NewEpisodesScreen(onEpisodeClick = { episodeId -> 
                    navController.navigate(Screen.EpisodeDetail.createRoute(episodeId))
                }) 
            }
            composable(Screen.Subscriptions.route) {
                SubscriptionsScreen(onPodcastClick = { feedUrl ->
                    navController.navigate(Screen.PodcastDetail.createRoute(feedUrl))
                })
            }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable(
                route = Screen.PodcastDetail.route,
                arguments = listOf(navArgument("feedUrl") { type = NavType.StringType })
            ) { backStackEntry ->
                val feedUrl = backStackEntry.arguments?.getString("feedUrl") ?: ""
                PodcastDetailScreen(
                    feedUrl = java.net.URLDecoder.decode(feedUrl, "UTF-8"),
                    onEpisodeClick = { episodeId -> 
                        navController.navigate(Screen.EpisodeDetail.createRoute(episodeId))
                    }
                )
            }
            composable(
                route = Screen.EpisodeDetail.route,
                arguments = listOf(navArgument("episodeId") { type = NavType.StringType })
            ) { backStackEntry ->
                val episodeId = backStackEntry.arguments?.getString("episodeId") ?: ""
                com.yuval.podcasts.ui.screens.EpisodeDetailScreen(
                    episodeId = java.net.URLDecoder.decode(episodeId, "UTF-8"),
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
