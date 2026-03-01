package com.yuval.podcasts.domain.usecase

import com.yuval.podcasts.data.opml.OpmlManager
import com.yuval.podcasts.data.repository.PodcastRepository
import kotlinx.coroutines.flow.first
import java.io.OutputStream
import javax.inject.Inject

class ExportOpmlUseCase @Inject constructor(
    private val opmlManager: OpmlManager,
    private val repository: PodcastRepository
) {
    suspend operator fun invoke(outputStream: OutputStream) {
        val podcasts = repository.allPodcasts.first()
        opmlManager.export(podcasts, outputStream)
    }
}
