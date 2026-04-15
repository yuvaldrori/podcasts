package com.yuval.podcasts.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yuval.podcasts.data.db.AppDatabase
import com.yuval.podcasts.data.db.dao.PodcastDao
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.QueueDao
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "podcasts_db"
        )
        .addMigrations(MIGRATION_5_6)
        .fallbackToDestructiveMigration(true)
        .build()
    }

    @Provides
    fun providePodcastDao(database: AppDatabase): PodcastDao = database.podcastDao()

    @Provides
    fun provideEpisodeDao(database: AppDatabase): EpisodeDao = database.episodeDao()

    @Provides
    fun provideQueueDao(database: AppDatabase): QueueDao = database.queueDao()

    @Provides
    fun provideChapterDao(database: AppDatabase): com.yuval.podcasts.data.db.dao.ChapterDao = database.chapterDao()

    @Provides
    @Singleton
    fun provideOpmlManager(): com.yuval.podcasts.data.opml.OpmlManager = com.yuval.podcasts.data.opml.OpmlManager()
}