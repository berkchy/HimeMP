package hime.mp.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hime.mp.core.PlaybackState
import hime.mp.core.Track
import hime.mp.data.MusicRepository
import hime.mp.data.PlaybackController
import hime.mp.ui.components.AlbumArt
import hime.mp.ui.theme.LocalHimeMotion
import hime.mp.util.formatDuration
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    onClose: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
) {
    val state by PlaybackController.state.collectAsState()
    val track by PlaybackController.currentTrack.collectAsState()
    val motion = LocalHimeMotion.current
    var scrubbing by remember { mutableStateOf(false) }
    var scrub by remember { mutableStateOf(0f) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { PlaybackController.refreshState() }
    LaunchedEffect(state.positionMs, state.durationMs, scrubbing) {
        if (!scrubbing && state.durationMs > 0) {
            scrub = (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
        }
    }

    val fraction = if (state.durationMs > 0) scrub else 0f
    val shownPosition = (fraction * state.durationMs).toLong()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Collapse player")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Now playing", style = MaterialTheme.typography.labelLarge)
                Text(track?.album.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showQueue = true }) {
                Icon(Icons.Default.List, contentDescription = "Queue")
            }
        }

        AlbumArt(
            track,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .aspectRatio(1f),
            corner = 28.dp,
        )

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    track?.title.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                )
                Text(
                    track?.artist.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val favoriteScale by animateFloatAsState(
                targetValue = if (track?.favorite == true) 1.15f else 1f,
                label = "favoriteScale",
            )
            IconButton(onClick = { track?.let(onToggleFavorite) }) {
                Icon(
                    if (track?.favorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    modifier = Modifier.scale(favoriteScale),
                    tint = if (track?.favorite == true) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Slider(
            value = fraction,
            onValueChange = { scrubbing = true; scrub = it },
            onValueChangeFinished = {
                PlaybackController.seek((scrub * state.durationMs).toLong())
                scrubbing = false
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(shownPosition), style = MaterialTheme.typography.bodyMedium)
            Text(formatDuration(state.durationMs), style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { PlaybackController.setShuffle(!state.shuffle) }) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (state.shuffle) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { PlaybackController.previous() }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
            }
            FilledIconButton(
                onClick = { PlaybackController.togglePlay() },
                modifier = Modifier.size(68.dp),
            ) {
                AnimatedContent(
                    targetState = state.playing,
                    transitionSpec = {
                        fadeIn(animationSpec = androidx.compose.animation.core.tween(motion.quick)) togetherWith
                            fadeOut(animationSpec = androidx.compose.animation.core.tween(motion.quick))
                    },
                    label = "playPauseFull",
                ) { playing ->
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            IconButton(onClick = { PlaybackController.next() }) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next")
            }
            IconButton(onClick = { PlaybackController.setRepeat((state.repeat + 1) % 3) }) {
                Icon(
                    if (state.repeat == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Repeat",
                    tint = if (state.repeat != 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = { showSleep = true }) { Text("Sleep") }
            TextButton(onClick = { showSpeed = true }) { Text("${state.speed}x") }
            TextButton(onClick = { track?.let(onAddToPlaylist) }) { Text("Playlist") }
            TextButton(onClick = { PlaybackController.queueClear() }) { Text("Clear queue") }
        }
    }

    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }) {
            QueueSheet(state = state)
        }
    }
    if (showSleep) {
        AlertDialog(
            onDismissRequest = { showSleep = false },
            title = { Text("Sleep timer") },
            text = {
                Column {
                    listOf(15L, 30L, 45L, 60L).forEach { minutes ->
                        TextButton(onClick = {
                            PlaybackController.setSleepTimer(minutes * 60_000L)
                            showSleep = false
                        }) { Text("$minutes minutes") }
                    }
                    TextButton(onClick = {
                        PlaybackController.setSleepTimer(-2L)
                        showSleep = false
                    }) { Text("End of track") }
                    TextButton(onClick = {
                        PlaybackController.setSleepTimer(0L)
                        showSleep = false
                    }) { Text("Cancel timer") }
                }
            },
            confirmButton = {},
        )
    }
    if (showSpeed) {
        AlertDialog(
            onDismissRequest = { showSpeed = false },
            title = { Text("Playback speed") },
            text = {
                Column {
                    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        TextButton(onClick = {
                            PlaybackController.setSpeed(speed)
                            showSpeed = false
                        }) { Text("${speed}x") }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
private fun QueueSheet(state: PlaybackState) {
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(state.queue) {
        delay(60)
        tracks = state.queue.mapNotNull { runCatching { MusicRepository.track(it) }.getOrNull() }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Up next", style = MaterialTheme.typography.titleLarge)
        LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                var dragged by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(index) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragged = false },
                                onDrag = { change, amount ->
                                    if (!dragged && abs(amount.x) > 8f) {
                                        dragged = true
                                        val target = (index + if (amount.x > 0) 1 else -1)
                                            .coerceIn(0, tracks.lastIndex)
                                        if (target != index) PlaybackController.queueMove(index, target)
                                        change.consume()
                                    }
                                },
                                onDragEnd = {},
                                onDragCancel = {},
                            )
                        }
                        .padding(vertical = 8.dp),
                ) {
                    AlbumArt(track, modifier = Modifier.size(40.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(track.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                    TextButton(onClick = { PlaybackController.queueRemove(index) }) { Text("Remove") }
                }
            }
        }
    }
}
