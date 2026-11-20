package hime.mp.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import hime.mp.core.Track
import hime.mp.data.PlaybackController
import hime.mp.ui.components.MiniPlayer
import hime.mp.ui.components.TextInputDialog
import hime.mp.ui.screens.FullPlayerScreen
import hime.mp.ui.screens.LibraryScreen
import hime.mp.ui.screens.PlaylistsScreen
import hime.mp.ui.screens.SearchScreen
import hime.mp.ui.screens.SettingsScreen
import hime.mp.ui.theme.HimeTheme
import hime.mp.ui.viewmodel.LibraryViewModel
import hime.mp.ui.viewmodel.PlaylistsViewModel
import hime.mp.ui.viewmodel.SettingsViewModel
import hime.mp.util.Permissions

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val library: LibraryViewModel = viewModel()
    val playlists: PlaylistsViewModel = viewModel()
    val settings: SettingsViewModel = viewModel()

    var destination by remember { mutableStateOf("library") }
    var fullPlayer by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<String?>(null) }
    var addTarget by remember { mutableStateOf<Track?>(null) }
    var creatingForTrack by remember { mutableStateOf(false) }

    val settingsMap by settings.settings.collectAsState()
    val playlistState by playlists.playlists.collectAsState()
    val playerState by PlaybackController.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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

    HimeTheme(
        themeMode = settingsMap["theme"] ?: "system",
        dynamicColor = settingsMap["dynamicColor"]?.toBoolean() ?: true,
        reducedMotion = settingsMap["reducedMotion"]?.toBoolean() ?: false,
    ) {
        Scaffold(
            snackbarHost = { host -> SnackbarHost(host) { data -> Snackbar(data) } },
            bottomBar = {
                Column {
                    MiniPlayer(onExpand = { fullPlayer = true })
                    NavigationBar {
                        val items = listOf(
                            "library" to Icons.Default.Home,
                            "search" to Icons.Default.Search,
                            "playlists" to Icons.Default.List,
                            "settings" to Icons.Default.Settings,
                        )
                        items.forEach { (route, icon) ->
                            NavigationBarItem(
                                selected = destination == route,
                                onClick = {
                                    destination = route
                                    if (route != "playlists") selectedPlaylist = null
                                },
                                icon = { Icon(icon, contentDescription = route) },
                                label = { Text(route.replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                when (destination) {
                    "library" -> LibraryScreen(
                        viewModel = library,
                        onPlayTracks = { tracks, index -> play(tracks, index) },
                        onQueueNext = { track -> queueNext(track, playerState.index) },
                    )
                    "search" -> SearchScreen(
                        onPlayTracks = { tracks, index -> play(tracks, index) },
                        onQueueNext = { track -> queueNext(track, playerState.index) },
                    )
                    "playlists" -> PlaylistsScreen(
                        viewModel = playlists,
                        selectedId = selectedPlaylist,
                        onSelect = { selectedPlaylist = it },
                        onPlayTracks = { tracks, index -> play(tracks, index) },
                        onQueueNext = { track -> queueNext(track, playerState.index) },
                    )
                    "settings" -> SettingsScreen(viewModel = settings)
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
                        onAddToPlaylist = { addTarget = it },
                    )
                }
            }
        }

        addTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { addTarget = null },
                title = { Text("Add to playlist") },
                text = {
                    Column {
                        playlistState.forEach { playlist ->
                            TextButton(onClick = {
                                playlists.addTracks(playlist.id, listOf(target.id))
                                addTarget = null
                            }) { Text(playlist.name) }
                        }
                        TextButton(onClick = { creatingForTrack = true }) { Text("New playlist") }
                    }
                },
                confirmButton = {},
            )
        }
        if (creatingForTrack) {
            TextInputDialog(
                title = "New playlist",
                onDismiss = { creatingForTrack = false },
                onConfirm = { name ->
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        val created = runCatching {
                            hime.mp.data.MusicRepository.createPlaylist(name)
                        }.getOrNull()
                        addTarget?.let { target ->
                            created?.id?.let { id -> playlists.addTracks(id, listOf(target.id)) }
                        }
                        playlists.refresh()
                        addTarget = null
                        creatingForTrack = false
                    }
                },
            )
        }
    }
}

private fun play(tracks: List<Track>, index: Int) {
    PlaybackController.playQueue(tracks.map { it.id }, index)
}

private fun queueNext(track: Track, currentIndex: Int) {
    PlaybackController.queueAdd(listOf(track.id), (currentIndex + 1).coerceAtLeast(0))
}
