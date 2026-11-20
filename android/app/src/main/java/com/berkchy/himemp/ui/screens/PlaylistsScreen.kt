package com.berkchy.himemp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.berkchy.himemp.core.Track
import com.berkchy.himemp.data.MusicRepository
import com.berkchy.himemp.ui.components.ConfirmDialog
import com.berkchy.himemp.ui.components.TextInputDialog
import com.berkchy.himemp.ui.components.TrackRow
import com.berkchy.himemp.ui.viewmodel.PlaylistsViewModel

@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onQueueNext: (Track) -> Unit,
) {
    if (selectedId != null) {
        PlaylistDetailScreen(selectedId, viewModel, onSelect, onPlayTracks, onQueueNext)
        return
    }
    val playlists by viewModel.playlists.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val recent by viewModel.recent.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deleting by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Playlists", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, "Create playlist") }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { onSelect("__favorites__") },
                ) {
                    Text("Liked songs (${favorites.size})", modifier = Modifier.padding(16.dp))
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { onSelect("__recent__") },
                ) {
                    Text("Recently played (${recent.size})", modifier = Modifier.padding(16.dp))
                }
            }
            items(playlists, key = { it.id }) { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(playlist.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${playlist.trackIds.size} tracks",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { renaming = playlist.id to playlist.name }) {
                        Text("Rename")
                    }
                    IconButton(onClick = { deleting = playlist.id to playlist.name }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                }
            }
        }
    }
    if (creating) {
        TextInputDialog(
            title = "New playlist",
            onDismiss = { creating = false },
            onConfirm = { viewModel.create(it) },
        )
    }
    renaming?.let { (id, name) ->
        TextInputDialog(
            title = "Rename playlist",
            initial = name,
            onDismiss = { renaming = null },
            onConfirm = { viewModel.rename(id, it) },
        )
    }
    deleting?.let { (id, name) ->
        ConfirmDialog(
            title = "Delete playlist",
            message = "Delete \"$name\"?",
            onDismiss = { deleting = null },
            onConfirm = { viewModel.delete(id) },
        )
    }
}

@Composable
private fun PlaylistDetailScreen(
    id: String,
    viewModel: PlaylistsViewModel,
    onSelect: (String?) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onQueueNext: (Track) -> Unit,
) {
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    val playlists by viewModel.playlists.collectAsState()
    LaunchedEffect(id) {
        tracks = when (id) {
            "__favorites__" -> MusicRepository.favorites()
            "__recent__" -> MusicRepository.recentlyPlayed()
            else -> MusicRepository.playlistTracks(id)
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSelect(null) }) { Icon(Icons.Default.ArrowBack, "Back") }
            Text(
                if (id == "__favorites__") "Liked songs"
                else if (id == "__recent__") "Recently played"
                else playlists.firstOrNull { it.id == id }?.name ?: "Playlist",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Button(onClick = { if (tracks.isNotEmpty()) onPlayTracks(tracks, 0) }, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("Play all")
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                TrackRow(
                    track = track,
                    isPlaying = false,
                    onPlay = { onPlayTracks(tracks, index) },
                    onFavorite = {},
                    onQueueNext = { onQueueNext(track) },
                    trailing = {
                        if (!id.startsWith("__")) {
                            IconButton(onClick = { viewModel.removeTrack(id, track.id) }) {
                                Icon(Icons.Default.Delete, "Remove from playlist")
                            }
                        }
                    },
                )
            }
        }
    }
}
