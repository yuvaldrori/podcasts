package com.yuval.podcasts.appfunctions

import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.domain.usecase.RefreshAllPodcastsSyncUseCase
import com.yuval.podcasts.domain.usecase.ReorderSubscriptionInQueueUseCase
import com.yuval.podcasts.media.PlayerManager
import com.yuval.podcasts.utils.LogManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Business logic for podcast-related capabilities exposed to the system (e.g., Gemini,
 * Google Assistant) via the Android AppFunctions framework.
 */
class PodcastAppFunctions @Inject constructor(
    private val playerManager: PlayerManager,
    private val refreshUseCase: RefreshAllPodcastsSyncUseCase,
    private val reorderUseCase: ReorderSubscriptionInQueueUseCase,
    private val repository: PodcastRepository,
    private val logManager: LogManager
) {
    fun resumeQueue(): String {
        playerManager.play()
        return "Resuming playback"
    }

    fun stopPlayback(): String {
        playerManager.pause()
        return "Playback stopped"
    }

    fun skipForward(): String {
        playerManager.seekForward(Constants.SEEK_FORWARD_MS)
        return "Skipped forward 30 seconds"
    }

    fun skipBackward(): String {
        playerManager.seekBackward(Constants.SEEK_BACKWARD_MS)
        return "Skipped backward 15 seconds"
    }

    fun nextEpisode(): String {
        playerManager.seekToNextMediaItem()
        return "Playing next episode"
    }

    suspend fun refreshNewEpisodes(): String {
        val newEpisodes = refreshUseCase()
        return if (newEpisodes > 0) {
            "Found $newEpisodes new episodes"
        } else {
            "No new episodes found"
        }
    }

    suspend fun moveSubscriptionToBottom(subscriptionNameOrUrl: String): String {
        val resolvedFeedUrl = resolveSubscriptionFeedUrl(subscriptionNameOrUrl)
            ?: return "Could not find a podcast subscription matching '$subscriptionNameOrUrl'"
        reorderUseCase(resolvedFeedUrl)
        return "Moved episodes to the bottom of the queue"
    }

    private suspend fun resolveSubscriptionFeedUrl(subscriptionNameOrUrl: String): String? {
        val podcasts = repository.allPodcasts.first()
        if (podcasts.isEmpty()) return null

        return podcasts.firstOrNull { it.feedUrl.equals(subscriptionNameOrUrl, ignoreCase = true) }?.feedUrl
            ?: podcasts.firstOrNull { it.title.equals(subscriptionNameOrUrl, ignoreCase = true) }?.feedUrl
            ?: podcasts.firstOrNull { it.title.contains(subscriptionNameOrUrl, ignoreCase = true) }?.feedUrl
    }

    fun addDebugLog(message: String): String {
        logManager.i(TAG, "User note: $message")
        return "Log added"
    }

    companion object {
        private const val TAG = "AppFunction"
    }
}

/**
 * AppFunctions 1.0.0 service entry point; KSP generates the concrete `PodcastAppFunctionService`
 * and `assets/podcast_app_function_service.xml` metadata from these declarations.
 */
@AndroidEntryPoint
@AppFunctionServiceEntryPoint(
    serviceName = "PodcastAppFunctionService",
    appFunctionXmlFileName = "podcast_app_function_service"
)
abstract class PodcastAppFunctionServiceEntryPoint : AppFunctionService() {

    @Inject
    lateinit var podcastAppFunctions: PodcastAppFunctions

    /**
     * Resumes or continues playing podcasts from the current playback queue.
     * Use this to play, unpause, or continue the next available podcast episode.
     *
     * @return A status message confirming that podcast playback has resumed.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun resumeQueue(): String = podcastAppFunctions.resumeQueue()

    /**
     * Pauses or stops the currently active podcast episode playback.
     * Use this to temporarily halt the audio playback of a podcast show.
     *
     * @return A status message confirming that playback has paused.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun stopPlayback(): String = podcastAppFunctions.stopPlayback()

    /**
     * Fast-forwards or jumps ahead in the currently playing podcast episode by 30 seconds.
     * Use this to skip advertisements, sponsors, or silent parts in a podcast.
     *
     * @return A status message confirming the skip forward action.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun skipForward(): String = podcastAppFunctions.skipForward()

    /**
     * Rewinds or jumps backward in the currently playing podcast episode by 15 seconds.
     * Use this to replay or go back to a portion of the podcast that was just missed.
     *
     * @return A status message confirming the skip backward action.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun skipBackward(): String = podcastAppFunctions.skipBackward()

    /**
     * Skips the current podcast episode and plays the next episode in the playback queue.
     * Use this to jump directly to the next podcast show or episode in line.
     *
     * @return A status message confirming that playback has transitioned to the next episode.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun nextEpisode(): String = podcastAppFunctions.nextEpisode()

    /**
     * Syncs, updates, or refreshes all subscribed podcasts to check for newly released episodes.
     * Use this to fetch the latest feeds and update subscription metadata.
     *
     * @return A message indicating the number of new podcast episodes found during the sync.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun refreshNewEpisodes(): String = podcastAppFunctions.refreshNewEpisodes()

    /**
     * Deprioritizes, postpones, or moves all episodes of a specific podcast subscription to the bottom of the playback queue.
     * Use this to reorder the playlist when a user wants to listen to a specific show last.
     *
     * @param subscriptionNameOrUrl The feed URL or the display title of the podcast subscription to move.
     * @return A message confirming the reordering of the podcast episodes in the queue.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun moveSubscriptionToBottom(subscriptionNameOrUrl: String): String =
        podcastAppFunctions.moveSubscriptionToBottom(subscriptionNameOrUrl)

    /**
     * Appends a custom developer note or user-provided message to the application's internal debug logs.
     * Use this for testing, debugging, or logging feedback within the app logs.
     *
     * @param message The text message or note to be appended to the debug log.
     * @return A message confirming that the note has been successfully logged.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun addDebugLog(message: String): String = podcastAppFunctions.addDebugLog(message)
}
