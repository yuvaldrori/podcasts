package com.yuval.podcasts.appfunctions

import androidx.appfunctions.service.AppFunction
import androidx.appfunctions.AppFunctionContext
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.domain.usecase.*
import com.yuval.podcasts.media.PlayerManager
import com.yuval.podcasts.utils.LogManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Exposes podcast-related capabilities to the system (e.g., Gemini, Google Assistant)
 * via the Android AppFunctions framework.
 */
class PodcastAppFunctions @Inject constructor(
    private val playerManager: PlayerManager,
    private val refreshUseCase: RefreshAllPodcastsSyncUseCase,
    private val reorderUseCase: ReorderSubscriptionInQueueUseCase,
    private val repository: PodcastRepository,
    private val logManager: LogManager
) {

    /**
     * Resumes playback of the current queue.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun resumeQueue(context: AppFunctionContext): String {
        playerManager.play()
        return "Resuming playback"
    }

    /**
     * Pauses or stops the current playback.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun stopPlayback(context: AppFunctionContext): String {
        playerManager.pause()
        return "Playback stopped"
    }

    /**
     * Skips forward by 30 seconds.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun skipForward(context: AppFunctionContext): String {
        playerManager.seekForward(30000L)
        return "Skipped forward 30 seconds"
    }

    /**
     * Skips backward by 30 seconds.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun skipBackward(context: AppFunctionContext): String {
        playerManager.seekBackward(30000L)
        return "Skipped backward 30 seconds"
    }

    /**
     * Plays the next episode in the queue.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun nextEpisode(context: AppFunctionContext): String {
        playerManager.seekToNextMediaItem()
        return "Playing next episode"
    }

    /**
     * Refreshes all podcasts and checks for new episodes.
     * 
     * @return A message indicating the number of new episodes found.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun refreshNewEpisodes(context: AppFunctionContext): String {
        val newEpisodes = refreshUseCase()
        return if (newEpisodes > 0) {
            "Found $newEpisodes new episodes"
        } else {
            "No new episodes found"
        }
    }

    /**
     * Moves all episodes from a specific subscription to the bottom of the queue.
     * 
     * @param subscriptionNameOrUrl The unique feed URL or the title/name of the podcast subscription.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun moveSubscriptionToBottom(context: AppFunctionContext, subscriptionNameOrUrl: String): String {
        val resolvedFeedUrl = resolveSubscriptionFeedUrl(subscriptionNameOrUrl)
            ?: return "Could not find a podcast subscription matching '$subscriptionNameOrUrl'"
        reorderUseCase(resolvedFeedUrl)
        return "Moved episodes to the bottom of the queue"
    }

    private suspend fun resolveSubscriptionFeedUrl(subscriptionNameOrUrl: String): String? {
        val podcasts = repository.allPodcasts.first()
        if (podcasts.isEmpty()) return null

        // 1. Exact match on feedUrl
        val exactFeedMatch = podcasts.firstOrNull { it.feedUrl.equals(subscriptionNameOrUrl, ignoreCase = true) }
        if (exactFeedMatch != null) return exactFeedMatch.feedUrl

        // 2. Exact match on title
        val exactTitleMatch = podcasts.firstOrNull { it.title.equals(subscriptionNameOrUrl, ignoreCase = true) }
        if (exactTitleMatch != null) return exactTitleMatch.feedUrl

        // 3. Substring match on title (case-insensitive)
        val partialTitleMatch = podcasts.firstOrNull { it.title.contains(subscriptionNameOrUrl, ignoreCase = true) }
        if (partialTitleMatch != null) return partialTitleMatch.feedUrl

        return null
    }

    /**
     * Adds a user-provided note to the application debug log.
     * 
     * @param message The note or message to log.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun addDebugLog(context: AppFunctionContext, message: String): String {
        logManager.i("AppFunction", "User note: $message")
        return "Log added"
    }
}
