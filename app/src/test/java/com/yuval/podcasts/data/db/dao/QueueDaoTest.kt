package com.yuval.podcasts.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yuval.podcasts.data.db.AppDatabase
import com.yuval.podcasts.data.db.entity.DownloadStatus
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
    fun updateQueue_replacesExistingAndReturnsOrderedListViaDao() = runBlocking {
        val podcast = Podcast("url1", "P1", "D1", "I1", "W1")
        podcastDao.insertPodcast(podcast)
        val ep1 = Episode("ep1", "url1", "E1", "D", "A", null, null, 1000L, 0L, 0, null, false, 0L, null)
        val ep2 = Episode("ep2", "url1", "E2", "D", "A", null, null, 2000L, 0L, 0, null, false, 0L, null)
        val epOld = Episode("epOld", "url1", "Old", "D", "A", null, null, 500L, 0L, 0, null, false, 0L, null)
        episodeDao.insertEpisodes(listOf(ep1, ep2, epOld))

        // Seed initial queue so updateQueue must replace it
        queueDao.updateQueue(listOf(QueueState("epOld", position = 0)))

        // Replace with reversed positions
        queueDao.updateQueue(listOf(QueueState("ep1", position = 1), QueueState("ep2", position = 0)))

        val queueSync = queueDao.getQueueEpisodesSync()
        assertEquals(listOf("ep2", "ep1"), queueSync.map { it.id })
        assertEquals(2, queueDao.getQueueCount().first())
    }

    @Test
    fun removeFromQueue_removesSpecificItem() = runBlocking {
        val podcast = Podcast("url1", "P1", "D1", "I1", "W1")
        podcastDao.insertPodcast(podcast)
        episodeDao.insertEpisodes(
            listOf(
                Episode("ep1", "url1", "E1", "D", "A", null, null, 1000L, 0L, 0, null, false, 0L, null),
                Episode("ep2", "url1", "E2", "D", "A", null, null, 2000L, 0L, 0, null, false, 0L, null)
            )
        )
        queueDao.updateQueue(listOf(QueueState("ep1", 0), QueueState("ep2", 1)))

        queueDao.removeFromQueue("ep1")

        val queue = queueDao.getQueueEpisodesSync()
        assertEquals(listOf("ep2"), queue.map { it.id })
    }

    @Test
    fun getQueueEpisodesWithPodcast_returnsJoinedDataInOrder() = runBlocking {
        val podcast = Podcast("url1", "P1", "D1", "I1", "W1")
        podcastDao.insertPodcast(podcast)

        val episode1 = Episode("ep1", "url1", "E1", "D", "A", null, null, 1000L, 0L, 0, null, false, 0L, null)
        val episode2 = Episode("ep2", "url1", "E2", "D", "A", null, null, 2000L, 0L, 0, null, false, 0L, null)
        episodeDao.insertEpisodes(listOf(episode1, episode2))

        queueDao.updateQueue(listOf(QueueState("ep1", position = 1), QueueState("ep2", position = 0)))

        val joinedQueue = queueDao.getQueueEpisodesWithPodcast().first()

        assertEquals(2, joinedQueue.size)
        assertEquals("ep2", joinedQueue[0].episode.id)
        assertEquals("P1", joinedQueue[0].podcast.title)
        assertEquals("ep1", joinedQueue[1].episode.id)
        assertEquals("P1", joinedQueue[1].podcast.title)
    }

    @Test
    fun getQueueCount_reflectsInsertionsAndRemovals() = runBlocking {
        val podcast = Podcast("url1", "P1", "D1", "I1", "W1")
        podcastDao.insertPodcast(podcast)
        episodeDao.insertEpisodes(
            listOf(
                Episode("ep1", "url1", "E1", "D", "A", null, null, 1000L, 0L, 0, null, false, 0L, null),
                Episode("ep2", "url1", "E2", "D", "A", null, null, 2000L, 0L, 0, null, false, 0L, null)
            )
        )

        assertEquals(0, queueDao.getQueueCount().first())

        queueDao.updateQueue(listOf(QueueState("ep1", 0), QueueState("ep2", 1)))
        assertEquals(2, queueDao.getQueueCount().first())

        queueDao.removeFromQueue("ep1")
        assertEquals(1, queueDao.getQueueCount().first())
    }

    @Test
    fun getQueueEpisodesWithPodcastSync_returnsJoinedDataInOrder() = runBlocking {
        val podcast = Podcast("url1", "P1", "D1", "https://podcast.art/img.jpg", "W1")
        podcastDao.insertPodcast(podcast)

        val episode1 = Episode("ep1", "url1", "E1", "D", "A", null, null, 1000L, 0L, 0, null, false, 0L, null)
        val episode2 = Episode("ep2", "url1", "E2", "D", "A", null, null, 2000L, 0L, 0, null, false, 0L, null)
        episodeDao.insertEpisodes(listOf(episode1, episode2))

        queueDao.updateQueue(listOf(QueueState("ep1", position = 1), QueueState("ep2", position = 0)))

        val joinedQueue = queueDao.getQueueEpisodesWithPodcastSync()

        assertEquals(2, joinedQueue.size)
        assertEquals("ep2", joinedQueue[0].episode.id)
        assertEquals("https://podcast.art/img.jpg", joinedQueue[0].podcast.imageUrl)
        assertEquals("ep1", joinedQueue[1].episode.id)
        assertEquals("https://podcast.art/img.jpg", joinedQueue[1].podcast.imageUrl)
    }
}
