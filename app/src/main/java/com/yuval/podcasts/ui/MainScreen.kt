package com.yuval.podcasts.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yuval.podcasts.R
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.ui.components.PlayerActions
import com.yuval.podcasts.ui.components.UnifiedPlayer
import com.yuval.podcasts.ui.navigation.*
import com.yuval.podcasts.ui.screens.*
import com.yuval.podcasts.ui.viewmodel.*
import kotlinx.collections.immutable.persistentListOf

private const val BADGE_OVERFLOW_LIMIT = 99

@Composable
fun MainScreen(
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val uiState by playerViewModel.uiState.collectAsStateWithLifecycle()

    val navBadgeViewModel: NavBadgeViewModel = hiltViewModel()
    val queueCount by navBadgeViewModel.queueCount.collectAsStateWithLifecycle()
    val newEpisodeCount by navBadgeViewModel.newEpisodeCount.collectAsStateWithLifecycle()
    
    val feedsViewModel: FeedsViewModel = hiltViewModel()
    val feedsUiState by feedsViewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing = (feedsUiState as? FeedsUiState.Success)?.isRefreshing ?: false
    val refreshProgress = (feedsUiState as? FeedsUiState.Success)?.refreshProgress
    val onRefreshAll = { feedsViewModel.refreshAll() }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            bottomNavItems.forEach { screen ->
                val selected = currentDestination?.hierarchy?.any {
                    it.hasRoute(screen.route::class)
                } == true
                val badgeCount = when (screen) {
                    BottomNavItem.Queue -> queueCount
                    BottomNavItem.NewEpisodes -> newEpisodeCount
                    else -> 0
                }
                item(
                    selected = selected,
                    modifier = Modifier.testTag("tab_${screen.tag}"),
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
                    },
                    icon = {
                        val label = stringResource(screen.titleRes)
                        val iconDescription = if (badgeCount > 0) {
                            stringResource(R.string.nav_item_with_badge, label, badgeCount)
                        } else {
                            label
                        }
                        BadgedBox(
                            badge = {
                                if (badgeCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text(if (badgeCount > BADGE_OVERFLOW_LIMIT) "$BADGE_OVERFLOW_LIMIT+" else badgeCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(screen.icon, contentDescription = iconDescription)
                        }
                    },
                    label = { Text(stringResource(screen.titleRes)) }
                )
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                val actions = remember(playerViewModel) {
                    PlayerActions(
                        onToggleSpeed = { playerViewModel.toggleSpeed() },
                        onSeekBackward = { playerViewModel.seekBackward() },
                        onPlayPause = { playerViewModel.playPause() },
                        onSeekForward = { playerViewModel.seekForward() },
                        onSeekTo = { playerViewModel.seekTo(it) }
                    )
                }
                UnifiedPlayer(
                    uiState = uiState,
                    actions = actions
                )
            }
        ) { innerPadding ->
        NavHost(
            navController = navController, 
            startDestination = QueueScreenRoute,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(animationSpec = tween(Constants.NAVIGATION_ANIMATION_MS)) },
            exitTransition = { fadeOut(animationSpec = tween(Constants.NAVIGATION_ANIMATION_MS)) },
            popEnterTransition = { fadeIn(animationSpec = tween(Constants.NAVIGATION_ANIMATION_MS)) },
            popExitTransition = { fadeOut(animationSpec = tween(Constants.NAVIGATION_ANIMATION_MS)) }
        ) {
            composable<QueueScreenRoute> { 
                val queueViewModel: QueueViewModel = hiltViewModel()
                val queueUiState by queueViewModel.uiState.collectAsStateWithLifecycle()
                val queueTimeRemaining by queueViewModel.queueTimeRemaining.collectAsStateWithLifecycle()

                QueueScreen(
                    uiState = queueUiState,
                    queueTimeRemaining = queueTimeRemaining,
                    isPlaying = uiState.isPlaying,
                    currentMediaId = uiState.currentEpisode?.id,
                    isRefreshing = isRefreshing,
                    refreshProgress = refreshProgress,
                    onRefreshAll = onRefreshAll,
                    onEpisodeClick = { episodeId -> 
                        navController.navigate(EpisodeDetailScreenRoute(episodeId))
                    },
                    onRemoveFromQueue = { episodeId -> queueViewModel.removeFromQueue(episodeId) },
                    onMoveItem = { from, to -> queueViewModel.moveItem(from, to) },
                    onCommitReorder = { queueViewModel.commitReorder() },
                    onPlayQueue = { episodes, startIndex, position -> 
                        playerViewModel.playQueue(episodes, startIndex, position)
                    },
                    onPause = { playerViewModel.playPause() },
                    contentPadding = innerPadding
                ) 
            }
            composable<NewEpisodesScreenRoute> { 
                NewEpisodesScreen(
                    uiState = feedsUiState,
                    onEpisodeClick = { episodeId -> 
                        navController.navigate(EpisodeDetailScreenRoute(episodeId))
                    },
                    onRefreshAll = onRefreshAll,
                    onDismissAll = { feedsViewModel.dismissAll() },
                    onDismissEpisode = { episode -> feedsViewModel.dismissEpisode(episode) },
                    onAddToQueue = { episode -> feedsViewModel.addToQueue(episode) },
                    onClearError = { feedsViewModel.clearError() },
                    contentPadding = innerPadding
                ) 
            }
            composable<SubscriptionsScreenRoute> {
                SubscriptionsScreen(
                    podcasts = (feedsUiState as? FeedsUiState.Success)?.podcasts ?: persistentListOf(),
                    onPodcastClick = { feedUrl ->
                        navController.navigate(PodcastDetailScreenRoute(feedUrl))
                    },
                    onUnsubscribe = { feedUrl -> feedsViewModel.unsubscribePodcast(feedUrl) },
                    isRefreshing = isRefreshing,
                    refreshProgress = refreshProgress,
                    onRefreshAll = onRefreshAll,
                    contentPadding = innerPadding
                )
            }
            composable<SettingsScreenRoute> {
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
                
                SettingsScreen(
                    uiState = settingsUiState,
                    onAddPodcast = { url -> settingsViewModel.addPodcast(url) },
                    onImportOpml = { uri -> settingsViewModel.importOpml(uri) },
                    onExportOpml = { uri -> settingsViewModel.exportOpml(uri) },
                    onImportLocalAudio = { uri -> settingsViewModel.importLocalAudio(uri) },
                    onLogNoteChanged = { note -> settingsViewModel.onLogNoteChanged(note) },
                    onSaveLogNote = { settingsViewModel.saveLogNote() },
                    onDownloadLogs = { uri -> settingsViewModel.exportLogs(uri) },
                    onClearError = { settingsViewModel.clearMessages() },
                    isRefreshing = isRefreshing,
                    refreshProgress = refreshProgress,
                    onRefreshAll = onRefreshAll,
                    contentPadding = innerPadding
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
                    onAddToQueue = { episode -> podcastDetailViewModel.addToQueue(episode) },
                    contentPadding = innerPadding
                )
            }
            composable<EpisodeDetailScreenRoute> {
                val episodeViewModel: EpisodeDetailViewModel = hiltViewModel()
                val episodeUiState by episodeViewModel.uiState.collectAsStateWithLifecycle()
                
                EpisodeDetailScreen(
                    uiState = episodeUiState,
                    onBack = { navController.popBackStack() },
                    onAddToQueue = { episode ->
                        episodeViewModel.addToQueue(episode)
                    },
                    onChapterClick = { chapter ->
                        (episodeUiState as? EpisodeDetailUiState.Success)?.let { state ->
                            playerViewModel.seekToChapter(state.episodeWithPodcast.episode, chapter)
                        }
                    },
                    contentPadding = innerPadding
                )
            }
        }
    }
}
}
