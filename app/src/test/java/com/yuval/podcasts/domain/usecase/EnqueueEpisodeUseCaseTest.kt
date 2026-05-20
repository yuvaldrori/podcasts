package com.yuval.podcasts.domain.usecase

import androidx.work.WorkManager
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.data.db.entity.Episode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class EnqueueEpisodeUseCaseTest {

    private lateinit var repository: PodcastRepository
    private lateinit var workManager: WorkManager
    private lateinit var enqueueEpisodeUseCase: EnqueueEpisodeUseCase

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        
        enqueueEpisodeUseCase = EnqueueEpisodeUseCase(repository, workManager)
    }

    @Test
    fun `invoke calls requeueEpisode and enqueues download work`() = runTest {
        val newEpisode = Episode("newEp", "feed1", "TNew", "DNew", "http://example.com/audio.mp3", null, null, 3000L, 0L, 0, null, false, 0L)

        enqueueEpisodeUseCase(newEpisode)

        coVerify { repository.requeueEpisode(newEpisode) }
        coVerify { workManager.enqueueUniqueWork(any<String>(), any<androidx.work.ExistingWorkPolicy>(), any<androidx.work.OneTimeWorkRequest>()) }
    }

    @Test
    fun `invoke calls requeueEpisode but does not enqueue download work if episode is local`() = runTest {
        val localEpisode = Episode("localEp", "feed1", "TLocal", "DLocal", "/local/path.mp3", null, null, 3000L, 0L, 0, null, false, 0L)

        enqueueEpisodeUseCase(localEpisode)

        coVerify { repository.requeueEpisode(localEpisode) }
        coVerify(exactly = 0) { workManager.enqueueUniqueWork(any<String>(), any<androidx.work.ExistingWorkPolicy>(), any<androidx.work.OneTimeWorkRequest>()) }
    }

    @Test
    fun `invoke calls requeueEpisode but does not enqueue download work if episode is already downloaded`() = runTest {
        val downloadedEpisode = Episode("downloadedEp", "feed1", "TDownloaded", "DDownloaded", "http://example.com/audio.mp3", null, null, 3000L, 0L, 2, "/local/path.mp3", false, 0L)

        enqueueEpisodeUseCase(downloadedEpisode)

        coVerify { repository.requeueEpisode(downloadedEpisode) }
        coVerify(exactly = 0) { workManager.enqueueUniqueWork(any<String>(), any<androidx.work.ExistingWorkPolicy>(), any<androidx.work.OneTimeWorkRequest>()) }
    }
}

