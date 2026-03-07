package com.yuval.podcasts.domain.usecase

import com.yuval.podcasts.data.repository.PodcastRepository
import javax.inject.Inject

class RefreshPodcastUseCase @Inject constructor(
    private val repository: PodcastRepository
) {
    suspend operator fun invoke(feedUrl: String) = repository.fetchAndStorePodcast(feedUrl)
}
