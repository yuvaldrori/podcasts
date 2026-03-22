package com.yuval.podcasts.domain.usecase

import com.yuval.podcasts.data.opml.OpmlManager
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.inject.Inject

class ExportOpmlUseCase @Inject constructor(
    private val opmlManager: OpmlManager,
    private val repository: PodcastRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(outputStream: OutputStream) = withContext(ioDispatcher) {
        val podcasts = repository.allPodcasts.first()
        opmlManager.export(podcasts, outputStream)
    }
}
