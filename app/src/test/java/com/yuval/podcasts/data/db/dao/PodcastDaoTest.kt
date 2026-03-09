package com.yuval.podcasts.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yuval.podcasts.data.db.AppDatabase
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.Podcast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33]) // Define SDK to avoid warning
class PodcastDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var podcastDao: PodcastDao
    private lateinit var episodeDao: EpisodeDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        podcastDao = database.podcastDao()
        episodeDao = database.episodeDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetPodcasts() = runBlocking {
        val podcast1 = Podcast("url1", "P1", "D1", "I1", "W1")
        val podcast2 = Podcast("url2", "P2", "D2", "I2", "W2")

        podcastDao.insertPodcast(podcast1)
        podcastDao.insertPodcast(podcast2)

        val allPodcasts = podcastDao.getAllPodcasts().first()

        assertEquals(2, allPodcasts.size)
        assertTrue(allPodcasts.contains(podcast1))
        assertTrue(allPodcasts.contains(podcast2))
    }

    @Test
    fun getAllPodcasts_sortedByLatestEpisode() = runBlocking {
        val podcast1 = Podcast("url1", "P1", "D1", "I1", "W1")
        val podcast2 = Podcast("url2", "P2", "D2", "I2", "W2")
        
        podcastDao.insertPodcast(podcast1)
        podcastDao.insertPodcast(podcast2)

        // Podcast 2 has a newer episode, so it should be first
        val episode1 = Episode("ep1", "url1", "E1", "D", "A", null, null, 1000L, 0L, 0, null, false, 0L, null, 0L)
        val episode2 = Episode("ep2", "url2", "E2", "D", "A", null, null, 2000L, 0L, 0, null, false, 0L, null, 0L)
        
        episodeDao.insertEpisodes(listOf(episode1, episode2))

        val allPodcasts = podcastDao.getAllPodcasts().first()

        assertEquals("url2", allPodcasts[0].feedUrl)
        assertEquals("url1", allPodcasts[1].feedUrl)
    }


    @Test
    fun deletePodcast_removesOnlyMatchingPodcast() = runBlocking {
        val podcast1 = Podcast("url1", "P1", "D1", "I1", "W1")
        val podcast2 = Podcast("url2", "P2", "D2", "I2", "W2")

        podcastDao.insertPodcast(podcast1)
        podcastDao.insertPodcast(podcast2)

        podcastDao.deletePodcast("url1")

        val allPodcasts = podcastDao.getAllPodcasts().first()

        assertEquals(1, allPodcasts.size)
        assertEquals("url2", allPodcasts[0].feedUrl)
    }
}
