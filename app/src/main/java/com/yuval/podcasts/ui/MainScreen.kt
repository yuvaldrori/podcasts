package com.yuval.podcasts.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yuval.podcasts.ui.components.UnifiedPlayer
import com.yuval.podcasts.ui.navigation.*
import com.yuval.podcasts.ui.screens.NewEpisodesScreen
import com.yuval.podcasts.ui.screens.PodcastDetailScreen
import com.yuval.podcasts.ui.screens.EpisodeDetailScreen
import com.yuval.podcasts.ui.screens.QueueScreen
import com.yuval.podcasts.ui.screens.SettingsScreen
import com.yuval.podcasts.ui.screens.SubscriptionsScreen
import com.yuval.podcasts.ui.viewmodel.*
import kotlinx.collections.immutable.persistentListOf

@Composable
fun MainScreen(
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val uiState by playerViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            Column {
                UnifiedPlayer(
                    currentEpisode = uiState.currentEpisode,
                    isPlaying = uiState.isPlaying,
                    playbackSpeed = uiState.playbackSpeed,
                    isConnected = uiState.isConnected,
                    currentPosition = uiState.currentPosition,
                    duration = uiState.duration,
                    onToggleSpeed = { playerViewModel.toggleSpeed() },
                    onSeekBackward = { playerViewModel.seekBackward() },
                    onPlayPause = { playerViewModel.playPause() },
                    onSeekForward = { playerViewModel.seekForward() },
                    onSeekTo = { playerViewModel.seekTo(it) }
                )
                
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route?.contains(screen.route::class.qualifiedName ?: "") == true } == true
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(screen.title) },
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            onClick = {
                                if (selected) {
                                    navController.popBackStack(screen.route, inclusive = false, saveState = true)
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
            startDestination = QueueScreenRoute,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(0)) },
            exitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(0)) },
            popEnterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(0)) },
            popExitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(0)) }
        ) {
            composable<QueueScreenRoute> { 
                val queueViewModel: QueueViewModel = hiltViewModel()
                val queueUiState by queueViewModel.uiState.collectAsStateWithLifecycle()
                
                QueueScreen(
                    uiState = queueUiState,
                    isPlaying = uiState.isPlaying,
                    currentMediaId = uiState.currentEpisode?.id,
                    onEpisodeClick = { episodeId -> 
                        navController.navigate(EpisodeDetailScreenRoute(episodeId))
                    },
                    onRemoveFromQueue = { episodeId -> queueViewModel.removeFromQueue(episodeId) },
                    onReorderQueue = { newOrder -> queueViewModel.reorderQueue(newOrder) },
                    onPlayQueue = { episodes, startIndex, position -> 
                        playerViewModel.playQueue(episodes, startIndex, position)
                    }
                ) 
            }
            composable<NewEpisodesScreenRoute> { 
                val feedsViewModel: FeedsViewModel = hiltViewModel()
                val feedsUiState by feedsViewModel.uiState.collectAsStateWithLifecycle()
                
                NewEpisodesScreen(
                    uiState = feedsUiState,
                    onEpisodeClick = { episodeId -> 
                        navController.navigate(EpisodeDetailScreenRoute(episodeId))
                    },
                    onRefreshAll = { feedsViewModel.refreshAll() },
                    onDismissAll = { feedsViewModel.dismissAll() },
                    onDismissEpisode = { episode -> feedsViewModel.dismissEpisode(episode) },
                    onAddToQueue = { episode -> feedsViewModel.addToQueue(episode) },
                    onClearError = { feedsViewModel.clearError() }
                ) 
            }
            composable<SubscriptionsScreenRoute> {
                val feedsViewModel: FeedsViewModel = hiltViewModel()
                val feedsUiState by feedsViewModel.uiState.collectAsStateWithLifecycle()
                
                SubscriptionsScreen(
                    podcasts = (feedsUiState as? FeedsUiState.Success)?.podcasts ?: persistentListOf(),
                    onPodcastClick = { feedUrl ->
                        navController.navigate(PodcastDetailScreenRoute(feedUrl))
                    },
                    onUnsubscribe = { feedUrl -> feedsViewModel.unsubscribePodcast(feedUrl) }
                )
            }
            composable<SettingsScreenRoute> {
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
                
                SettingsScreen(
                    uiState = settingsUiState,
                    onAddPodcast = { url -> settingsViewModel.addPodcast(url) },
                    onImportOpml = { uri -> settingsViewModel.importOpml(uri) },
                    onExportOpml = { ctx, uri -> settingsViewModel.exportOpml(ctx, uri) },
                    onToggleSkipSilence = { enabled -> settingsViewModel.toggleSkipSilence(enabled) },
                    onImportLocalAudio = { uri -> settingsViewModel.importLocalAudio(uri) },
                    onClearError = { settingsViewModel.clearError() }
                )
            }
            composable<PodcastDetailScreenRoute> {
                val podcastDetailViewModel: PodcastDetailViewModel = hiltViewModel()
                val episodes by podcastDetailViewModel.episodes.collectAsStateWithLifecycle()
                PodcastDetailScreen(
                    episodes = episodes,
                    onBack = { navController.popBackStack() },
                    onEpisodeClick = { episodeId ->
                        navController.navigate(EpisodeDetailScreenRoute(episodeId))
                    },
                    onAddToQueue = { episode -> podcastDetailViewModel.addToQueue(episode) }
                )
            }
            composable<EpisodeDetailScreenRoute> {
                val episodeDetailViewModel: EpisodeDetailViewModel = hiltViewModel()
                val episodeUiState by episodeDetailViewModel.uiState.collectAsStateWithLifecycle()
                val context = LocalContext.current
                EpisodeDetailScreen(
                    uiState = episodeUiState,
                    onBack = { navController.popBackStack() },
                    onAddToQueue = { episode -> episodeDetailViewModel.addToQueue(episode) },
                    onChapterClick = { chapter -> playerViewModel.seekToChapter(chapter) }
                )
            }
        }
    }
}
