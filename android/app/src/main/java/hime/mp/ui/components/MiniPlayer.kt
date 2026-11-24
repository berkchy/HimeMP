package hime.mp.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hime.mp.data.PlaybackController
import hime.mp.ui.theme.AlbumThemeController
import hime.mp.ui.theme.LocalHimeMotion
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun MiniPlayer(onExpand: () -> Unit, progressStyle: String = "pill") {
    val state by PlaybackController.state.collectAsState()
    val track by PlaybackController.currentTrack.collectAsState()
    val motion = LocalHimeMotion.current
    val palette by AlbumThemeController.palette.collectAsState()
    var dragX by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    if (track == null) return

    val fraction = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
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
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = palette?.primary?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArt(
                    track,
                    modifier = Modifier
                        .size(42.dp)
                        .graphicsLayer { translationX = dragX * 0.35f },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        track!!.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        track!!.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { PlaybackController.togglePlay() }) {
                    AnimatedContent(
                        targetState = state.playing,
                        transitionSpec = {
                            fadeIn(animationSpec = androidx.compose.animation.core.tween(motion.quick)) togetherWith
                                fadeOut(animationSpec = androidx.compose.animation.core.tween(motion.quick))
                        },
                        label = "playPause",
                    ) { playing ->
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                        )
                    }
                }
            }
        }
    }
}
