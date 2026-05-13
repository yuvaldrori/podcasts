package com.yuval.podcasts.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yuval.podcasts.R
import com.yuval.podcasts.data.Constants

@Composable
fun PodcastCover(
    model: Any?,
    modifier: Modifier = Modifier,
    size: Dp = Constants.COVER_SIZE_LIST_DP.dp,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    contentDescription: String? = stringResource(R.string.podcast_cover)
) {
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(shape)
    )
}
