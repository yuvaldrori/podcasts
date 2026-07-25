package com.yuval.podcasts.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.utils.LogManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class HardRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    repository: PodcastRepository,
    logManager: LogManager
) : BaseSyncWorker(
    appContext = appContext,
    workerParams = workerParams,
    repository = repository,
    logManager = logManager,
    forceRefresh = true,
    notificationId = Constants.NOTIFICATION_ID_HARD_REFRESH,
    workerTag = "HardRefreshWorker"
)
