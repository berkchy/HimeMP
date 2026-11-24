package hime.mp.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hime.mp.core.PlaybackState
import hime.mp.core.Track
import hime.mp.data.MusicRepository
import hime.mp.data.PlaybackController
import hime.mp.lyrics.LyricsData
import hime.mp.lyrics.LyricsManager
import hime.mp.ui.components.AlbumArt
import hime.mp.ui.theme.AlbumThemeController
import hime.mp.ui.theme.LocalHimeMotion
import hime.mp.ui.theme.containerOf
import hime.mp.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    onClose: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
) {
    val state by PlaybackController.state.collectAsState()
    val track by PlaybackController.currentTrack.collectAsState()
    val motion = LocalHimeMotion.current
    var scrubbing by remember { mutableStateOf(false) }
    var scrub by remember { mutableStateOf(0f) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<LyricsData?>(null) }
    var lyricsLoading by remember { mutableStateOf(false) }
    var artWidth by remember { mutableStateOf(0) }
    var dragOffset by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(track?.id) {
        lyrics = null
        lyricsLoading = track != null
        if (track != null) {
            lyrics = LyricsManager.load(track)
            lyricsLoading = false
        }
    }
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
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.pointerInput(Unit) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onVerticalDrag = { change, amount ->
                        totalDrag += amount
                        change.consume()
                    },
                    onDragEnd = { if (totalDrag > 120f) onClose() },
                    onDragCancel = {},
                )
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Collapse player")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Now playing", style = MaterialTheme.typography.labelLarge)
                    Text(
                        track?.album.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showLyrics = !showLyrics }) {
                    Text(if (showLyrics) "Hide lyrics" else "Lyrics")
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
                    .aspectRatio(1f)
                    .onSizeChanged { artWidth = it.width }
                    .graphicsLayer {
                        translationX = dragOffset
                        rotationZ = if (artWidth > 0) dragOffset / artWidth * 6f else 0f
                        alpha = if (artWidth > 0) {
                            1f - (abs(dragOffset) / artWidth).coerceIn(0f, 0.2f)
                        } else {
                            1f
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragOffset = 0f },
                            onHorizontalDrag = { change, amount ->
                                dragOffset += amount
                                change.consume()
                            },
                            onDragEnd = {
                                val width = artWidth.toFloat().takeIf { it > 0f } ?: 1000f
                                if (abs(dragOffset) > 80f) {
                                    val direction = if (dragOffset > 0) 1 else -1
                                    scope.launch {
                                        animate(
                                            initialValue = dragOffset,
                                            targetValue = direction * width,
                                            animationSpec = tween(180),
                                        ) { value, _ -> dragOffset = value }
                                        if (direction > 0) PlaybackController.previous()
                                        else PlaybackController.next()
                                        dragOffset = -direction * width
                                        animate(
                                            initialValue = dragOffset,
                                            targetValue = 0f,
                                            animationSpec = tween(240),
                                        ) { value, _ -> dragOffset = value }
                                    }
                                } else {
                                    scope.launch {
                                        animate(
                                            initialValue = dragOffset,
                                            targetValue = 0f,
                                            animationSpec = tween(160),
                                        ) { value, _ -> dragOffset = value }
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    animate(
                                        initialValue = dragOffset,
                                        targetValue = 0f,
                                        animationSpec = tween(160),
                                    ) { value, _ -> dragOffset = value }
                                }
                            },
                        )
                    },
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
        }

        Spacer(Modifier.height(8.dp))
        ModernSeekBar(
            fraction = fraction,
            onScrub = { scrubbing = true; scrub = it },
            onScrubFinish = {
                PlaybackController.seek((scrub * state.durationMs).toLong())
                scrubbing = false
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(shownPosition), style = MaterialTheme.typography.bodyMedium)
            Text(formatDuration(state.durationMs), style = MaterialTheme.typography.bodyMedium)
        }

        if (showLyrics) {
            Spacer(Modifier.height(8.dp))
            LyricsPanel(lyrics, lyricsLoading, shownPosition, Modifier.fillMaxWidth().weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = containerOf(MaterialTheme.colorScheme.secondary, dark = true),
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
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
        }
    }

    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }) {
            QueueSheet(state = state)
        }
    }
}

@Composable
private fun ModernSeekBar(
    fraction: Float,
    onScrub: (Float) -> Unit,
    onScrubFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette by AlbumThemeController.palette.collectAsState()
    val accent = palette?.primary?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    var widthPx by remember { mutableStateOf(1) }
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .height(28.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                var active = false
                detectHorizontalDragGestures(
                    onDragStart = { start ->
                        active = true
                        onScrub((start.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f))
                    },
                    onHorizontalDrag = { change, _ ->
                        onScrub((change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f))
                        change.consume()
                    },
                    onDragEnd = {
                        if (active) onScrubFinish()
                        active = false
                    },
                    onDragCancel = {
                        if (active) onScrubFinish()
                        active = false
                    },
                )
            },
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(accent),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = with(density) { (widthPx * fraction).toDp() - 9.dp })
                .size(18.dp)
                .clip(CircleShape)
                .background(accent),
        )
    }
}

@Composable
private fun LyricsPanel(data: LyricsData?, loading: Boolean, positionMs: Long, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    if (loading) {
        Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (data == null || data.lines.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "Lyrics not found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val active = data.lines.indexOfLast { it.timeMs in 0..positionMs }.coerceAtLeast(0)
    LaunchedEffect(active) {
        runCatching { listState.animateScrollToItem(active, -120) }
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(data.lines) { index, line ->
            Text(
                line.text,
                style = if (index == active && data.synced) {
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = if (index == active && data.synced) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun QueueSheet(state: PlaybackState) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("himemp_ui", Context.MODE_PRIVATE) }
    val savedIndex = remember { prefs.getInt("queue_scroll_index", 0) }
    val savedOffset = remember { prefs.getInt("queue_scroll_offset", 0) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    val listState = rememberLazyListState(savedIndex, savedOffset)
    LaunchedEffect(state.queue) {
        delay(60)
        tracks = state.queue.mapNotNull { runCatching { MusicRepository.track(it) }.getOrNull() }
        if (tracks.isNotEmpty()) {
            if (savedIndex > 0) {
                listState.scrollToItem(savedIndex.coerceIn(0, tracks.lastIndex), savedOffset)
            } else if (state.index in tracks.indices) {
                listState.scrollToItem(state.index)
            }
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                prefs.edit()
                    .putInt("queue_scroll_index", index)
                    .putInt("queue_scroll_offset", offset)
                    .apply()
            }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Up next", style = MaterialTheme.typography.titleLarge)
        LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp), state = listState) {
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
