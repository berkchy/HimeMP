package hime.mp.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hime.mp.core.Track
import hime.mp.data.PlaybackController
import hime.mp.ui.theme.AlbumThemeController
import hime.mp.ui.theme.LocalHimeMotion
import hime.mp.util.ArtCache
import kotlinx.coroutines.launch
import kotlin.math.abs

private fun miniAccent(raw: Color): Color = lerp(raw, Color.White, 0.42f)

private fun miniOnAccent(accent: Color): Color =
    if (accent.luminance() > 0.55f) Color(0xFF1B1418) else Color.White

@Composable
fun MiniPlayer(onExpand: () -> Unit, progressStyle: String = "pill") {
    val state by PlaybackController.state.collectAsState()
    val track by PlaybackController.currentTrack.collectAsState()
    val motion = LocalHimeMotion.current
    val palette by AlbumThemeController.palette.collectAsState()
    var dragX by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    if (track == null) return

    val rawAccent = palette?.primary?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val accent by animateColorAsState(
        targetValue = miniAccent(rawAccent),
        animationSpec = tween(motion.standard),
        label = "miniAccent",
    )
    val container by animateColorAsState(
        targetValue = lerp(MaterialTheme.colorScheme.surfaceContainerHigh, rawAccent, 0.16f),
        animationSpec = tween(motion.standard),
        label = "miniContainer",
    )
    val fraction = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        shape = CircleShape,
        color = container,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onExpand)
            .pointerInput(Unit) {
                var totalX = 0f
                var totalY = 0f
                var expanded = false
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                        expanded = false
                        dragX = 0f
                    },
                    onDrag = { change, amount ->
                        totalX += amount.x
                        totalY += amount.y
                        if (abs(totalX) > abs(totalY)) {
                            dragX = totalX
                            change.consume()
                        } else if (totalY < -12f) {
                            change.consume()
                            if (!expanded) {
                                expanded = true
                                onExpand()
                            }
                        }
                    },
                    onDragEnd = {
                        if (abs(totalX) > 80f && abs(totalX) > abs(totalY)) {
                            if (totalX > 0) PlaybackController.previous()
                            else PlaybackController.next()
                        }
                        scope.launch {
                            animate(
                                initialValue = dragX,
                                targetValue = 0f,
                                animationSpec = tween(180),
                            ) { value, _ -> dragX = value }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            animate(
                                initialValue = dragX,
                                targetValue = 0f,
                                animationSpec = tween(180),
                            ) { value, _ -> dragX = value }
                        }
                    },
                )
            },
    ) {
        Column {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (progressStyle == "thin") 2.dp else 3.dp),
                trackColor = Color.White.copy(alpha = 0.20f),
                color = accent,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShapedArtworkWithProgress(
                    track = track,
                    fraction = fraction,
                    accent = accent,
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer { translationX = dragX * 0.25f },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                ) {
                    AnimatedContent(
                        targetState = track?.id,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(motion.quick)) togetherWith
                                fadeOut(animationSpec = tween(motion.quick))
                        },
                        label = "miniTrack",
                    ) {
                        Column {
                            Text(
                                track?.title.orEmpty(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                            )
                            Text(
                                track?.artist.orEmpty(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.78f),
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(onClick = { PlaybackController.previous() }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(18.dp),
                            tint = Color.White.copy(alpha = 0.88f),
                        )
                    }
                    FilledIconButton(
                        onClick = { PlaybackController.togglePlay() },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = accent,
                            contentColor = miniOnAccent(accent),
                        ),
                    ) {
                        AnimatedContent(
                            targetState = state.playing,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(motion.quick)) togetherWith
                                    fadeOut(animationSpec = tween(motion.quick))
                            },
                            label = "miniPlayPause",
                        ) { playing ->
                            Icon(
                                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    IconButton(onClick = { PlaybackController.next() }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(18.dp),
                            tint = Color.White.copy(alpha = 0.88f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShapedArtworkWithProgress(
    track: Track?,
    fraction: Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = track?.id) {
        value = track?.let { ArtCache.cached(it) ?: ArtCache.load(it) }?.asImageBitmap()
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Color.White.copy(alpha = 0.24f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
        }
        val shape = RoundedCornerShape(18.dp, 6.dp, 18.dp, 6.dp)
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = track?.title,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
                    .clip(shape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(5.dp)
                    .clip(shape)
                    .background(accent.copy(alpha = 0.28f)),
            )
        }
    }
}
