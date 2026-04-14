package com.yuval.podcasts

import android.os.Bundle
import androidx.tracing.trace
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yuval.podcasts.ui.theme.PodcastsTheme
import com.yuval.podcasts.ui.MainScreen
import com.yuval.podcasts.ui.viewmodel.PlayerViewModel
import com.yuval.podcasts.ui.viewmodel.ThemeViewModel
import com.yuval.podcasts.work.CleanupWorker
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        trace("MainActivity.onCreate") {
            val workManager = WorkManager.getInstance(this)
            val cleanupRequest = OneTimeWorkRequestBuilder<CleanupWorker>().build()
            workManager.enqueueUniqueWork(
                "cleanup_orphaned_files",
                ExistingWorkPolicy.KEEP,
                cleanupRequest
            )

            setContent {
                val themeViewModel: ThemeViewModel = hiltViewModel()
                val dynamicColorScheme by themeViewModel.dynamicColorScheme.collectAsStateWithLifecycle()
                val darkTheme = isSystemInDarkTheme()

                LaunchedEffect(darkTheme) {
                    themeViewModel.updateColorScheme(darkTheme)
                }

                PodcastsTheme(colorSchemeOverride = dynamicColorScheme) {
                    MainScreen()
                }
            }
        }
    }
}
