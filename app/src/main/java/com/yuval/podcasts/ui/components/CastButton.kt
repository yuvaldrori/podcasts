package com.yuval.podcasts.ui.components

import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.yuval.podcasts.R

/**
 * A Composable that wraps the [MediaRouteButton] for Google Cast support.
 * 
 * Note: [MediaRouteButton] requires an AppCompat theme to properly calculate 
 * contrast for its icons. We use a [ContextThemeWrapper] to provide this.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            val themedContext = ContextThemeWrapper(context, R.style.Theme_Podcasts)
            MediaRouteButton(themedContext).apply {
                CastButtonFactory.setUpMediaRouteButton(themedContext, this)
            }
        },
        modifier = modifier.size(48.dp)
    )
}
