package com.yuval.podcasts

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yuval.podcasts.ui.MainScreen
import com.yuval.podcasts.ui.theme.PodcastsTheme
import com.yuval.podcasts.work.CleanupWorker
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // If granted, Media3 will automatically show the playback notification.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Modern Android 15+ edge-to-edge UI (supported natively in Compose Scaffold)
        enableEdgeToEdge()

        // Unconditionally request POST_NOTIFICATIONS since minSdk is 36 (Android 16)
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

        // Queue background cleanup task
        val cleanupRequest = OneTimeWorkRequestBuilder<CleanupWorker>().build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "cleanup_orphaned_files",
            ExistingWorkPolicy.KEEP,
            cleanupRequest
        )

        setContent {
            PodcastsTheme {
                MainScreen()
            }
        }
    }
}
