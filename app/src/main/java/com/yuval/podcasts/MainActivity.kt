package com.yuval.podcasts

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tracing.trace
import com.yuval.podcasts.ui.MainScreen
import com.yuval.podcasts.ui.theme.PodcastsTheme
import com.yuval.podcasts.ui.viewmodel.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        trace("MainActivity_onCreate") {
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
    }
}
