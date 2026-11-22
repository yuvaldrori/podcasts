package com.yuval.podcasts.media

import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.utils.LogManager

/**
 * Surgically reconciles a Media3 [Player]'s playlist with the latest database queue without
 * interrupting the currently playing item.
 */
@OptIn(UnstableApi::class)
internal object QueuePlaylistSynchronizer {

    fun sync(
        currentPlayer: Player,
        episodes: List<Episode>,
        mediaItems: List<MediaItem>,
        logManager: LogManager? = null
    ) {
        val mediaItemsById = mediaItems.associateBy { it.mediaId }
        val alignedEpisodes = episodes.filter { it.id in mediaItemsById }

        if (alignedEpisodes.isEmpty()) {
            if (currentPlayer.mediaItemCount > 0) {
                currentPlayer.stop()
                currentPlayer.clearMediaItems()
            }
            return
        }

        val alignedMediaItems = alignedEpisodes.map { mediaItemsById.getValue(it.id) }
        val currentMediaId = currentPlayer.currentMediaItem?.mediaId

        if (currentMediaId == null) {
            currentPlayer.setMediaItems(alignedMediaItems)
            return
        }

        val newIds = alignedEpisodes.map { it.id }
        val newIdSet = newIds.toHashSet()

        if (currentMediaId !in newIdSet) {
            handleCurrentItemRemoved(currentPlayer, currentMediaId, alignedEpisodes, alignedMediaItems, newIdSet, logManager)
            return
        }

        val currentIds = (0 until currentPlayer.mediaItemCount).map { currentPlayer.getMediaItemAt(it).mediaId }
        if (currentIds != newIds) {
            reconcileOrder(currentPlayer, currentMediaId, alignedEpisodes, mediaItemsById, newIdSet)
        }

        updateChangedMetadata(currentPlayer, alignedEpisodes, mediaItemsById)
    }

    private fun handleCurrentItemRemoved(
        currentPlayer: Player,
        currentMediaId: String,
        alignedEpisodes: List<Episode>,
        alignedMediaItems: List<MediaItem>,
        newIdSet: Set<String>,
        logManager: LogManager?
    ) {
        val hasQueueItems = (0 until currentPlayer.mediaItemCount).any {
            currentPlayer.getMediaItemAt(it).mediaId in newIdSet
        }
        if (!hasQueueItems) return

        val currentIndex = currentPlayer.currentMediaItemIndex
        val nextEpisodeId = (currentIndex + 1 until currentPlayer.mediaItemCount)
            .map { currentPlayer.getMediaItemAt(it).mediaId }
            .firstOrNull { it in newIdSet }

        if (nextEpisodeId != null) {
            val nextIndexInNew = alignedEpisodes.indexOfFirst { it.id == nextEpisodeId }
            if (nextIndexInNew != -1) {
                logManager?.i("PlaybackService", "Current item $currentMediaId removed from queue, transitioning to next item $nextEpisodeId")
                currentPlayer.setMediaItems(alignedMediaItems, nextIndexInNew, 0L)
                currentPlayer.prepare()
                currentPlayer.play()
                return
            }
        }

        logManager?.i("PlaybackService", "Current item $currentMediaId removed from queue and no next item found, stopping player")
        currentPlayer.stop()
        currentPlayer.clearMediaItems()
    }

    private fun reconcileOrder(
        currentPlayer: Player,
        currentMediaId: String,
        alignedEpisodes: List<Episode>,
        mediaItemsById: Map<String, MediaItem>,
        newIdSet: Set<String>
    ) {
        var i = 0
        while (i < currentPlayer.mediaItemCount) {
            val id = currentPlayer.getMediaItemAt(i).mediaId
            if (id != currentMediaId && id !in newIdSet) {
                currentPlayer.removeMediaItem(i)
            } else {
                i++
            }
        }

        val existingIdsInPlayer = (0 until currentPlayer.mediaItemCount)
            .mapTo(HashSet()) { currentPlayer.getMediaItemAt(it).mediaId }

        for (episode in alignedEpisodes) {
            if (episode.id !in existingIdsInPlayer) {
                mediaItemsById[episode.id]?.let { currentPlayer.addMediaItem(it) }
            }
        }

        for (index in alignedEpisodes.indices) {
            if (index >= currentPlayer.mediaItemCount) break
            val expectedId = alignedEpisodes[index].id
            if (currentPlayer.getMediaItemAt(index).mediaId != expectedId) {
                for (searchIndex in index + 1 until currentPlayer.mediaItemCount) {
                    if (currentPlayer.getMediaItemAt(searchIndex).mediaId == expectedId) {
                        currentPlayer.moveMediaItem(searchIndex, index)
                        break
                    }
                }
            }
        }
    }

    private fun updateChangedMetadata(
        currentPlayer: Player,
        alignedEpisodes: List<Episode>,
        mediaItemsById: Map<String, MediaItem>
    ) {
        @Suppress("DEPRECATION")
        val isCasting = (currentPlayer as? CastPlayer)?.isCastSessionAvailable == true

        alignedEpisodes.forEachIndexed { index, episode ->
            if (index >= currentPlayer.mediaItemCount || currentPlayer.currentMediaItemIndex == index) return@forEachIndexed
            val itemInPlayer = currentPlayer.getMediaItemAt(index)
            if (itemInPlayer.mediaId != episode.id) return@forEachIndexed
            val updatedItem = mediaItemsById[episode.id] ?: return@forEachIndexed

            if (needsReplacement(itemInPlayer, updatedItem, isCasting)) {
                currentPlayer.replaceMediaItem(index, updatedItem)
            }
        }
    }

    private fun needsReplacement(
        itemInPlayer: MediaItem,
        updatedItem: MediaItem,
        isCasting: Boolean
    ): Boolean {
        val titleChanged = updatedItem.mediaMetadata.title != null &&
            updatedItem.mediaMetadata.title != itemInPlayer.mediaMetadata.title

        if (isCasting) {
            val playerRemoteUrl = itemInPlayer.mediaMetadata.extras?.getString(Constants.EXTRA_REMOTE_AUDIO_URL)
                ?: itemInPlayer.localConfiguration?.uri?.toString()
            val updatedRemoteUrl = updatedItem.mediaMetadata.extras?.getString(Constants.EXTRA_REMOTE_AUDIO_URL)
                ?: updatedItem.localConfiguration?.uri?.toString()
            return titleChanged || (updatedRemoteUrl != null && updatedRemoteUrl != playerRemoteUrl)
        }

        val uriChanged = updatedItem.localConfiguration?.uri != itemInPlayer.localConfiguration?.uri
        val artistChanged = updatedItem.mediaMetadata.artist != null &&
            updatedItem.mediaMetadata.artist != itemInPlayer.mediaMetadata.artist
        val albumTitleChanged = updatedItem.mediaMetadata.albumTitle != null &&
            updatedItem.mediaMetadata.albumTitle != itemInPlayer.mediaMetadata.albumTitle
        val artworkChanged = updatedItem.mediaMetadata.artworkUri != itemInPlayer.mediaMetadata.artworkUri
        return uriChanged || titleChanged || artistChanged || albumTitleChanged || artworkChanged
    }
}
