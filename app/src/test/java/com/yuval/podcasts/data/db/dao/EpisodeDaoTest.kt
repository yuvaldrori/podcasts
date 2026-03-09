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

        val episode1 = Episode("ep1", "url1", "E1", "D", "A", null, null, 1000L, 0L, 0, null, false, 0L, null, 0L)
        val episode2 = Episode("ep2", "url1", "E2", "D", "A", null, null, 2000L, 0L, 0, null, false, 0L, null, 0L)
        
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

        val episode = Episode("ep1", "url1", "E1", "D", "A", null, null, 1000L, 0L, 0, null, false, 0L, null, 0L)
        episodeDao.upsertEpisodes(listOf(com.yuval.podcasts.data.db.entity.NetworkEpisode(episode.id, episode.podcastFeedUrl, episode.title, episode.description, episode.audioUrl, episode.imageUrl, episode.episodeWebLink, episode.pubDate, episode.duration)))

        episodeDao.updatePlaybackStatus("ep1", true, 12345L)

        val fetchedEpisode = episodeDao.getEpisodeById("ep1")
        assertNotNull(fetchedEpisode)
        assertEquals(true, fetchedEpisode?.isPlayed)
        assertEquals(12345L, fetchedEpisode?.completedAt)
    }

    @Test
    fun getPlayHistory_returnsCompletedEpisodesOnly() = runBlocking {
        val podcast = Podcast("url1", "P1", "D1", "I1", "W1")
        podcastDao.insertPodcast(podcast)

        // Add an episode that's simply enqueued (isPlayed=true, completedAt=null)
        val ep1 = Episode("ep1", "url1", "E1", "D", "A", null, null, 1000L, 0L, 0, null, false, 0L, null, 0L)
        // Add an episode that's finished (isPlayed=true, completedAt=1000)
        val ep2 = Episode("ep2", "url1", "E2", "D", "A", null, null, 2000L, 0L, 0, null, false, 0L, null, 0L)
        // Add an episode that's finished later (isPlayed=true, completedAt=2000)
        val ep3 = Episode("ep3", "url1", "E3", "D", "A", null, null, 3000L, 0L, 0, null, false, 0L, null, 0L)
        
        episodeDao.upsertEpisodes(listOf(
            com.yuval.podcasts.data.db.entity.NetworkEpisode(ep1.id, ep1.podcastFeedUrl, ep1.title, ep1.description, ep1.audioUrl, ep1.imageUrl, ep1.episodeWebLink, ep1.pubDate, ep1.duration),
            com.yuval.podcasts.data.db.entity.NetworkEpisode(ep2.id, ep2.podcastFeedUrl, ep2.title, ep2.description, ep2.audioUrl, ep2.imageUrl, ep2.episodeWebLink, ep2.pubDate, ep2.duration),
            com.yuval.podcasts.data.db.entity.NetworkEpisode(ep3.id, ep3.podcastFeedUrl, ep3.title, ep3.description, ep3.audioUrl, ep3.imageUrl, ep3.episodeWebLink, ep3.pubDate, ep3.duration)
        ))

        episodeDao.updatePlaybackStatus("ep1", true, null)
        episodeDao.updatePlaybackStatus("ep2", true, 1000L)
        episodeDao.updatePlaybackStatus("ep3", true, 2000L)

        val history = episodeDao.getPlayHistory().first()

        assertEquals(2, history.size)
        // Should be ordered by completedAt DESC
        assertEquals("ep3", history[0].episode.id)
        assertEquals("ep2", history[1].episode.id)
    }

    @Test
    fun getUnplayedEpisodesWithPodcast_returnsOnlyUnplayed() = runBlocking {
        val podcast = Podcast("url1", "P1", "D1", "I1", "W1")
        podcastDao.insertPodcast(podcast)

        val unplayedEpisode = Episode("ep1", "url1", "E1", "D", "A", null, null, 2000L, 0L, 0, null, false, 0L, null, 0L)
        val playedEpisode = Episode("ep2", "url1", "E2", "D", "A", null, null, 1000L, 0L, 0, null, true, 0L)
        
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

        val unplayedEpisode1 = Episode("ep1", "url1", "E1", "D", "A", null, null, 2000L, 0L, 0, null, false, 0L, null, 0L)
        val unplayedEpisode2 = Episode("ep2", "url1", "E2", "D", "A", null, null, 1000L, 0L, 0, null, false, 0L, null, 0L)
        
        episodeDao.insertEpisodes(listOf(unplayedEpisode1, unplayedEpisode2))

        episodeDao.markAllUnplayedAsPlayed()

        val unplayed = episodeDao.getUnplayedEpisodesWithPodcast().first()
        assertEquals(0, unplayed.size)
    }


    @Test
    fun getEpisodesForPodcastSync_returnsCorrectList() = runBlocking {
        val podcast = Podcast("url2", "P2", "D2", "I2", "W2")
        podcastDao.insertPodcast(podcast)

        val episode1 = Episode("ep3", "url2", "E3", "D", "A", null, null, 1000L, 0L, 0, null, false, 0L, null, 0L)
        val episode2 = Episode("ep4", "url2", "E4", "D", "A", null, null, 2000L, 0L, 0, null, false, 0L, null, 0L)
        
        episodeDao.insertEpisodes(listOf(episode1, episode2))

        val episodes = episodeDao.getEpisodesForPodcastSync("url2")
        assertEquals(2, episodes.size)
        // Ensure they match the inserted data
        assertEquals("url2", episodes[0].podcastFeedUrl)
    }

    @Test
    fun deleteEpisodesByPodcast_removesOnlyMatchingEpisodes() = runBlocking {
        val podcast1 = Podcast("url1", "P1", "D1", "I1", "W1")
        val podcast2 = Podcast("url2", "P2", "D2", "I2", "W2")
        podcastDao.insertPodcast(podcast1)
        podcastDao.insertPodcast(podcast2)

        val ep1 = Episode("ep1", "url1", "E1", "D", "A", null, null, 1000L, 0L, 0, null, false, 0L, null, 0L)
        val ep2 = Episode("ep2", "url2", "E2", "D", "A", null, null, 2000L, 0L, 0, null, false, 0L, null, 0L)
        episodeDao.insertEpisodes(listOf(ep1, ep2))

        episodeDao.deleteEpisodesByPodcast("url1")

        val remainingEp1 = episodeDao.getEpisodeById("ep1")
        val remainingEp2 = episodeDao.getEpisodeById("ep2")
        
        org.junit.Assert.assertNull(remainingEp1)
        assertNotNull(remainingEp2)
    }
}
