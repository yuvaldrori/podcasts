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

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
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
        .addMigrations(MIGRATION_3_4)
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
    @Singleton
    fun provideOpmlManager(): com.yuval.podcasts.data.opml.OpmlManager = com.yuval.podcasts.data.opml.OpmlManager()
}