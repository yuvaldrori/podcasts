package com.yuval.podcasts.domain.usecase

import androidx.work.WorkManager
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.QueueDao
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

import kotlinx.coroutines.test.UnconfinedTestDispatcher

class RemoveEpisodeUseCaseTest {

    private val episodeDao = mockk<EpisodeDao>(relaxed = true)
    private val queueDao = mockk<QueueDao>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)
    
    private val useCase = RemoveEpisodeUseCase(episodeDao, queueDao, workManager, UnconfinedTestDispatcher())

    @Test
    fun invoke_withMarkAsPlayedTrue_updatesPlaybackStatusWithTimestamp() = runTest {
        useCase("ep1", markAsPlayed = true)
        
        // Use any(Long::class) for the completedAt param because it uses System.currentTimeMillis()
        coVerify { episodeDao.updatePlaybackStatus("ep1", true, any()) }
        coVerify { episodeDao.updateLastPlayedPosition("ep1", 0L) }
        coVerify { queueDao.removeFromQueue("ep1") }
    }

    @Test
    fun invoke_withMarkAsPlayedFalse_doesNotUpdatePlaybackStatus() = runTest {
        useCase("ep1", markAsPlayed = false)
        
        coVerify(exactly = 0) { episodeDao.updatePlaybackStatus(any(), any(), any()) }
        coVerify(exactly = 0) { episodeDao.updateLastPlayedPosition(any(), any()) }
        coVerify { queueDao.removeFromQueue("ep1") }
    }
}
