package com.yuval.podcasts.domain.usecase

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class RefreshAllPodcastsUseCaseTest {

    @Test
    fun `invoke enqueues SyncWorker`() {
        val workManager = mockk<WorkManager>(relaxed = true)
        val useCase = RefreshAllPodcastsUseCase(workManager)

        useCase()

        verify {
            workManager.enqueueUniqueWork(
                "sync_all_podcasts",
                ExistingWorkPolicy.REPLACE,
                any<OneTimeWorkRequest>()
            )
        }
    }
}