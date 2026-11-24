package hime.mp.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hime.mp.core.PlaybackState
import hime.mp.core.Track
import hime.mp.data.MusicRepository
import hime.mp.data.PlaybackController
import hime.mp.lyrics.LyricLine
import hime.mp.lyrics.LyricsData
import hime.mp.lyrics.LyricsKind
import hime.mp.lyrics.LyricsManager
import hime.mp.ui.components.AlbumArt
import hime.mp.ui.theme.AlbumThemeController
import hime.mp.ui.theme.LocalHimeMotion
import hime.mp.ui.theme.containerOf
import hime.mp.util.ArtCache
import hime.mp.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.TimeSource

private fun playerAccent(raw: Color): Color = lerp(raw, Color.White, 0.42f)

private fun playerOnAccent(accent: Color): Color =
    if (accent.luminance() > 0.55f) Color(0xFF1B1418) else Color.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    onClose: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    settings: Map<String, String> = emptyMap(),
) {
    val context = LocalContext.current
    val state by PlaybackController.state.collectAsState()
    val track by PlaybackController.currentTrack.collectAsState()
    val motion = LocalHimeMotion.current
    val palette by AlbumThemeController.palette.collectAsState()
    val rawAccent = palette?.primary?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val accent = playerAccent(rawAccent)
    val onAccent = playerOnAccent(accent)
    var scrubbing by remember { mutableStateOf(false) }
    var scrub by remember { mutableStateOf(0f) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
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

    fun refreshLyrics() {
        val current = track ?: return
        scope.launch {
            lyricsLoading = true
            lyrics = LyricsManager.refresh(current)
            lyricsLoading = false
        }
    }

    val fraction = if (state.durationMs > 0) scrub else 0f
    val shownPosition = (fraction * state.durationMs).toLong()
    val lyricsPosition = rememberSmoothLyricsPosition(shownPosition, state.playing && !scrubbing)
    val baseBackground = MaterialTheme.colorScheme.surfaceContainerLowest
    val scrim = Brush.verticalGradient(
        listOf(
            Color.Black.copy(alpha = 0.30f),
            Color.Black.copy(alpha = 0.52f),
            Color.Black.copy(alpha = 0.68f),
        ),
    )

    Box(Modifier.fillMaxSize().background(baseBackground)) {
        BlurredArtwork(track, 56.dp, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(scrim))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 8.dp),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CircularGlassButton(Icons.Default.ArrowDropDown, "Collapse player", onClose)
                TwoOptionPill(
                    selected = if (showLyrics) 1 else 0,
                    accent = accent,
                    onAccent = onAccent,
                    onChange = { showLyrics = it == 1 },
                )
                CircularGlassButton(Icons.Default.Cast, "Output", onClick = { showQueue = true })
            }

            Box(
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
                    }
                    .clip(RoundedCornerShape(28.dp)),
            ) {
                AlbumArt(track, modifier = Modifier.fillMaxSize(), corner = 28.dp)
                val accent = palette?.primary?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                val blurEnabled = settings["lyricsBlur"]?.toBoolean() ?: true
                val blurLevel = settings["lyricsBlurIntensity"]?.toIntOrNull()?.coerceIn(0, 4) ?: 2
                val blurRadius = if (blurEnabled) (4 + blurLevel * 6).dp else 0.dp
                Column(Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = showLyrics,
                        enter = fadeIn(tween(220)) +
                            slideInVertically(initialOffsetY = { it / 10 }) +
                            scaleIn(initialScale = 0.98f),
                        exit = fadeOut(tween(180)) +
                            slideOutVertically(targetOffsetY = { it / 10 }) +
                            scaleOut(targetScale = 0.98f),
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(28.dp)),
                        ) {
                            LyricsBackdrop(
                                track = track,
                                accent = accent,
                                blurRadius = blurRadius,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(Modifier.fillMaxSize().padding(16.dp)) {
                                LyricsPanel(
                                    data = lyrics,
                                    loading = lyricsLoading,
                                    positionMs = lyricsPosition,
                                    settings = settings,
                                    onRefresh = { refreshLyrics() },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track?.title.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track?.artist.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                SquareGlassButton(Icons.Default.Share, "Share") {
                    val current = track ?: return@SquareGlassButton
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${current.title} — ${current.artist}")
                    }
                    runCatching { context.startActivity(Intent.createChooser(intent, "Share track")) }
                }
                Spacer(Modifier.width(8.dp))
                SquareGlassButton(
                    icon = if (track?.favorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    label = "Favorite",
                    tint = if (track?.favorite == true) accent else Color.Unspecified,
                ) { track?.let(onToggleFavorite) }
            }
        }

        Spacer(Modifier.height(8.dp))
        ModernSeekBar(
            style = settings["progressStyle"] ?: "pill",
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

        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CircularGlassButton(
                icon = Icons.Default.SkipPrevious,
                label = "Previous",
                onClick = { PlaybackController.previous() },
                size = 64.dp,
            )
            Button(
                onClick = { PlaybackController.togglePlay() },
                modifier = Modifier
                    .height(64.dp)
                    .widthIn(min = 180.dp),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = onAccent,
                ),
            ) {
                AnimatedContent(
                    targetState = state.playing,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(motion.quick)) togetherWith
                            fadeOut(animationSpec = tween(motion.quick))
                    },
                    label = "playPauseFull",
                ) { playing ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (playing) "Duraklat" else "Çal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            CircularGlassButton(
                icon = Icons.Default.SkipNext,
                label = "Next",
                onClick = { PlaybackController.next() },
                size = 64.dp,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassPillAction(Icons.Default.List, "Queue", accent) { showQueue = true }
                GlassPillAction(Icons.Default.DarkMode, "Sleep", accent) {}
                GlassPillAction(
                    Icons.Default.Shuffle,
                    "Shuffle",
                    accent,
                    state.shuffle,
                ) { PlaybackController.setShuffle(!state.shuffle) }
                GlassPillAction(
                    Icons.Default.Lyrics,
                    "Lyrics",
                    accent,
                    showLyrics,
                ) { showLyrics = !showLyrics }
                GlassPillAction(
                    if (state.repeat == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "Repeat",
                    accent,
                    state.repeat != 0,
                ) { PlaybackController.setRepeat((state.repeat + 1) % 3) }
            }
            Box {
                CircularGlassButton(Icons.Default.MoreVert, "More", { showMore = true }, 48.dp)
                DropdownMenu(
                    expanded = showMore,
                    onDismissRequest = { showMore = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy track title") },
                        onClick = {
                            track?.let { current ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("track", "${current.title} — ${current.artist}"),
                                )
                            }
                            showMore = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Open queue") },
                        onClick = {
                            showQueue = true
                            showMore = false
                        },
                    )
                }
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
private fun CircularGlassButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    size: Dp = 44.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.42f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = Color.White,
        )
    }
}

