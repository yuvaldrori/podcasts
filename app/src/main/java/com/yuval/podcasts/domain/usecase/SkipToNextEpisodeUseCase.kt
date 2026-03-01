package com.yuval.podcasts.domain.usecase

import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.media.PlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SkipToNextEpisodeUseCase @Inject constructor(
    private val playerManager: PlayerManager
) {
    operator fun invoke() {
        playerManager.seekToNextMediaItem()
    }
}
