package com.yuval.podcasts.ui.viewmodel

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.media.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val playerManager: PlayerManager,
    private val repository: PodcastRepository
) : ViewModel() {

    private val _dynamicColorScheme = MutableStateFlow<ColorScheme?>(null)
    val dynamicColorScheme: StateFlow<ColorScheme?> = _dynamicColorScheme.asStateFlow()

    fun updateColorScheme(isDarkTheme: Boolean) {
        viewModelScope.launch {
            playerManager.currentMediaId.collectLatest { id ->
                val imageUrl = if (id != null) {
                    repository.getEpisodeByIdFlow(id).first()?.imageUrl
                } else null

                if (imageUrl != null && imageUrl.startsWith("http")) {
                    generateColorScheme(imageUrl, isDarkTheme)
                } else {
                    _dynamicColorScheme.value = null
                }
            }
        }
    }

    private suspend fun generateColorScheme(imageUrl: String, isDarkTheme: Boolean) {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .allowHardware(false)
            .build()

        val result = loader.execute(request)
        if (result is SuccessResult) {
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return
            val palette = Palette.from(bitmap).generate()
            
            val swatch = if (isDarkTheme) {
                palette.darkVibrantSwatch ?: palette.vibrantSwatch
            } else {
                palette.lightVibrantSwatch ?: palette.vibrantSwatch
            }
            
            swatch?.let {
                val seedColor = Color(it.rgb)
                _dynamicColorScheme.value = if (isDarkTheme) {
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
    }
}
