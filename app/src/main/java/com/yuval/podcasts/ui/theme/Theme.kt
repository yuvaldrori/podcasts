package com.yuval.podcasts.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme()
private val LightColorScheme = lightColorScheme()

@Composable
fun PodcastsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorSchemeOverride: ColorScheme? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = colorSchemeOverride ?: if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
