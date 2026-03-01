package com.yuval.podcasts.domain.usecase

import com.yuval.podcasts.data.opml.OpmlManager
import com.yuval.podcasts.data.repository.PodcastRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.InputStream
import javax.inject.Inject

class ImportOpmlUseCase @Inject constructor(
    private val opmlManager: OpmlManager,
    private val repository: PodcastRepository
) {
    suspend operator fun invoke(inputStream: InputStream) = coroutineScope {
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
