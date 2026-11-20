package hime.mp.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hime.mp.core.Track
import hime.mp.data.MusicRepository
import hime.mp.data.PlaybackController
import hime.mp.ui.components.AlbumArt
import hime.mp.ui.components.ConfirmDialog
import hime.mp.ui.components.TextInputDialog
import hime.mp.ui.components.TrackRow
import hime.mp.ui.viewmodel.LibraryViewModel
import kotlinx.coroutines.launch

private val tabs = listOf("Tracks", "Albums", "Artists", "Folders")
private val sorts = listOf("Artist", "Album", "Title", "Duration", "Added")

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onQueueNext: (Track) -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    var sort by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var playlistNames by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    val scope = rememberCoroutineScope()
    val tracks by viewModel.tracks.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val playerState by PlaybackController.state.collectAsState()

    val sortedTracks = remember(tracks, sort) {
        when (sorts[sort]) {
            "Title" -> tracks.sortedBy { it.title.lowercase() }
            "Album" -> tracks.sortedWith(
                compareBy<Track> { it.album.lowercase() }
                    .thenBy { it.trackNumber }
                    .thenBy { it.title.lowercase() }
            )
            "Duration" -> tracks.sortedByDescending { it.durationMs }
            "Added" -> tracks.sortedByDescending { it.dateAdded }
            else -> tracks
        }
    }
    val selectionMode = selected.isNotEmpty()

    if (selectionMode) {
        Surface(tonalElevation = 3.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("${selected.size} selected", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selected = sortedTracks.map { it.id }.toSet() }) { Text("All") }
                    TextButton(onClick = { selected = emptySet() }) { Text("None") }
                    TextButton(onClick = {
                        val ids = selected.toList()
                        scope.launch {
                            ids.forEach { runCatching { MusicRepository.setFavorite(it, true) } }
                            viewModel.refresh()
                        }
                        selected = emptySet()
                    }) { Text("Fav") }
                    TextButton(onClick = {
                        PlaybackController.queueAdd(selected.toList(), playerState.index + 1)
                        selected = emptySet()
                    }) { Text("Queue") }
                    TextButton(onClick = {
                        scope.launch {
                            playlistNames = MusicRepository.playlists().map { it.id to it.name }
                        }
                        showPlaylistDialog = true
                    }) { Text("Playlist") }
                    TextButton(onClick = { showRemoveDialog = true }) { Text("Remove") }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        if (tab == 0) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                FilterChip(
                    selected = false,
                    onClick = { sort = (sort + 1) % sorts.size },
                    label = { Text("Sort: ${sorts[sort]}") },
                )
            }
        }
        when (tab) {
            0 -> LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(sortedTracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        isPlaying = false,
                        onPlay = {
                            if (selectionMode) {
                                selected = if (track.id in selected) selected - track.id else selected + track.id
                            } else {
                                onPlayTracks(sortedTracks, index)
                            }
                        },
                        onFavorite = { viewModel.toggleFavorite(track) },
                        onQueueNext = { onQueueNext(track) },
                        leading = if (selectionMode) {
                            {
                                Checkbox(
                                    checked = track.id in selected,
                                    onCheckedChange = {
                                        selected = if (it) selected + track.id else selected - track.id
                                    },
                                )
                            }
                        } else null,
                        selected = track.id in selected,
                        onLongClick = { selected = selected + track.id },
                    )
                }
            }
            1 -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(albums.size, key = { albums[it].id }) { index ->
                    val album = albums[index]
                    val cover = tracks.firstOrNull { it.album.equals(album.title, ignoreCase = true) }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        AlbumArt(cover, modifier = Modifier.size(52.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(album.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${album.artist} • ${album.trackCount} tracks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            2 -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(artists.size, key = { artists[it].id }) { index ->
                    val artist = artists[index]
                    Text(
                        "${artist.name} — ${artist.trackCount} tracks, ${artist.albumCount} albums",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            3 -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(folders.size, key = { folders[it].path }) { index ->
                    val folder = folders[index]
                    Text(
                        "${folder.name} — ${folder.trackCount} tracks",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        if (tracks.isEmpty()) {
            Text(
                "No tracks yet. Add a music folder in Settings, then rescan.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("Add to playlist") },
            text = {
                Column {
                    playlistNames.forEach { (id, name) ->
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { MusicRepository.addToPlaylist(id, selected.toList()) }
                            }
                            selected = emptySet()
                            showPlaylistDialog = false
                        }) { Text(name) }
                    }
                    TextButton(onClick = {
                        showPlaylistDialog = false
                        showCreatePlaylist = true
                    }) { Text("New playlist") }
                }
            },
            confirmButton = {},
        )
    }
    if (showCreatePlaylist) {
        TextInputDialog(
            title = "New playlist",
            onDismiss = { showCreatePlaylist = false },
            onConfirm = { name ->
                scope.launch {
                    val created = runCatching { MusicRepository.createPlaylist(name) }.getOrNull()
                    created?.id?.let { id ->
                        runCatching { MusicRepository.addToPlaylist(id, selected.toList()) }
                    }
                    selected = emptySet()
                }
            },
        )
    }
    if (showRemoveDialog) {
        ConfirmDialog(
            title = "Remove from library",
            message = "Remove ${selected.size} tracks from the library index? Files on disk are kept.",
            onDismiss = { showRemoveDialog = false },
            onConfirm = {
                val ids = selected.toList()
                scope.launch {
                    runCatching { MusicRepository.removeFromLibrary(ids) }
                    viewModel.refresh()
                }
                selected = emptySet()
                showRemoveDialog = false
            },
        )
    }
}
