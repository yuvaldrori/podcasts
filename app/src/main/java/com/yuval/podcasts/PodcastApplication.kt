package com.yuval.podcasts

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.appfunctions.service.AppFunctionConfiguration
import com.yuval.podcasts.appfunctions.PodcastAppFunctions
import com.yuval.podcasts.work.CleanupWorker
import com.yuval.podcasts.work.HardRefreshWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

import com.yuval.podcasts.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class PodcastApplication : Application(), Configuration.Provider, AppFunctionConfiguration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var podcastAppFunctions: PodcastAppFunctions

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val appFunctionConfiguration: AppFunctionConfiguration by lazy {
        AppFunctionConfiguration.Builder()
            .addEnclosingClassFactory(PodcastAppFunctions::class.java) { podcastAppFunctions }
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            scheduleCleanup()
            scheduleHardRefresh()
        }
    }

    private fun scheduleCleanup() {
        val constraints = Constraints.Builder()
            .setRequiresDeviceIdle(true)
            .setRequiresBatteryNotLow(true)
            .build()

        val cleanupRequest = PeriodicWorkRequestBuilder<CleanupWorker>(
            com.yuval.podcasts.data.Constants.PERIODIC_CLEANUP_INTERVAL_HOURS, 
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            com.yuval.podcasts.data.Constants.WORK_NAME_CLEANUP,
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest
        )
    }

    private fun scheduleHardRefresh() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val hardRefreshRequest = PeriodicWorkRequestBuilder<HardRefreshWorker>(
            com.yuval.podcasts.data.Constants.PERIODIC_HARD_REFRESH_INTERVAL_DAYS,
            TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            com.yuval.podcasts.data.Constants.WORK_NAME_HARD_REFRESH_ALL,
            ExistingPeriodicWorkPolicy.KEEP,
            hardRefreshRequest
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
