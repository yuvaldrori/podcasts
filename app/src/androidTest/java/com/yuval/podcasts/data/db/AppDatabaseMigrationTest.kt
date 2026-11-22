package com.yuval.podcasts.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuval.podcasts.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val testDbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrate5To6() {
        var db = helper.createDatabase(testDbName, 5)

        // Use non-default values for all columns to verify data isn't silently reset to defaults
        db.execSQL(
            """
            INSERT INTO episodes (
                id, podcastFeedUrl, title, description, audioUrl, imageUrl,
                pubDate, duration, downloadStatus, localFilePath, isPlayed,
                lastPlayedPosition, completedAt
            )
            VALUES (
                'test_episode_1', 'https://test.com/feed', 'Test Episode', 'Test Description',
                'http://audio', 'https://img.test/ep.jpg',
                123456789, 3600, 2, '/data/ep1.mp3', 1, 95000, 987654321
            )
            """.trimIndent()
        )
        db.close()

        db = helper.runMigrationsAndValidate(testDbName, 6, true, DatabaseModule.MIGRATION_5_6)

        db.query("SELECT * FROM episodes WHERE id = 'test_episode_1'").use { cursor ->
            assertTrue("Data did not survive the migration from 5 to 6", cursor.moveToFirst())
            assertEquals("Test Episode", cursor.getString(cursor.getColumnIndexOrThrow("title")))
            assertEquals("Test Description", cursor.getString(cursor.getColumnIndexOrThrow("description")))
            assertEquals("https://img.test/ep.jpg", cursor.getString(cursor.getColumnIndexOrThrow("imageUrl")))
            assertEquals(123456789L, cursor.getLong(cursor.getColumnIndexOrThrow("pubDate")))
            assertEquals(3600L, cursor.getLong(cursor.getColumnIndexOrThrow("duration")))
            assertEquals(2, cursor.getInt(cursor.getColumnIndexOrThrow("downloadStatus")))
            assertEquals("/data/ep1.mp3", cursor.getString(cursor.getColumnIndexOrThrow("localFilePath")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isPlayed")))
            assertEquals(95000L, cursor.getLong(cursor.getColumnIndexOrThrow("lastPlayedPosition")))
            assertEquals(987654321L, cursor.getLong(cursor.getColumnIndexOrThrow("completedAt")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("episodeWebLink")))
            assertTrue(cursor.getLong(cursor.getColumnIndexOrThrow("localId")) > 0L)
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate8To9() {
        val dbName = "$testDbName-8-9"
        var db = helper.createDatabase(dbName, 8)
        db.execSQL(
            """
            INSERT INTO podcasts (feedUrl, title, description, imageUrl, website)
            VALUES ('https://test.com/feed', 'Show', 'Desc', 'https://img', 'https://web')
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO episodes (
                id, podcastFeedUrl, title, description, audioUrl, imageUrl, episodeWebLink,
                pubDate, duration, downloadStatus, localFilePath, isPlayed, lastPlayedPosition, completedAt
            )
            VALUES (
                'ep_8_9', 'https://test.com/feed', 'Ep 8->9', 'Desc', 'http://audio',
                'https://ep.img', 'https://ep.web', 123456, 3600, 2, '/tmp/a.mp3', 1, 42000, 555555
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO chapters (episodeId, title, startTimeMs, url, imageUrl)
            VALUES ('ep_8_9', 'Chapter 1', 15000, 'https://ch.url', 'https://ch.img')
            """.trimIndent()
        )
        db.close()

        db = helper.runMigrationsAndValidate(dbName, 9, true, DatabaseModule.MIGRATION_8_9)

        db.query("SELECT title, etag, lastModified FROM podcasts WHERE feedUrl = 'https://test.com/feed'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Show", cursor.getString(0))
            assertNull(cursor.getString(1))
            assertNull(cursor.getString(2))
        }
        db.query("SELECT title, lastPlayedPosition FROM episodes WHERE id = 'ep_8_9'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Ep 8->9", cursor.getString(0))
            assertEquals(42000L, cursor.getLong(1))
        }
        db.query("SELECT title, startTimeMs FROM chapters WHERE episodeId = 'ep_8_9'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Chapter 1", cursor.getString(0))
            assertEquals(15000L, cursor.getLong(1))
        }
        db.close()
    }
}
