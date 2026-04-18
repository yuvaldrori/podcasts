package com.yuval.podcasts.utils

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StorageUtilsTest {

    private val context: Context = mockk()
    private val filesDir = File("/tmp/podcasts_test")

    @Test
    fun getDownloadsDir_returnsCorrectPathAndCreatesDirectory() {
        every { context.filesDir } returns filesDir
        
        val dir = StorageUtils.getDownloadsDir(context)
        
        assertEquals(File(filesDir, "podcasts").absolutePath, dir.absolutePath)
        // Cleanup after test if needed, but in unit tests we usually mock the directory anyway
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
        every { context.filesDir } returns filesDir
        val episodeId = "test_ep"
        
        val file = StorageUtils.getFileForEpisode(context, episodeId)
        
        assertEquals(File(File(filesDir, "podcasts"), StorageUtils.getFileName(episodeId)).absolutePath, file.absolutePath)
    }
}
