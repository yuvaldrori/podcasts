package com.yuval.podcasts.domain.usecase

import androidx.work.WorkManager
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.QueueState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class EnqueueEpisodeUseCaseTest {

    private lateinit var queueDao: QueueDao
    private lateinit var episodeDao: EpisodeDao
    private lateinit var workManager: WorkManager
    private lateinit var enqueueEpisodeUseCase: EnqueueEpisodeUseCase

    @Before
    fun setup() {
        queueDao = mockk(relaxed = true)
        episodeDao = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        
        enqueueEpisodeUseCase = EnqueueEpisodeUseCase(queueDao, episodeDao, workManager)
    }

    @Test
    fun enqueue_insertsChronologically_withoutDisruptingUserOrder() = runTest {
        // Setup: Queue already has two episodes.
        // The user manually reordered them, so we must NOT change their relative order.
        // Queue currently: [UserItem1 (Pos 0), UserItem2 (Pos 1)]
        val existingQueueEpisodes = listOf(
            Episode("user1_newest", "feed1", "T1", "D1", "url", null, 5000L, 0L, 0, null, false, 0L),
            Episode("user2_oldest", "feed1", "T2", "D2", "url", null, 1000L, 0L, 0, null, false, 0L) 
        )
        
        // Notice user1_newest (pubDate 5000) is BEFORE user2_oldest (pubDate 1000). The user explicitly put it there.
        // If we just sorted the whole list by pubDate, it would flip them. We MUST NOT do that.
        
        coEvery { queueDao.getQueueEpisodes() } returns flowOf(existingQueueEpisodes)

        // The new episode we are inserting. pubDate is 3000L.
        // Rule: "the queue should have the older episode come before the newer one."
        // We scan from top to bottom. We want to insert our new item (3000) immediately BEFORE the first item that is NEWER than it.
        // At index 0, user1_newest is 5000L. 5000L is newer than 3000L.
        // So the new item should be inserted at index 0.
        // The final order should be: [newEp, user1_newest, user2_oldest]
        
        val newEpisode = Episode("newEp_middle", "feed1", "TNew", "DNew", "url", null, 3000L, 0L, 0, null, false, 0L)

        val updatedQueueSlot = slot<List<QueueState>>()
        coEvery { queueDao.updateQueue(capture(updatedQueueSlot)) } returns Unit

        enqueueEpisodeUseCase(newEpisode)

        val capturedQueue = updatedQueueSlot.captured
        assertEquals(3, capturedQueue.size)
        
        // Assert the exact order
        assertEquals("newEp_middle", capturedQueue[0].episodeId)
        assertEquals(0, capturedQueue[0].position)
        
        assertEquals("user1_newest", capturedQueue[1].episodeId)
        assertEquals(1, capturedQueue[1].position)
        
        assertEquals("user2_oldest", capturedQueue[2].episodeId)
        assertEquals(2, capturedQueue[2].position)
    }

    @Test
    fun enqueue_insertsAtEnd_ifAllItemsAreOlder() = runTest {
        val existingQueueEpisodes = listOf(
            Episode("user1_oldest", "feed1", "T1", "D1", "url", null, 1000L, 0L, 0, null, false, 0L)
        )
        coEvery { queueDao.getQueueEpisodes() } returns flowOf(existingQueueEpisodes)

        // New item is newer (2000L) than existing item (1000L).
        // Since older comes before newer, the new item goes at the end.
        val newEpisode = Episode("newEp_newer", "feed1", "TNew", "DNew", "url", null, 2000L, 0L, 0, null, false, 0L)

        val updatedQueueSlot = slot<List<QueueState>>()
        coEvery { queueDao.updateQueue(capture(updatedQueueSlot)) } returns Unit

        enqueueEpisodeUseCase(newEpisode)

        val capturedQueue = updatedQueueSlot.captured
        assertEquals(2, capturedQueue.size)
        
        assertEquals("user1_oldest", capturedQueue[0].episodeId)
        assertEquals("newEp_newer", capturedQueue[1].episodeId)
    }
}
