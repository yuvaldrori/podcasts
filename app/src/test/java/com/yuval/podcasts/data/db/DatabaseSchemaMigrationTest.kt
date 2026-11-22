package com.yuval.podcasts.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuval.podcasts.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseSchemaMigrationTest {

    @Test
    fun migrate8To9_createsCompositePrimaryKeyAddsHeadersDropsChapterFkAndPreservesData() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Authentic v8 schema matching app/schemas/.../8.json
                    db.execSQL(
                        "CREATE TABLE `podcasts` (`feedUrl` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `imageUrl` TEXT NOT NULL, `website` TEXT NOT NULL, PRIMARY KEY(`feedUrl`))"
                    )
                    db.execSQL(
                        "CREATE TABLE `episodes` (`id` TEXT NOT NULL, `podcastFeedUrl` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `audioUrl` TEXT NOT NULL, `imageUrl` TEXT, `episodeWebLink` TEXT DEFAULT NULL, `pubDate` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `downloadStatus` INTEGER NOT NULL DEFAULT 0, `localFilePath` TEXT, `isPlayed` INTEGER NOT NULL DEFAULT 0, `lastPlayedPosition` INTEGER NOT NULL DEFAULT 0, `completedAt` INTEGER DEFAULT NULL, `localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"
                    )
                    db.execSQL("CREATE UNIQUE INDEX `index_episodes_id` ON `episodes` (`id`)")
                    db.execSQL("CREATE INDEX `index_episodes_isPlayed_pubDate` ON `episodes` (`isPlayed`, `pubDate`)")
                    db.execSQL("CREATE INDEX `index_episodes_podcastFeedUrl` ON `episodes` (`podcastFeedUrl`)")
                    db.execSQL(
                        "CREATE TABLE `chapters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `episodeId` TEXT NOT NULL, `title` TEXT NOT NULL, `startTimeMs` INTEGER NOT NULL, `url` TEXT, `imageUrl` TEXT, FOREIGN KEY(`episodeId`) REFERENCES `episodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                    )
                    db.execSQL("CREATE INDEX `index_chapters_episodeId` ON `chapters` (`episodeId`)")

                    // Seed v8 rows to verify data preservation across table rebuilds
                    db.execSQL(
                        "INSERT INTO `podcasts` (`feedUrl`, `title`, `description`, `imageUrl`, `website`) VALUES ('https://feed.test/rss', 'Test Show', 'Desc', 'https://img', 'https://web')"
                    )
                    db.execSQL(
                        "INSERT INTO `episodes` (`id`, `podcastFeedUrl`, `title`, `description`, `audioUrl`, `imageUrl`, `episodeWebLink`, `pubDate`, `duration`, `downloadStatus`, `localFilePath`, `isPlayed`, `lastPlayedPosition`, `completedAt`) VALUES ('ep-1', 'https://feed.test/rss', 'Episode 1', 'Ep Desc', 'https://audio.mp3', 'https://ep.img', 'https://ep.web', 123456, 3600, 2, '/tmp/ep1.mp3', 1, 45000, 999999)"
                    )
                    db.execSQL(
                        "INSERT INTO `chapters` (`episodeId`, `title`, `startTimeMs`, `url`, `imageUrl`) VALUES ('ep-1', 'Intro', 5000, 'https://ch.url', 'https://ch.img')"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        helper.writableDatabase.use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")

            // Execute MIGRATION_8_9
            DatabaseModule.MIGRATION_8_9.migrate(db)

            // 1. Verify podcasts table has etag and lastModified columns and preserved row with NULL defaults
            val podcastColumns = mutableMapOf<String, String>()
            db.query("PRAGMA table_info(podcasts)").use { cursor ->
                while (cursor.moveToNext()) {
                    podcastColumns[cursor.getString(cursor.getColumnIndexOrThrow("name"))] =
                        cursor.getString(cursor.getColumnIndexOrThrow("type"))
                }
            }
            assertNotNull("podcasts table must contain etag column", podcastColumns["etag"])
            assertNotNull("podcasts table must contain lastModified column", podcastColumns["lastModified"])

            db.query("SELECT title, etag, lastModified FROM podcasts WHERE feedUrl = 'https://feed.test/rss'").use { cursor ->
                assertTrue("Existing podcast row must survive migration", cursor.moveToFirst())
                assertEquals("Test Show", cursor.getString(0))
                assertNull("Existing podcast etag must default to NULL", cursor.getString(1))
                assertNull("Existing podcast lastModified must default to NULL", cursor.getString(2))
            }

            // 2. Verify episodes table composite PK, dropped localId, recreated indexes, and preserved row
            val episodePkPositions = mutableMapOf<String, Int>()
            db.query("PRAGMA table_info(episodes)").use { cursor ->
                while (cursor.moveToNext()) {
                    episodePkPositions[cursor.getString(cursor.getColumnIndexOrThrow("name"))] =
                        cursor.getInt(cursor.getColumnIndexOrThrow("pk"))
                }
            }
            assertEquals("id column must be primary key position 1", 1, episodePkPositions["id"])
            assertEquals("podcastFeedUrl column must be primary key position 2", 2, episodePkPositions["podcastFeedUrl"])
            assertFalse("localId column must be removed in v9", episodePkPositions.containsKey("localId"))

            val episodeIndexes = mutableSetOf<String>()
            db.query("PRAGMA index_list(episodes)").use { cursor ->
                while (cursor.moveToNext()) {
                    episodeIndexes.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
            assertTrue("index_episodes_isPlayed_pubDate must exist", episodeIndexes.contains("index_episodes_isPlayed_pubDate"))
            assertTrue("index_episodes_podcastFeedUrl must exist", episodeIndexes.contains("index_episodes_podcastFeedUrl"))
            assertFalse("legacy index_episodes_id must be removed", episodeIndexes.contains("index_episodes_id"))

            db.query("SELECT title, episodeWebLink, downloadStatus, localFilePath, isPlayed, lastPlayedPosition, completedAt FROM episodes WHERE id = 'ep-1'").use { cursor ->
                assertTrue("Existing episode row must survive migration", cursor.moveToFirst())
                assertEquals("Episode 1", cursor.getString(0))
                assertEquals("https://ep.web", cursor.getString(1))
                assertEquals(2, cursor.getInt(2))
                assertEquals("/tmp/ep1.mp3", cursor.getString(3))
                assertEquals(1, cursor.getInt(4))
                assertEquals(45000L, cursor.getLong(5))
                assertEquals(999999L, cursor.getLong(6))
            }

            // 3. Verify chapters FK was dropped, index recreated, and chapter data survived
            db.query("PRAGMA foreign_key_list(chapters)").use { cursor ->
                assertEquals("chapters table must have no foreign keys in v9", 0, cursor.count)
            }
            val chapterIndexes = mutableSetOf<String>()
            db.query("PRAGMA index_list(chapters)").use { cursor ->
                while (cursor.moveToNext()) {
                    chapterIndexes.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
            assertTrue("index_chapters_episodeId must exist", chapterIndexes.contains("index_chapters_episodeId"))

            db.query("SELECT episodeId, title, startTimeMs, url, imageUrl FROM chapters WHERE episodeId = 'ep-1'").use { cursor ->
                assertTrue("Existing chapter row must survive migration", cursor.moveToFirst())
                assertEquals("ep-1", cursor.getString(0))
                assertEquals("Intro", cursor.getString(1))
                assertEquals(5000L, cursor.getLong(2))
                assertEquals("https://ch.url", cursor.getString(3))
                assertEquals("https://ch.img", cursor.getString(4))
            }
        }
    }
}
