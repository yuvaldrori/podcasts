package com.yuval.podcasts.appfunctions

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuval.podcasts.PodcastApplication
import com.yuval.podcasts.data.db.AppDatabase
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.db.entity.QueueState
import com.yuval.podcasts.data.network.PodcastApi
import com.yuval.podcasts.data.network.RssParser
import com.yuval.podcasts.data.repository.DefaultPodcastRepository
import com.yuval.podcasts.data.repository.DownloadProgressTracker
import com.yuval.podcasts.domain.usecase.RefreshAllPodcastsSyncUseCase
import com.yuval.podcasts.domain.usecase.ReorderSubscriptionInQueueUseCase
import com.yuval.podcasts.utils.LogManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class PodcastAppFunctionsIntegrationTest {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppFunctionsEntryPoint {
        fun podcastAppFunctions(): PodcastAppFunctions
    }

    private lateinit var app: PodcastApplication
    private lateinit var database: AppDatabase
    private lateinit var logManager: LogManager
    private lateinit var appFunctions: PodcastAppFunctions

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        // Verify real Hilt graph provides PodcastAppFunctions without eager Application injection
        val entryPoint = EntryPointAccessors.fromApplication(app, AppFunctionsEntryPoint::class.java)
        assertNotNull("PodcastAppFunctions should be provided by Hilt", entryPoint.podcastAppFunctions())

        val context: Context = app
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        logManager = LogManager(context)
        val repository = DefaultPodcastRepository(
            database = database,
            podcastApi = PodcastApi(OkHttpClient(), Dispatchers.IO),
            rssParser = RssParser(),
            podcastDao = database.podcastDao(),
            episodeDao = database.episodeDao(),
            queueDao = database.queueDao(),
            chapterDao = database.chapterDao(),
            workManager = mockk(relaxed = true),
            downloadProgressTracker = DownloadProgressTracker(),
            ioDispatcher = Dispatchers.IO,
            logManager = logManager
        )
        appFunctions = PodcastAppFunctions(
            playerManager = mockk(relaxed = true),
            refreshUseCase = RefreshAllPodcastsSyncUseCase(repository),
            reorderUseCase = ReorderSubscriptionInQueueUseCase(repository),
            repository = repository,
            logManager = logManager
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testAddDebugLog_persistsEntryToLogManager() = runBlocking {
        val uniqueNote = "Integration test note ${System.nanoTime()}"
        val result = appFunctions.addDebugLog(uniqueNote)
        assertEquals("Log added", result)

        // Allow LogManager channel consumer to process and verify via export
        var exported = ""
        repeat(10) {
            val out = ByteArrayOutputStream()
            logManager.exportLogsToStream(out)
            exported = out.toString("UTF-8")
            if (exported.contains(uniqueNote)) return@repeat
            kotlinx.coroutines.delay(50)
        }
        assertTrue("Exported logs should contain the added debug note", exported.contains(uniqueNote))
    }

    @Test
    fun testRefreshNewEpisodes_withEmptyDatabase_returnsZeroCount() = runBlocking {
        val result = appFunctions.refreshNewEpisodes()
        assertEquals("Refreshed feeds. 0 new episodes found.", result)
    }

    @Test
    fun testMoveSubscriptionToBottom_reordersPersistedQueueInRoom() = runBlocking {
        val p1 = Podcast("https://feed1.test/rss", "Tech Daily", "Desc", "https://img1", "https://web1")
        val p2 = Podcast("https://feed2.test/rss", "History Hour", "Desc", "https://img2", "https://web2")
        database.podcastDao().insertPodcast(p1)
        database.podcastDao().insertPodcast(p2)

        val ep1New = Episode("ep1_new", p1.feedUrl, "T1 New", "D", "https://a1", null, null, 3000L, 600L, 0, null, false, 0L, null)
        val ep2 = Episode("ep2", p2.feedUrl, "H1", "D", "https://a2", null, null, 2000L, 600L, 0, null, false, 0L, null)
        val ep1Old = Episode("ep1_old", p1.feedUrl, "T1 Old", "D", "https://a3", null, null, 1000L, 600L, 0, null, false, 0L, null)
        database.episodeDao().insertEpisodes(listOf(ep1New, ep2, ep1Old))
        database.queueDao().updateQueue(
            listOf(
                QueueState("ep1_new", 0),
                QueueState("ep2", 1),
                QueueState("ep1_old", 2)
            )
        )

        val result = appFunctions.moveSubscriptionToBottom("Tech")

        assertEquals("Moved episodes of 'Tech Daily' to the bottom of the queue", result)
        val reorderedIds = database.queueDao().getQueueEpisodesSync().map { it.id }
        assertEquals(listOf("ep2", "ep1_old", "ep1_new"), reorderedIds)
    }

    @Test
    fun testMoveSubscriptionToBottom_notFound() = runBlocking {
        val result = appFunctions.moveSubscriptionToBottom("Non-existent podcast")
        assertEquals("Could not find a podcast subscription matching 'Non-existent podcast'", result)
    }
}
