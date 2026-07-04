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
import com.yuval.podcasts.ui.theme.PodcastsTheme
import com.yuval.podcasts.ui.MainScreen
import com.yuval.podcasts.ui.viewmodel.PlayerViewModel
import com.yuval.podcasts.ui.viewmodel.ThemeViewModel
import android.os.Build
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.yuval.podcasts.media.PlayerManager

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var playerManager: PlayerManager

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        enableEdgeToEdge()
        
        window.isNavigationBarContrastEnforced = false
        
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val dynamicColorScheme by themeViewModel.dynamicColorScheme.collectAsStateWithLifecycle()
            val darkTheme = isSystemInDarkTheme()

            LaunchedEffect(darkTheme) {
                themeViewModel.updateThemeMode(darkTheme)
            }

            PodcastsTheme(colorSchemeOverride = dynamicColorScheme) {
                MainScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
