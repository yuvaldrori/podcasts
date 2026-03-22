package com.yuval.podcasts.domain.usecase

import com.yuval.podcasts.data.opml.OpmlManager
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject

class ImportOpmlUseCase @Inject constructor(
    private val opmlManager: OpmlManager,
    private val repository: PodcastRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(inputStream: InputStream) = withContext(ioDispatcher) {
        val urls = opmlManager.parse(inputStream)
        urls.map { url ->
            async {
                try {
                    repository.fetchAndStorePodcast(url)
                } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                    e.printStackTrace()
                }
            }
        }.awaitAll()
    }
}
