package com.yuval.podcasts.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yuval.podcasts.BuildConfig
import com.yuval.podcasts.data.db.AppDatabase
import com.yuval.podcasts.data.db.dao.ChapterDao
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.PodcastDao
import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.utils.LogManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add episodeWebLink column
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `episodes_new` (`id` TEXT NOT NULL, `podcastFeedUrl` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `audioUrl` TEXT NOT NULL, `imageUrl` TEXT, `episodeWebLink` TEXT DEFAULT NULL, `pubDate` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `downloadStatus` INTEGER NOT NULL DEFAULT 0, `localFilePath` TEXT, `isPlayed` INTEGER NOT NULL DEFAULT 0, `lastPlayedPosition` INTEGER NOT NULL DEFAULT 0, `completedAt` INTEGER DEFAULT NULL, `localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO `episodes_new` (`id`, `podcastFeedUrl`, `title`, `description`, `audioUrl`, `imageUrl`, `pubDate`, `duration`, `downloadStatus`, `localFilePath`, `isPlayed`, `lastPlayedPosition`, `completedAt`) SELECT `id`, `podcastFeedUrl`, `title`, `description`, `audioUrl`, `imageUrl`, `pubDate`, `duration`, `downloadStatus`, `localFilePath`, `isPlayed`, `lastPlayedPosition`, `completedAt` FROM `episodes`"
            )
            db.execSQL("DROP TABLE `episodes`")
            db.execSQL("ALTER TABLE `episodes_new` RENAME TO `episodes`")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_episodes_id` ON `episodes` (`id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_isPlayed_pubDate` ON `episodes` (`isPlayed`, `pubDate`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_podcastFeedUrl` ON `episodes` (`podcastFeedUrl`)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        logManager: LogManager
    ): AppDatabase {
        val builder = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            Constants.DATABASE_NAME
        )
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
            }
        })
        .addMigrations(MIGRATION_5_6)
        .fallbackToDestructiveMigration(true)
        
        if (com.yuval.podcasts.BuildConfig.DEBUG) {
            builder.setQueryCallback({ sqlQuery, bindArgs ->
                val sql = sqlQuery.uppercase()
                val isSignificant = sql.startsWith("INSERT") || sql.startsWith("UPDATE") || 
                                   sql.startsWith("DELETE") || sql.startsWith("BEGIN") || 
                                   sql.startsWith("COMMIT") || sql.startsWith("ROLLBACK")
                
                if (isSignificant) {
                    logManager.i("DATABASE", "Query: $sqlQuery Args: $bindArgs")
                } else {
                    logManager.v("DATABASE", "Query: $sqlQuery Args: $bindArgs")
                }
            }, { it.run() })
        }
        
        return builder.build()
    }

    @Provides
    @Singleton
    fun providePodcastDao(database: AppDatabase): PodcastDao = database.podcastDao()

    @Provides
    @Singleton
    fun provideEpisodeDao(database: AppDatabase): EpisodeDao = database.episodeDao()

    @Provides
    @Singleton
    fun provideQueueDao(database: AppDatabase): QueueDao = database.queueDao()

    @Provides
    @Singleton
    fun provideChapterDao(database: AppDatabase): ChapterDao = database.chapterDao()
}
