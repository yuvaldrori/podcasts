package com.yuval.podcasts.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yuval.podcasts.data.db.AppDatabase
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.db.entity.QueueState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QueueDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var queueDao: QueueDao
    private lateinit var episodeDao: EpisodeDao
    private lateinit var podcastDao: PodcastDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        queueDao = database.queueDao()
        episodeDao = database.episodeDao()
        podcastDao = database.podcastDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun updateQueue_and_getQueue_returnsOrderedList() = runBlocking {
        val queueItem1 = QueueState("ep1", position = 1)
        val queueItem2 = QueueState("ep2", position = 0) // Should be first
        
        queueDao.updateQueue(listOf(queueItem1, queueItem2))

        val queue = queueDao.getQueue().first()

        assertEquals(2, queue.size)
        assertEquals("ep2", queue[0].episodeId)
        assertEquals("ep1", queue[1].episodeId)
    }

    @Test
    fun removeFromQueue_removesSpecificItem() = runBlocking {
        queueDao.updateQueue(listOf(QueueState("ep1", 0), QueueState("ep2", 1)))
        
        queueDao.removeFromQueue("ep1")

        val queue = queueDao.getQueue().first()
        assertEquals(1, queue.size)
        assertEquals("ep2", queue[0].episodeId)
    }

    @Test
    fun getQueueEpisodesWithPodcast_returnsJoinedDataInOrder() = runBlocking {
        // Setup Podcast
        val podcast = Podcast("url1", "P1", "D1", "I1", "W1")
        podcastDao.insertPodcast(podcast)

        // Setup Episodes
        val episode1 = Episode("ep1", "url1", "E1", "D", "A", null, null, 1000L, 0L, 0, null, false, 0L, null, 0L)
        val episode2 = Episode("ep2", "url1", "E2", "D", "A", null, null, 2000L, 0L, 0, null, false, 0L, null, 0L)
        episodeDao.insertEpisodes(listOf(episode1, episode2))

        // Setup Queue (Reverse order)
        val queueItem1 = QueueState("ep1", position = 1)
        val queueItem2 = QueueState("ep2", position = 0)
        queueDao.updateQueue(listOf(queueItem1, queueItem2))

        val joinedQueue = queueDao.getQueueEpisodesWithPodcast().first()

        assertEquals(2, joinedQueue.size)
        // Position 0 is ep2
        assertEquals("ep2", joinedQueue[0].episode.id)
        assertEquals("P1", joinedQueue[0].podcast.title)
        
        // Position 1 is ep1
        assertEquals("ep1", joinedQueue[1].episode.id)
        assertEquals("P1", joinedQueue[1].podcast.title)
    }
}
