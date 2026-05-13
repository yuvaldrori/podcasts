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
        val newEpisode = Episode("newEp", "feed1", "TNew", "DNew", "url", null, null, 3000L, 0L, 0, null, false, 0L)

        enqueueEpisodeUseCase(newEpisode)

        coVerify { repository.requeueEpisode(newEpisode) }
        coVerify { workManager.enqueueUniqueWork(any<String>(), any<androidx.work.ExistingWorkPolicy>(), any<androidx.work.OneTimeWorkRequest>()) }
    }
}
