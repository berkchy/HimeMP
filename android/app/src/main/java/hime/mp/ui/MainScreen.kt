package hime.mp.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import hime.mp.core.Track
import hime.mp.data.PlaybackController
import hime.mp.ui.components.MiniPlayer
import hime.mp.ui.screens.FullPlayerScreen
import hime.mp.ui.screens.LibraryScreen
import hime.mp.ui.screens.PlaylistsScreen
import hime.mp.ui.screens.SearchScreen
import hime.mp.ui.screens.SettingsScreen
import hime.mp.ui.theme.AlbumThemeController
import hime.mp.ui.theme.HimeTheme
import hime.mp.ui.viewmodel.LibraryViewModel
import hime.mp.ui.viewmodel.PlaylistsViewModel
import hime.mp.ui.viewmodel.SettingsViewModel
import hime.mp.util.ArtCache
import hime.mp.util.Permissions

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val library: LibraryViewModel = viewModel()
    val playlists: PlaylistsViewModel = viewModel()
    val settings: SettingsViewModel = viewModel()

    var fullPlayer by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 4 })
    var selectedTab by remember { mutableIntStateOf(pagerState.currentPage) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { selectedTab = it }
    }

    val goToPage: (Int) -> Unit = { page ->
        selectedTab = page
        scope.launch {
            pagerState.animateScrollToPage(
                page,
                animationSpec = tween(420, easing = FastOutSlowInEasing),
            )
        }
    }

    val settingsMap by settings.settings.collectAsState()
    val currentTrack by PlaybackController.currentTrack.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(currentTrack?.id) {
        val bitmap = withContext(Dispatchers.IO) {
            currentTrack?.let { ArtCache.loadBlocking(it) }
        }
        if (bitmap == null) {
            AlbumThemeController.clear()
            return@LaunchedEffect
        }
        val extracted = withContext(Dispatchers.Default) {
            Palette.from(bitmap).maximumColorCount(16).generate()
        }
        AlbumThemeController.update(extracted)
    }

    LaunchedEffect(Unit) {
        PlaybackController.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            settings.addFolder("/storage/emulated/0/Music")
            settings.rescan(incremental = true)
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        if (!Permissions.hasAudioPermission(context)) {
            audioPermission.launch(Permissions.audioPermission())
        } else {
            settings.refresh()
        }
        if (!Permissions.hasNotificationPermission(context)) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = fullPlayer) { fullPlayer = false }
    BackHandler(enabled = !fullPlayer && selectedPlaylist != null) { selectedPlaylist = null }
    BackHandler(enabled = !fullPlayer && selectedPlaylist == null && pagerState.currentPage != 0) {
        goToPage(0)
    }

    HimeTheme(
        themeMode = settingsMap["theme"] ?: "system",
        dynamicColor = settingsMap["dynamicColor"]?.toBoolean() ?: true,
        reducedMotion = settingsMap["reducedMotion"]?.toBoolean() ?: false,
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) { data -> Snackbar(data) } },
            bottomBar = {
                Column {
                    if (!fullPlayer) {
                        MiniPlayer(onExpand = { fullPlayer = true })
                    }
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 3.dp,
                    ) {
                        val items = listOf(
                            Triple(0, Icons.Default.Home, "Library"),
                            Triple(1, Icons.Default.Search, "Search"),
                            Triple(2, Icons.Default.List, "Playlists"),
                            Triple(3, Icons.Default.Settings, "Settings"),
                        )
                        items.forEach { (page, icon, label) ->
                            NavigationBarItem(
                                selected = selectedTab == page,
                                onClick = {
                                    fullPlayer = false
                                    goToPage(page)
                                    if (page == 2) {
                                        playlists.refresh()
                                    } else {
                                        selectedPlaylist = null
                                    }
                                },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .fillMaxSize(),
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 3,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (page) {
                        0 -> LibraryScreen(
                            viewModel = library,
                            onPlayTracks = { tracks, index -> play(tracks, index) },
                        )
                        1 -> SearchScreen(
                            onPlayTracks = { tracks, index -> play(tracks, index) },
                        )
                        2 -> PlaylistsScreen(
                            viewModel = playlists,
                            selectedId = selectedPlaylist,
                            onSelect = { selectedPlaylist = it },
                            onPlayTracks = { tracks, index -> play(tracks, index) },
                        )
                        else -> SettingsScreen(viewModel = settings)
                    }
                }

                AnimatedVisibility(
                    visible = fullPlayer,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                ) {
                    FullPlayerScreen(
                        onClose = { fullPlayer = false },
                        onToggleFavorite = { track ->
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                runCatching { hime.mp.data.MusicRepository.setFavorite(track.id, !track.favorite) }
                                library.refresh()
                                playlists.refresh()
                                PlaybackController.refreshState()
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun play(tracks: List<Track>, index: Int) {
    PlaybackController.playTracks(tracks, index)
}
