package com.yuval.podcasts.ui.viewmodel

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot
import com.yuval.podcasts.ui.utils.toColorScheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.media.PlayerManager
import com.yuval.podcasts.data.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val playerManager: PlayerManager,
    private val repository: PodcastRepository,
    private val imageLoader: ImageLoader
) : ViewModel() {

    private val _dynamicColorScheme = MutableStateFlow<ColorScheme?>(null)
    val dynamicColorScheme: StateFlow<ColorScheme?> = _dynamicColorScheme.asStateFlow()

    private val isDarkThemeFlow = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            combine(
                playerManager.currentMediaId.flatMapLatest { id ->
                    if (id == null) flowOf(null) else repository.getEpisodeByIdFlow(id)
                },
                isDarkThemeFlow
            ) { episode, isDark -> episode to isDark }
                .debounce(Constants.DYNAMIC_THEME_DEBOUNCE_MS)
                .collectLatest { (episode, isDark) ->
                    val finalImageUrl = episode?.imageUrl
                    
                    if (finalImageUrl != null && finalImageUrl.startsWith("http")) {
                        generateColorScheme(finalImageUrl, isDark)
                    } else {
                        _dynamicColorScheme.value = null
                    }
                }
        }
    }

    fun updateThemeMode(isDarkTheme: Boolean) {
        isDarkThemeFlow.value = isDarkTheme
    }

    private suspend fun generateColorScheme(imageUrl: String, isDarkTheme: Boolean) {
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .allowHardware(false)
            .build()

        val result = imageLoader.execute(request)
        if (result is SuccessResult) {
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return
            val palette = Palette.from(bitmap).generate()
            
            val swatch = if (isDarkTheme) {
                palette.darkVibrantSwatch ?: palette.vibrantSwatch
            } else {
                palette.lightVibrantSwatch ?: palette.vibrantSwatch
            }
            
            swatch?.let {
                val seedColorArgb = it.rgb
                val scheme = SchemeTonalSpot(Hct.fromInt(seedColorArgb), isDarkTheme, 0.0)
                _dynamicColorScheme.value = scheme.toColorScheme()
            }
        }
    }
}
