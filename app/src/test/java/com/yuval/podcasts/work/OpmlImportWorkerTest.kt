package com.yuval.podcasts.work

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.yuval.podcasts.data.opml.OpmlManager
import com.yuval.podcasts.data.repository.PodcastRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
class OpmlImportWorkerTest {
    private lateinit var context: Context
    private lateinit var repository: PodcastRepository
    private lateinit var opmlManager: OpmlManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = mockk(relaxed = true)
        opmlManager = mockk()
    }

    @Test
    fun `doWork imports all urls from OPML and reports progress`() = runBlocking {
        val urls = listOf("url1", "url2")
        val uri = Uri.parse("content://test/podcasts.opml")
        
        val realContext = ApplicationProvider.getApplicationContext<Context>()
        val shadowContentResolver = org.robolectric.Shadows.shadowOf(realContext.contentResolver)
        
        // Mocking the stream to avoid real file I/O
        val dummyStream = "test data".byteInputStream()
        shadowContentResolver.registerInputStream(uri, dummyStream)
        
        coEvery { opmlManager.parse(any()) } returns urls
        
        val worker = TestListenableWorkerBuilder<OpmlImportWorker>(realContext)
            .setInputData(workDataOf(OpmlImportWorker.KEY_URI to uri.toString()))
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: androidx.work.WorkerParameters
                ): ListenableWorker? {
                    val spyContext = spyk(appContext)
                    every { spyContext.getString(any()) } returns "test"
                    every { spyContext.getString(any(), any(), any()) } returns "test"
                    
                    return OpmlImportWorker(spyContext, workerParameters, opmlManager, repository, mockk(relaxed = true), Dispatchers.Unconfined)
                }
            })
            .build()

        val result = try {
            worker.doWork()
        } catch (e: Exception) {
            println("DOWORK FAILED: ${e.message}")
            e.printStackTrace()
            throw e
        }

        println("RESULT: $result")
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { repository.fetchAndStorePodcast("url1") }
        coVerify(exactly = 1) { repository.fetchAndStorePodcast("url2") }
    }
}
