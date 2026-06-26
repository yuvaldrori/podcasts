package com.yuval.podcasts.domain.usecase

import android.content.Context
import android.net.Uri
import com.yuval.podcasts.R
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.entity.DownloadStatus
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.repository.LocalMediaDataSource
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.utils.LogManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ImportLocalFileUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: PodcastRepository,
    private val localMediaDataSource: LocalMediaDataSource,
    private val logManager: LogManager
) {
    suspend operator fun invoke(uri: Uri): Result<Unit> {
        return try {
            val localFeedUrl = Constants.LOCAL_PODCAST_FEED_URL

            // 1. Ensure the Local Podcast feed exists
            if (repository.getPodcast(localFeedUrl) == null) {
                repository.insertPodcast(
                    Podcast(
                        feedUrl = localFeedUrl,
                        title = context.getString(R.string.local_files_title),
                        description = context.getString(R.string.local_files_desc),
                        imageUrl = "", 
                        website = ""
                    )
                )
            }

            // 2. Delegate file IO and extraction to LocalMediaDataSource
            val metadataResult = localMediaDataSource.copyAndExtract(uri)
            val metadata = metadataResult.getOrThrow()

            // 3. Insert Episode
            val episodeId = "local_${System.currentTimeMillis()}_${metadata.destFile.name.hashCode()}"
            try {
                repository.insertEpisodes(listOf(
                    Episode(
                        id = episodeId,
                        podcastFeedUrl = localFeedUrl,
                        title = metadata.title,
                        description = metadata.description,
                        audioUrl = metadata.destFile.absolutePath, 
                        imageUrl = null,
                        episodeWebLink = null, 
                        pubDate = System.currentTimeMillis(),
                        duration = metadata.durationSecs,
                        downloadStatus = DownloadStatus.DOWNLOADED.value,
                        localFilePath = metadata.destFile.absolutePath,
                        isPlayed = false,
                        lastPlayedPosition = 0L,
                        completedAt = null,
                        localId = 0L
                    )
                ))
            } catch (e: Exception) {
                if (metadata.destFile.exists()) {
                    metadata.destFile.delete()
                }
                throw e
            }

            Result.success(Unit)
        } catch (e: Exception) {
            logManager.e("ImportLocalFileUseCase", "Failed to add local file", mapOf("error" to e.message.toString()))
            Result.failure(e)
        }
    }
}
