package com.yuval.podcasts.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yuval.podcasts.data.Constants
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

val LocalMainPadding = compositionLocalOf { PaddingValues(0.dp) }

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
                    uiState = uiState,
                    actions = com.yuval.podcasts.ui.components.PlayerActions(
                        onToggleSpeed = { playerViewModel.toggleSpeed() },
                        onSeekBackward = { playerViewModel.seekBackward() },
                        onPlayPause = { playerViewModel.playPause() },
                        onSeekForward = { playerViewModel.seekForward() },
                        onSeekTo = { playerViewModel.seekTo(it) }
                    )
                )
                
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route?.contains(screen.route::class.qualifiedName ?: "") == true } == true
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(stringResource(screen.titleRes)) },
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
        CompositionLocalProvider(LocalMainPadding provides innerPadding) {
            NavHost(
                navController = navController, 
                startDestination = QueueScreenRoute,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(Constants.NAVIGATION_ANIMATION_MS)) },
                exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(Constants.NAVIGATION_ANIMATION_MS)) },
                popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(Constants.NAVIGATION_ANIMATION_MS)) },
                popExitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(Constants.NAVIGATION_ANIMATION_MS)) }
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
                        onLogNoteChanged = { note -> settingsViewModel.onLogNoteChanged(note) },
                        onSaveLogNote = { settingsViewModel.saveLogNote() },
                        onDownloadLogs = { ctx, uri -> settingsViewModel.exportLogs(ctx, uri) },
                        onClearError = { settingsViewModel.clearMessages() }
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
}
