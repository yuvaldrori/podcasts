package com.yuval.podcasts.domain.usecase

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.repository.SettingsRepository
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
                Constants.WORK_NAME_SYNC_ALL,
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>()
            )
        }
    }
}