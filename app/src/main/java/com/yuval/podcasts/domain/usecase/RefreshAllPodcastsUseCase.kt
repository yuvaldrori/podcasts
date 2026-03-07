package com.yuval.podcasts.domain.usecase

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yuval.podcasts.work.SyncWorker
import javax.inject.Inject

class RefreshAllPodcastsUseCase @Inject constructor(
    private val workManager: WorkManager
) {
    operator fun invoke() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            "sync_all_podcasts",
            ExistingWorkPolicy.REPLACE,
            syncWorkRequest
        )
    }
}
