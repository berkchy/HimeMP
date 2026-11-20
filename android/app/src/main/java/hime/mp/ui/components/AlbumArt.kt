package hime.mp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hime.mp.R
import hime.mp.core.Track
import hime.mp.util.ArtCache

@Composable
fun AlbumArt(
    track: Track?,
    modifier: Modifier = Modifier,
    corner: Dp = 8.dp,
) {
    val art by produceState<ImageBitmap?>(initialValue = null, key1 = track?.id) {
        value = track?.let { ArtCache.cached(it) ?: ArtCache.load(it) }?.asImageBitmap()
    }
    val image = art
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = track?.title,
            modifier = modifier.clip(RoundedCornerShape(corner)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Image(
            painter = painterResource(R.drawable.ic_placeholder_album),
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(corner)),
            contentScale = ContentScale.Crop,
        )
    }
}
