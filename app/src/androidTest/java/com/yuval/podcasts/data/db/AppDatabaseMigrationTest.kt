package com.yuval.podcasts.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuval.podcasts.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrate5To6() {
        // Create database at version 5
        var db = helper.createDatabase(TEST_DB, 5)
        
        // Insert a dummy episode at version 5
        db.execSQL("""
            INSERT INTO episodes (id, podcastFeedUrl, title, description, audioUrl, pubDate, duration, downloadStatus, isPlayed, lastPlayedPosition)
            VALUES ('test_episode_1', 'https://test.com/feed', 'Test Episode', 'Test Description', 'http://audio', 123456789, 3600, 0, 0, 0)
        """.trimIndent())
        
        // Prepare to close before migrating
        db.close()

        // Run migration to version 6
        db = helper.runMigrationsAndValidate(TEST_DB, 6, true, DatabaseModule.MIGRATION_5_6)
        
        // Query to see if the data survived the destructive change
        val cursor = db.query("SELECT * FROM episodes WHERE id = 'test_episode_1'")
        assertTrue("Data did not survive the migration from 5 to 6", cursor.moveToFirst())
        assertEquals("Test Episode", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        cursor.close()
    }
}
