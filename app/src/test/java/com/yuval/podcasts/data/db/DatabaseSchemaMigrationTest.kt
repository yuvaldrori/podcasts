package com.yuval.podcasts.data.db

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuval.podcasts.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseSchemaMigrationTest {

    @Test
    fun migrate8To9_createsCompositePrimaryKeyAndAddsPodcastHeaderColumns() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE `podcasts` (`feedUrl` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `imageUrl` TEXT NOT NULL, `website` TEXT NOT NULL, PRIMARY KEY(`feedUrl`))"
                    )
                    db.execSQL(
                        "CREATE TABLE `episodes` (`id` TEXT NOT NULL, `podcastFeedUrl` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `audioUrl` TEXT NOT NULL, `imageUrl` TEXT, `episodeWebLink` TEXT DEFAULT NULL, `pubDate` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `downloadStatus` INTEGER NOT NULL DEFAULT 0, `localFilePath` TEXT, `isPlayed` INTEGER NOT NULL DEFAULT 0, `lastPlayedPosition` INTEGER NOT NULL DEFAULT 0, `completedAt` INTEGER DEFAULT NULL, PRIMARY KEY(`id`, `podcastFeedUrl`))"
                    )
                }

                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        // Execute MIGRATION_8_9
        DatabaseModule.MIGRATION_8_9.migrate(db)

        // Verify podcasts table has etag and lastModified
        val podcastColumns = mutableMapOf<String, String>()
        db.query("PRAGMA table_info(podcasts)").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val type = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                podcastColumns[name] = type
            }
        }
        assertNotNull("podcasts table must contain etag column after migration", podcastColumns["etag"])
        assertNotNull("podcasts table must contain lastModified column after migration", podcastColumns["lastModified"])

        // Verify episodes table composite primary key (id = pk 1, podcastFeedUrl = pk 2)
        val episodePkPositions = mutableMapOf<String, Int>()
        db.query("PRAGMA table_info(episodes)").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val pk = cursor.getInt(cursor.getColumnIndexOrThrow("pk"))
                episodePkPositions[name] = pk
            }
        }
        assertEquals("id column must be primary key position 1", 1, episodePkPositions["id"])
        assertEquals("podcastFeedUrl column must be primary key position 2", 2, episodePkPositions["podcastFeedUrl"])

        db.close()
    }
}
