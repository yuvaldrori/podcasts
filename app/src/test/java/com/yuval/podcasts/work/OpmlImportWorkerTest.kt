package com.yuval.podcasts.work

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.ProgressUpdater
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.google.common.util.concurrent.Futures
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.opml.OpmlManager
import com.yuval.podcasts.data.repository.PodcastRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

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

        val shadowContentResolver = Shadows.shadowOf(context.contentResolver)
        shadowContentResolver.registerInputStream(uri, "test data".byteInputStream())

        every { opmlManager.parse(any()) } returns urls
        coEvery { repository.refreshPodcasts(urls, forceRefresh = false, any()) } coAnswers {
            val onProgress = arg<suspend (Int, Int) -> Unit>(2)
            onProgress(1, 2)
            onProgress(2, 2)
            2
        }

        val reportedProgress = mutableListOf<Pair<Int, Int>>()
        val progressUpdater = ProgressUpdater { _, _, data: Data ->
            reportedProgress.add(
                data.getInt(Constants.WORK_KEY_PROGRESS, -1) to data.getInt(Constants.WORK_KEY_TOTAL, -1)
            )
            Futures.immediateFuture(null)
        }

        val worker = TestListenableWorkerBuilder<OpmlImportWorker>(context)
            .setInputData(workDataOf(OpmlImportWorker.KEY_URI to uri.toString()))
            .setProgressUpdater(progressUpdater)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    val spyContext = spyk(appContext)
                    every { spyContext.getString(any()) } returns "test"
                    every { spyContext.getString(any(), any(), any()) } returns "test"

                    return OpmlImportWorker(spyContext, workerParameters, opmlManager, repository, mockk(relaxed = true), Dispatchers.Unconfined)
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { repository.refreshPodcasts(urls, forceRefresh = false, any()) }
        assertEquals(listOf(0 to 2, 1 to 2, 2 to 2), reportedProgress)
    }
}
