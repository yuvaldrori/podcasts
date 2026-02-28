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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EpisodeDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var episodeDao: EpisodeDao
    private lateinit var podcastDao: PodcastDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        episodeDao = database.episodeDao()
        podcastDao = database.podcastDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetEpisodes() = runBlocking {
        val podcast = Podcast("url1", "P1", "D1", "I1", "W1")
        podcastDao.insertPodcast(podcast)

        val episode1 = Episode("ep1", "url1", "E1", "D", "A", null, 1000L, 0L, 0, null, false, 0L)
        val episode2 = Episode("ep2", "url1", "E2", "D", "A", null, 2000L, 0L, 0, null, false, 0L)
        
        episodeDao.insertEpisodes(listOf(episode1, episode2))

        val episodes = episodeDao.getEpisodesForPodcast("url1").first()

        assertEquals(2, episodes.size)
        // Ensure sorted by pubDate DESC
        assertEquals("ep2", episodes[0].id)
        assertEquals("ep1", episodes[1].id)
    }

    @Test
    fun updatePlaybackStatus_marksAsPlayed() = runBlocking {
        val podcast = Podcast("url1", "P1", "D1", "I1", "W1")
        podcastDao.insertPodcast(podcast)

        val episode = Episode("ep1", "url1", "E1", "D", "A", null, 1000L, 0L, 0, null, false, 0L)
        episodeDao.insertEpisodes(listOf(episode))

        episodeDao.updatePlaybackStatus("ep1", true)

        val fetchedEpisode = episodeDao.getEpisodeById("ep1")
        assertNotNull(fetchedEpisode)
        assertEquals(true, fetchedEpisode?.isPlayed)
    }

    @Test
    fun getUnplayedEpisodesWithPodcast_returnsOnlyUnplayed() = runBlocking {
        val podcast = Podcast("url1", "P1", "D1", "I1", "W1")
        podcastDao.insertPodcast(podcast)

        val unplayedEpisode = Episode("ep1", "url1", "E1", "D", "A", null, 2000L, 0L, 0, null, false, 0L)
        val playedEpisode = Episode("ep2", "url1", "E2", "D", "A", null, 1000L, 0L, 0, null, true, 0L)
        
        episodeDao.insertEpisodes(listOf(unplayedEpisode, playedEpisode))

        val unplayedWithPodcast = episodeDao.getUnplayedEpisodesWithPodcast().first()

        assertEquals(1, unplayedWithPodcast.size)
        assertEquals("ep1", unplayedWithPodcast[0].episode.id)
        assertEquals("P1", unplayedWithPodcast[0].podcast.title)
    }

    @Test
    fun markAllUnplayedAsPlayed_updatesAll() = runBlocking {
        val podcast = Podcast("url1", "P1", "D1", "I1", "W1")
        podcastDao.insertPodcast(podcast)

        val unplayedEpisode1 = Episode("ep1", "url1", "E1", "D", "A", null, 2000L, 0L, 0, null, false, 0L)
        val unplayedEpisode2 = Episode("ep2", "url1", "E2", "D", "A", null, 1000L, 0L, 0, null, false, 0L)
        
        episodeDao.insertEpisodes(listOf(unplayedEpisode1, unplayedEpisode2))

        episodeDao.markAllUnplayedAsPlayed()

        val unplayed = episodeDao.getUnplayedEpisodesWithPodcast().first()
        assertEquals(0, unplayed.size)
    }
}
