package hime.mp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import hime.mp.core.Track
import hime.mp.data.MusicRepository
import hime.mp.ui.components.CardShape
import hime.mp.ui.components.ConfirmDialog
import hime.mp.ui.components.SectionHeader
import hime.mp.ui.components.TextInputDialog
import hime.mp.ui.components.TrackRow
import hime.mp.ui.components.getGroupedItemShape
import hime.mp.ui.viewmodel.PlaylistsViewModel

@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
) {
    if (selectedId != null) {
        PlaylistDetailScreen(selectedId, viewModel, onSelect, onPlayTracks)
        return
    }
    val playlists by viewModel.playlists.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val recent by viewModel.recent.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deleting by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        SectionHeader(
            title = "Playlists",
            modifier = Modifier.padding(top = 8.dp),
            action = {
                IconButton(onClick = { creating = true }) {
                    Icon(Icons.Default.Add, "Create playlist")
                }
            },
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable { onSelect("__favorites__") },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlaylistIcon(Icons.Default.Favorite)
                        Text(
                            "Liked songs (${favorites.size})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable { onSelect("__recent__") },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlaylistIcon(Icons.Default.History)
                        Text(
                            "Recently played (${recent.size})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable { onSelect("__recent_added__") },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlaylistIcon(Icons.Default.NewReleases)
                        Text(
                            "Recently added",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
            items(playlists, key = { it.id }) { playlist ->
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable { onSelect(playlist.id) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlaylistIcon(Icons.Default.MusicNote)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${playlist.trackIds.size} tracks",
                                style = MaterialTheme.typography.bodySmall,
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
) {
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    val listState = rememberLazyListState()
    val playlists by viewModel.playlists.collectAsState()
    LaunchedEffect(id) { listState.scrollToItem(0) }
    LaunchedEffect(id) {
        tracks = when (id) {
            "__favorites__" -> MusicRepository.favorites()
            "__recent__" -> MusicRepository.recentlyPlayed()
            "__recent_added__" -> MusicRepository.recentlyAdded()
            else -> MusicRepository.playlistTracks(id)
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSelect(null) }) { Icon(Icons.Default.ArrowBack, "Back") }
            Text(
                if (id == "__favorites__") "Liked songs"
                else if (id == "__recent__") "Recently played"
                else if (id == "__recent_added__") "Recently added"
                else playlists.firstOrNull { it.id == id }?.name ?: "Playlist",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Button(
            onClick = { if (tracks.isNotEmpty()) onPlayTracks(tracks, 0) },
            modifier = Modifier.padding(horizontal = 16.dp),
            shape = CardShape,
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Play all", modifier = Modifier.padding(start = 8.dp))
        }
        LazyColumn(modifier = Modifier.weight(1f), state = listState) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                var dragAccumulated by remember { mutableStateOf(0f) }
                TrackRow(
                    track = track,
                    isPlaying = false,
                    onPlay = { onPlayTracks(tracks, index) },
                    onFavorite = {},
                    shape = getGroupedItemShape(index, tracks.size),
                    modifier = Modifier.pointerInput(index) {
                        if (id.startsWith("__")) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragAccumulated = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragAccumulated += amount.y
                                if (abs(dragAccumulated) > 48f) {
                                    val target = (index + if (dragAccumulated > 0) 1 else -1)
                                        .coerceIn(0, tracks.lastIndex)
                                    if (target != index) viewModel.moveTrack(id, index, target)
                                    dragAccumulated = 0f
                                }
                            },
                            onDragEnd = {},
                            onDragCancel = {},
                        )
                    },
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

@Composable
private fun PlaylistIcon(icon: ImageVector) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
