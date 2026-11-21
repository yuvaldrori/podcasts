package com.yuval.podcasts.domain.usecase

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.entity.DownloadStatus
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.media.PlayerManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class EnqueueEpisodeUseCaseTest {

    private lateinit var repository: PodcastRepository
    private lateinit var playerManager: PlayerManager
    private lateinit var workManager: WorkManager
    private lateinit var isPlayingFlow: MutableStateFlow<Boolean>
    private lateinit var currentMediaIdFlow: MutableStateFlow<String?>
    private lateinit var enqueueEpisodeUseCase: EnqueueEpisodeUseCase

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        playerManager = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        isPlayingFlow = MutableStateFlow(false)
        currentMediaIdFlow = MutableStateFlow(null)

        every { playerManager.isPlaying } returns isPlayingFlow
        every { playerManager.currentMediaId } returns currentMediaIdFlow

        enqueueEpisodeUseCase = EnqueueEpisodeUseCase(repository, playerManager, workManager, UnconfinedTestDispatcher())
    }

    @Test
    fun `invoke calls requeueEpisode with null playing id when not playing and enqueues download work`() = runTest {
        currentMediaIdFlow.value = "loadedButPausedEp"
        isPlayingFlow.value = false
        val newEpisode = testEpisode("newEp")

        enqueueEpisodeUseCase(newEpisode)

        coVerify(exactly = 1) { repository.requeueEpisode(newEpisode, null) }
        verifyDownloadEnqueued("newEp")
    }

    @Test
    fun `invoke passes currently playing episode id to requeueEpisode and enqueues download work when playing`() = runTest {
        currentMediaIdFlow.value = "playingEp"
        isPlayingFlow.value = true
        val newEpisode = testEpisode("newEp")

        enqueueEpisodeUseCase(newEpisode)

        coVerify(exactly = 1) { repository.requeueEpisode(newEpisode, "playingEp") }
        verifyDownloadEnqueued("newEp")
    }

    @Test
    fun `invoke passes null playing id when isPlaying is true but currentMediaId is null`() = runTest {
        currentMediaIdFlow.value = null
        isPlayingFlow.value = true
        val newEpisode = testEpisode("newEp")

        enqueueEpisodeUseCase(newEpisode)

        coVerify(exactly = 1) { repository.requeueEpisode(newEpisode, null) }
        verifyDownloadEnqueued("newEp")
    }

    @Test
    fun `invoke calls requeueEpisode but does not enqueue download work if episode has local audio path`() = runTest {
        val localEpisode = testEpisode("localEp", audioUrl = "/local/path.mp3")

        enqueueEpisodeUseCase(localEpisode)

        coVerify(exactly = 1) { repository.requeueEpisode(localEpisode, null) }
        verifyNoDownloadEnqueued()
    }

    @Test
    fun `invoke calls requeueEpisode but does not enqueue download work if episode belongs to local pseudo feed`() = runTest {
        val localFeedEpisode = testEpisode("localFeedEp", feedUrl = Constants.LOCAL_PODCAST_FEED_URL)

        enqueueEpisodeUseCase(localFeedEpisode)

        coVerify(exactly = 1) { repository.requeueEpisode(localFeedEpisode, null) }
        verifyNoDownloadEnqueued()
    }

    @Test
    fun `invoke calls requeueEpisode but does not enqueue download work if episode is already downloaded and file exists`() = runTest {
        val tempFile = File.createTempFile("temp_episode", ".mp3").apply { deleteOnExit() }
        try {
            val downloadedEpisode = testEpisode(
                id = "downloadedEp",
                downloadStatus = DownloadStatus.DOWNLOADED.value,
                localFilePath = tempFile.absolutePath
            )

            enqueueEpisodeUseCase(downloadedEpisode)

            coVerify(exactly = 1) { repository.requeueEpisode(downloadedEpisode, null) }
            verifyNoDownloadEnqueued()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `invoke calls requeueEpisode and enqueues download work if episode is marked downloaded but physical file is missing`() = runTest {
        val missingFileEpisode = testEpisode(
            id = "missingEp",
            downloadStatus = DownloadStatus.DOWNLOADED.value,
            localFilePath = "/nonexistent/path.mp3"
        )

        enqueueEpisodeUseCase(missingFileEpisode)

        coVerify(exactly = 1) { repository.requeueEpisode(missingFileEpisode, null) }
        verifyDownloadEnqueued("missingEp")
    }

    @Test
    fun `invoke calls requeueEpisode and enqueues download work if episode is marked downloaded but localFilePath is null`() = runTest {
        val nullPathEpisode = testEpisode(
            id = "nullPathEp",
            downloadStatus = DownloadStatus.DOWNLOADED.value,
            localFilePath = null
        )

        enqueueEpisodeUseCase(nullPathEpisode)

        coVerify(exactly = 1) { repository.requeueEpisode(nullPathEpisode, null) }
        verifyDownloadEnqueued("nullPathEp")
    }

    @Test
    fun `invoke calls requeueEpisode and enqueues download work if downloadStatus is not downloaded even when file exists`() = runTest {
        val tempFile = File.createTempFile("temp_partial_episode", ".mp3").apply { deleteOnExit() }
        try {
            val notDownloadedEpisode = testEpisode(
                id = "partialEp",
                downloadStatus = DownloadStatus.NOT_DOWNLOADED.value,
                localFilePath = tempFile.absolutePath
            )

            enqueueEpisodeUseCase(notDownloadedEpisode)

            coVerify(exactly = 1) { repository.requeueEpisode(notDownloadedEpisode, null) }
            verifyDownloadEnqueued("partialEp")
        } finally {
            tempFile.delete()
        }
    }

    private fun testEpisode(
        id: String,
        feedUrl: String = "feed1",
        audioUrl: String = "http://example.com/$id.mp3",
        downloadStatus: Int = DownloadStatus.NOT_DOWNLOADED.value,
        localFilePath: String? = null
    ): Episode = Episode(
        id = id,
        podcastFeedUrl = feedUrl,
        title = "Title-$id",
        description = "Desc",
        audioUrl = audioUrl,
        imageUrl = null,
        episodeWebLink = null,
        pubDate = 3000L,
        duration = 0L,
        downloadStatus = downloadStatus,
        localFilePath = localFilePath,
        isPlayed = false,
        lastPlayedPosition = 0L
    )

    private fun verifyDownloadEnqueued(episodeId: String) {
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                "${Constants.WORK_TAG_DOWNLOAD_PREFIX}$episodeId",
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>()
            )
        }
    }

    private fun verifyNoDownloadEnqueued() {
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }
}

