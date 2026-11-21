package hime.mp.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hime.mp.core.Track
import hime.mp.data.MusicRepository
import hime.mp.data.PlaybackController
import hime.mp.ui.components.ConfirmDialog
import hime.mp.ui.components.PillButton
import hime.mp.ui.components.TextInputDialog
import hime.mp.ui.components.TrackRow
import hime.mp.ui.components.getGroupedItemShape
import hime.mp.ui.viewmodel.LibraryViewModel
import kotlinx.coroutines.launch

private val sorts = listOf("Artist", "Album", "Title", "Duration", "Added")

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onPlayTracks: (List<Track>, Int) -> Unit,
) {
    var sort by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var playlistNames by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val tracks by viewModel.tracks.collectAsState()
    val playerState by PlaybackController.state.collectAsState()
    val selectionMode = selected.isNotEmpty()

    LaunchedEffect(sort, query) { listState.scrollToItem(0) }

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

    val visibleTracks = remember(sortedTracks, query) {
        val needle = query.trim()
        if (needle.isBlank()) {
            sortedTracks
        } else {
            sortedTracks.filter {
                it.title.contains(needle, ignoreCase = true) ||
                    it.artist.contains(needle, ignoreCase = true) ||
                    it.album.contains(needle, ignoreCase = true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(tonalElevation = 3.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                )
                if (!selectionMode) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Tracks",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    PillButton(text = "Sort: ${sorts[sort]}") {
                        sort = (sort + 1) % sorts.size
                    }
                }
            }
            if (selectionMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${selected.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { selected = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                        }
                    }
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { selected = visibleTracks.map { it.id }.toSet() }) {
                            Text("All")
                        }
                        TextButton(onClick = { selected = emptySet() }) { Text("None") }
                        TextButton(onClick = {
                            val ids = selected.toList()
                            scope.launch {
                                ids.forEach { runCatching { MusicRepository.setFavorite(it, true) } }
                                viewModel.refresh()
                            }
                            selected = emptySet()
                        }) { Text("Favorite") }
                        TextButton(onClick = {
                            val ids = selected.toList()
                            PlaybackController.queueAdd(ids, playerState.index + 1)
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

        LazyColumn(modifier = Modifier.weight(1f), state = listState) {
            itemsIndexed(visibleTracks, key = { _, track -> track.id }) { index, track ->
                TrackRow(
                    track = track,
                    isPlaying = false,
                    onPlay = {
                        if (selectionMode) {
                            selected = if (track.id in selected) selected - track.id else selected + track.id
                        } else {
                            onPlayTracks(visibleTracks, index)
                        }
                    },
                    onFavorite = { viewModel.toggleFavorite(track) },
                    leading = if (selectionMode) {
                        {
                            Checkbox(
                                checked = track.id in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + track.id else selected - track.id
                                },
                            )
                        }
                    } else {
                        null
                    },
                    selected = track.id in selected,
                    shape = getGroupedItemShape(index, visibleTracks.size),
                    onLongClick = { selected = selected + track.id },
                )
            }
        }
        if (visibleTracks.isEmpty()) {
            Text(
                if (query.isBlank()) {
                    "No tracks yet. Add a music folder in Settings, then rescan."
                } else {
                    "No matching tracks."
                },
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
                            val ids = selected.toList()
                            scope.launch {
                                runCatching { MusicRepository.addToPlaylist(id, ids) }
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
                val ids = selected.toList()
                scope.launch {
                    val created = runCatching { MusicRepository.createPlaylist(name) }.getOrNull()
                    created?.id?.let { id ->
                        runCatching { MusicRepository.addToPlaylist(id, ids) }
                    }
                }
                selected = emptySet()
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
