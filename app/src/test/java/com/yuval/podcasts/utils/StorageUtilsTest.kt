package com.yuval.podcasts.utils

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageUtilsTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var filesDir: File

    @Before
    fun setup() {
        context = mockk()
        filesDir = tmpFolder.newFolder("files")
        every { context.filesDir } returns filesDir
    }

    @Test
    fun getDownloadsDir_returnsCorrectPathAndCreatesDirectory() {
        val expectedDir = File(filesDir, "podcasts")
        assertFalse("Directory should not exist before calling getDownloadsDir", expectedDir.exists())

        val dir = StorageUtils.getDownloadsDir(context)

        assertEquals(expectedDir.absolutePath, dir.absolutePath)
        assertTrue("getDownloadsDir must create the podcasts directory", dir.exists())
        assertTrue("getDownloadsDir must create a directory", dir.isDirectory)
    }

    @Test
    fun getFileName_isConsistentForSameId() {
        val id1 = "episode_123"
        val id2 = "episode_123"

        val name1 = StorageUtils.getFileName(id1)
        val name2 = StorageUtils.getFileName(id2)

        assertEquals(name1, name2)
        assertTrue(name1.startsWith("episode_"))
        assertTrue(name1.endsWith(".mp3"))
    }

    @Test
    fun getFileName_isDifferentForDifferentIds() {
        val id1 = "episode_1"
        val id2 = "episode_2"

        val name1 = StorageUtils.getFileName(id1)
        val name2 = StorageUtils.getFileName(id2)

        assertTrue(name1 != name2)
    }

    @Test
    fun getFileForEpisode_returnsFileInCorrectDir() {
        val episodeId = "test_ep"

        val file = StorageUtils.getFileForEpisode(context, episodeId)

        assertEquals(File(File(filesDir, "podcasts"), StorageUtils.getFileName(episodeId)).absolutePath, file.absolutePath)
    }
}
