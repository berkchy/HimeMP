package com.berkchy.himemp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.berkchy.himemp.R
import com.berkchy.himemp.core.Track
import com.berkchy.himemp.util.ArtCache

@Composable
fun AlbumArt(
    track: Track?,
    modifier: Modifier = Modifier,
    corner: Dp = 8.dp,
) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = track?.id) {
        value = track?.let { ArtCache.cached(it) ?: ArtCache.load(it) }
    }
    val imageModifier = modifier
        .clip(RoundedCornerShape(corner))
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = track?.title,
            modifier = imageModifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Image(
            painter = painterResource(R.drawable.ic_placeholder_album),
            contentDescription = null,
            modifier = imageModifier,
            contentScale = ContentScale.Crop,
        )
    }
}
