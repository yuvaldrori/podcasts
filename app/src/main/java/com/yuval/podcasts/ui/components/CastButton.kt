package com.yuval.podcasts.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.view.ContextThemeWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.cast.MediaRouteButtonFactory
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.app.MediaRouteButton
import com.yuval.podcasts.R
import com.yuval.podcasts.data.Constants
import kotlinx.coroutines.delay

/**
 * A Composable that wraps the [MediaRouteButton] for Google Cast support.
 * 
 * Note: [MediaRouteButton] requires an AppCompat theme to properly calculate 
 * contrast for its icons. We use a [ContextThemeWrapper] to provide this.
 * Cast setup is deferred by [Constants.CAST_INIT_DELAY_MS] and uses Media3's
 * [MediaRouteButtonFactory] so [com.google.android.gms.cast.framework.CastContext]
 * is initialized asynchronously on a background executor once across the process.
 */
@OptIn(UnstableApi::class)
@Composable
fun CastButton(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    var mediaRouteButton by remember { mutableStateOf<MediaRouteButton?>(null) }

    LaunchedEffect(mediaRouteButton) {
        val button = mediaRouteButton ?: return@LaunchedEffect
        delay(Constants.CAST_INIT_DELAY_MS)
        try {
            val unused = MediaRouteButtonFactory.setUpMediaRouteButton(button.context, button)
        } catch (_: Exception) {
            // Ignore if Play Services / Cast framework is unavailable on the device
        }
    }

    AndroidView(
        factory = { ctx ->
            val themedContext = ContextThemeWrapper(ctx, R.style.Theme_Podcasts)
            MediaRouteButton(themedContext).also { button ->
                button.setOnClickListener {
                    if (ContextCompat.checkSelfPermission(
                            ctx,
                            Manifest.permission.ACCESS_LOCAL_NETWORK
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                    }
                    button.showDialog()
                }
                mediaRouteButton = button
            }
        },
        update = {
            // Proper tinting is typically handled via theme.
        },
        modifier = modifier.size(48.dp)
    )
}
