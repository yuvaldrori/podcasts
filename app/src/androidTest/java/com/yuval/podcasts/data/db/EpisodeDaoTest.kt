package com.yuval.podcasts.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.Podcast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpisodeDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var episodeDao: EpisodeDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        episodeDao = database.episodeDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun getUnplayedEpisodes_respectsLimit() = runBlocking {
        // 1. Insert a podcast
        database.podcastDao().insertPodcast(Podcast("feed", "Title", "Desc", "url", "web"))

        // 2. Insert 10 unplayed episodes
        for (i in 1..10) {
            episodeDao.insertEpisode(
                Episode(
                    id = "ep$i",
                    podcastFeedUrl = "feed",
                    title = "Episode $i",
                    description = "Desc",
                    audioUrl = "url",
                    imageUrl = null,
                    episodeWebLink = null,
                    pubDate = 1000L * i,
                    duration = 300L,
                    downloadStatus = 0,
                    localFilePath = null,
                    isPlayed = false,
                    lastPlayedPosition = 0L,
                    completedAt = null,
                    localId = 0L
                )
            )
        }

        // 3. Test limit of 5
        val result5 = episodeDao.getUnplayedEpisodes(limit = 5).first()
        assertEquals(5, result5.size)

        // 4. Test limit of 2
        val result2 = episodeDao.getUnplayedEpisodes(limit = 2).first()
        assertEquals(2, result2.size)
        
        // 5. Verify most recent is first
        assertEquals("ep10", result2[0].id)
        assertEquals("ep9", result2[1].id)
    }
}