@Composable
private fun TwoOptionPill(
    selected: Int,
    accent: Color,
    onAccent: Color,
    onChange: (Int) -> Unit,
) {
    val options = listOf(
        Icons.Default.Headphones to "Player",
        Icons.Default.SmartDisplay to "Lyrics",
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.42f))
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, (icon, label) ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (active) accent else Color.Transparent)
                    .clickable { onChange(index) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (active) onAccent else Color.White.copy(alpha = 0.78f),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) onAccent else Color.White.copy(alpha = 0.78f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SquareGlassButton(
    icon: ImageVector,
    label: String,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.42f),
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = if (tint == Color.Unspecified) Color.White else tint,
            )
        }
    }
}

@Composable
private fun GlassPillAction(
    icon: ImageVector,
    label: String,
    accent: Color,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (active) {
            accent.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.38f)
        },
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(18.dp),
                tint = if (active) accent else Color.White.copy(alpha = 0.82f),
            )
        }
    }
}

@Composable
private fun ModernSeekBar(
    style: String,
    fraction: Float,
    onScrub: (Float) -> Unit,
    onScrubFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette by AlbumThemeController.palette.collectAsState()
    val accent = playerAccent(palette?.primary?.let { Color(it) } ?: MaterialTheme.colorScheme.primary)
    val trackColor = Color.White.copy(alpha = 0.24f)
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
        when (style) {
            "thin" -> {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(trackColor),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent),
                )
            }

            "classic" -> {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(trackColor),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = with(density) { (widthPx * fraction).toDp() - 6.dp })
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }

            "segmented" -> {
                val segments = 36
                val filled = (fraction * segments).toInt()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(segments) { index ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(if (index < filled) 10.dp else 5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (index < filled) accent else trackColor),
                        )
                    }
                }
            }

            else -> {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(trackColor),
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
    }
}

