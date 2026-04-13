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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yuval.podcasts.ui.theme.PodcastsTheme
import com.yuval.podcasts.ui.MainScreen
import com.yuval.podcasts.ui.viewmodel.PlayerViewModel
import com.yuval.podcasts.work.CleanupWorker
import dagger.hilt.android.AndroidEntryPoint
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

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
                val playerViewModel: PlayerViewModel = hiltViewModel()
                val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
                val darkTheme = isSystemInDarkTheme()
                val context = LocalContext.current
                
                var dynamicColorScheme by remember { mutableStateOf<androidx.compose.material3.ColorScheme?>(null) }

                LaunchedEffect(playerState.currentEpisode?.imageUrl) {
                    val imageUrl = playerState.currentEpisode?.imageUrl
                    if (imageUrl != null && imageUrl.startsWith("http")) {
                        val loader = ImageLoader(context)
                        val request = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .allowHardware(false)
                            .build()

                        val result = loader.execute(request)
                        if (result is SuccessResult) {
                            val bitmap = (result.drawable as android.graphics.drawable.BitmapDrawable).bitmap
                            val palette = Palette.from(bitmap).generate()
                            
                            val swatch = if (darkTheme) palette.darkVibrantSwatch ?: palette.vibrantSwatch else palette.lightVibrantSwatch ?: palette.vibrantSwatch
                            
                            swatch?.let {
                                val seedColor = Color(it.rgb)
                                // In a real app with API 31+, we'd use dynamicColorScheme(context)
                                // For cross-version support with a custom seed, we'll keep the simplified manual logic
                                // but slightly improve the secondary/tertiary balance.
                                dynamicColorScheme = if (darkTheme) {
                                    darkColorScheme(
                                        primary = seedColor,
                                        secondary = seedColor.copy(alpha = 0.7f),
                                        tertiary = seedColor.copy(alpha = 0.5f)
                                    )
                                } else {
                                    lightColorScheme(
                                        primary = seedColor,
                                        secondary = seedColor.copy(alpha = 0.7f),
                                        tertiary = seedColor.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    } else {
                        dynamicColorScheme = null
                    }
                }

                PodcastsTheme(colorSchemeOverride = dynamicColorScheme) {
                    MainScreen()
                }
            }
        }
    }
}
