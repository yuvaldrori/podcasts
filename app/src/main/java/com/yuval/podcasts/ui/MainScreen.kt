package com.yuval.podcasts.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yuval.podcasts.ui.components.UnifiedPlayer
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
                UnifiedPlayer(viewModel = queueViewModel)
                NavigationBar(
                    modifier = Modifier.height(64.dp) // Reduced height from default ~80dp
                ) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp) // Slightly smaller icon
                                    )
                                    Spacer(modifier = Modifier.height(2.dp)) // Minimal space
                                    Text(
                                        text = screen.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                            },
                            label = null, // Disabling default label slot to use our custom Column in icon slot
                            selected = selected,
                            onClick = {
                                if (currentDestination?.hierarchy?.any { it.route == screen.route } == true) {
                                    navController.popBackStack(screen.route, false)
                                } else {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
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
            modifier = Modifier.padding(innerPadding),
            enterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(0)) },
            exitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(0)) },
            popEnterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(0)) },
            popExitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(0)) }
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
                    onBack = { navController.popBackStack() },
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