@Composable
private fun BlurredArtwork(
    track: Track?,
    blurRadius: Dp,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = track?.id) {
        value = track?.let { ArtCache.cached(it) ?: ArtCache.load(it) }?.asImageBitmap()
    }
    val density = LocalDensity.current
    val radiusPx = with(density) { blurRadius.toPx() }
    val paint = remember(radiusPx) {
        Paint().apply {
            isFilterBitmap = true
            maskFilter = BlurMaskFilter(radiusPx.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        }
    }
    Canvas(modifier) {
        val image = bitmap ?: return@Canvas
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmap(
                image.asAndroidBitmap(),
                null,
                RectF(0f, 0f, size.width, size.height),
                paint,
            )
        }
    }
}

@Composable
private fun LyricsBackdrop(
    track: Track?,
    accent: Color,
    blurRadius: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clip(RoundedCornerShape(28.dp))) {
        if (blurRadius > 0.dp) {
            BlurredArtwork(track, blurRadius, Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(lerp(Color.Black, accent, 0.18f).copy(alpha = 0.72f)),
        )
    }
}

@Composable
private fun LyricsPanel(
    data: LyricsData?,
    loading: Boolean,
    positionMs: Long,
    settings: Map<String, String>,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var lastActive by remember { mutableStateOf(-1) }
    var headerHeightPx by remember { mutableStateOf(0) }
    val horizontalAlignment = when (settings["lyricsAlignment"]) {
        "start" -> Alignment.Start
        "end" -> Alignment.End
        else -> Alignment.CenterHorizontally
    }
    val textAlign = when (settings["lyricsAlignment"]) {
        "start" -> TextAlign.Start
        "end" -> TextAlign.End
        else -> TextAlign.Center
    }
    val baseSize = when (settings["lyricsSize"]) {
        "small" -> 15.sp
        "large" -> 21.sp
        "xlarge" -> 25.sp
        else -> 18.sp
    }

    if (loading) {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (data == null || data.lines.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (data?.kind == LyricsKind.ERROR) "Lyrics unavailable" else "Lyrics not found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRefresh) { Text("Retry") }
            }
        }
        return
    }

    val active = data.lines.indexOfLast { it.timeMs in 0..positionMs }.coerceAtLeast(0)
    val autoScroll = settings["lyricsAutoScroll"]?.toBoolean() != false
    val seekEnabled = settings["lyricsSeek"]?.toBoolean() != false

    BoxWithConstraints(modifier.clipToBounds()) {
        val halfHeight = maxHeight / 2
        val density = LocalDensity.current
        val coverCenterPx = with(density) { halfHeight.toPx() }
        LaunchedEffect(active, autoScroll, data.lines, maxHeight, headerHeightPx) {
            if (!autoScroll || data.lines.isEmpty()) return@LaunchedEffect
            val anchorPx = (coverCenterPx - headerHeightPx).coerceAtLeast(1f)
            fun offsetToCenter(): Float? {
                val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == active } ?: return null
                return info.offset + info.size / 2f - anchorPx
            }
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == active }) {
                runCatching { listState.scrollToItem(active) }
                withFrameNanos {}
                withFrameNanos {}
            }
            val jump = lastActive == -1 || abs(active - lastActive) > 1
            if (jump) {
                repeat(4) {
                    val offset = offsetToCenter() ?: return@repeat
                    if (abs(offset) <= 1f) return@repeat
                    listState.scrollBy(offset)
                    withFrameNanos {}
                }
            } else {
                val offset = offsetToCenter() ?: return@LaunchedEffect
                if (abs(offset) > 1f) {
                    runCatching { listState.animateScrollBy(offset, tween(240)) }
                    withFrameNanos {}
                    val residual = offsetToCenter() ?: 0f
                    if (abs(residual) > 2f) listState.scrollBy(residual)
                }
            }
            lastActive = active
        }
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.onSizeChanged { headerHeightPx = it.height },
            ) {
                val status = when (data.kind) {
                    LyricsKind.SYNCED -> "Synced"
                    LyricsKind.PLAIN -> "Plain only"
                    else -> data.source
                }
                Text(
                    "Lyrics · $status · ${data.source}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refetch lyrics")
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                state = listState,
                horizontalAlignment = horizontalAlignment,
                contentPadding = PaddingValues(vertical = (halfHeight - 24.dp).coerceAtLeast(0.dp)),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(data.lines) { index, line ->
                    val isActive = index == active && data.synced
                    val style = if (isActive) {
                        MaterialTheme.typography.titleLarge.copy(
                            fontSize = baseSize * 1.18f,
                            textAlign = textAlign,
                        )
                    } else {
                        MaterialTheme.typography.bodyLarge.copy(
                            fontSize = baseSize,
                            textAlign = textAlign,
                        )
                    }
                    val lineModifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isActive) 1f else 0.55f)
                        .graphicsLayer {
                            scaleX = if (isActive) 1.05f else 1f
                            scaleY = if (isActive) 1.05f else 1f
                        }
                        .clickable(enabled = seekEnabled && line.timeMs >= 0) {
                            PlaybackController.seek(line.timeMs)
                        }
                    if (isActive && line.words.isNotEmpty()) {
                        KaraokeLine(
                            line = line,
                            positionMs = positionMs,
                            style = style,
                            accent = MaterialTheme.colorScheme.primary,
                            dim = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = lineModifier,
                        )
                    } else {
                        Text(
                            text = line.text,
                            style = style,
                            color = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = lineModifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberSmoothLyricsPosition(positionMs: Long, running: Boolean): Long {
    val clock = remember { TimeSource.Monotonic }
    var smooth by remember { mutableLongStateOf(positionMs) }
    var latest by remember { mutableLongStateOf(positionMs) }
    var anchor by remember { mutableLongStateOf(positionMs) }
    var startedAt by remember { mutableStateOf(clock.markNow()) }

    LaunchedEffect(positionMs) { latest = positionMs }
    LaunchedEffect(latest, running) {
        if (abs(latest - anchor) > 300L || !running) {
            anchor = latest
            startedAt = clock.markNow()
            smooth = latest
        }
    }
    LaunchedEffect(running) {
        if (!running) {
            smooth = latest
            return@LaunchedEffect
        }
        anchor = latest
        startedAt = clock.markNow()
        smooth = latest
        while (true) {
            delay(16)
            smooth = anchor + startedAt.elapsedNow().inWholeMilliseconds
        }
    }
    return smooth
}

@Composable
private fun KaraokeLine(
    line: LyricLine,
    positionMs: Long,
    style: TextStyle,
    accent: Color,
    dim: Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val layout = remember(line.text, style) {
        measurer.measure(AnnotatedString(line.text), style = style, maxLines = 1)
    }
    val words = remember(line) { line.words.filter { it.text.isNotEmpty() } }
    val textMatches = remember(line, words) { words.joinToString("") { it.text } == line.text }
    val activeIndex = words.indexOfLast { positionMs >= it.timeMs }.coerceAtLeast(0)
    val current = words.getOrNull(activeIndex)
    val active = textMatches && current != null && positionMs >= line.timeMs
    val progress = if (!active || current == null) {
        0f
    } else {
        val duration = (current.endMs - current.timeMs).coerceAtLeast(100L)
        ((positionMs - current.timeMs).toFloat() / duration).coerceIn(0f, 1f)
    }
    val startChar = if (active) (0 until activeIndex).sumOf { words[it].text.length } else 0
    val endChar = (startChar + (current?.text?.length ?: 0)).coerceAtMost(line.text.length)
    val startX = if (active) layout.getHorizontalPosition(startChar, true) else 0f
    val endX = if (active) layout.getHorizontalPosition(endChar, true) else 0f
    val wipeX = startX + (endX - startX) * progress
    val height = with(LocalDensity.current) { layout.size.height.toDp() }

    Canvas(modifier.fillMaxWidth().height(height)) {
        val textX = when (style.textAlign) {
            TextAlign.Center -> (size.width - layout.size.width) / 2f
            TextAlign.End -> size.width - layout.size.width
            else -> 0f
        }
        if (!active || !textMatches) {
            drawText(
                layout,
                color = if (active) accent else dim,
                topLeft = Offset(textX, 0f),
            )
            return@Canvas
        }
        drawText(
            layout,
            color = dim.copy(alpha = 0.42f),
            topLeft = Offset(textX, 0f),
        )
        val band = 24f
        drawText(
            layout,
            brush = Brush.linearGradient(
                colors = listOf(accent, accent, dim, dim),
                start = Offset(textX + wipeX - band, 0f),
                end = Offset(textX + wipeX + band, 0f),
            ),
            shadow = Shadow(blurRadius = 12f, color = accent.copy(alpha = 0.7f)),
            topLeft = Offset(textX, 0f),
        )
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
