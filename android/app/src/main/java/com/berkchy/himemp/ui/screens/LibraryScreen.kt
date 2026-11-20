package com.berkchy.himemp.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.berkchy.himemp.core.Track
import com.berkchy.himemp.ui.components.AlbumArt
import com.berkchy.himemp.ui.components.TrackRow
import com.berkchy.himemp.ui.viewmodel.LibraryViewModel

private val tabs = listOf("Tracks", "Albums", "Artists", "Folders")

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onQueueNext: (Track) -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    val tracks by viewModel.tracks.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val folders by viewModel.folders.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        when (tab) {
            0 -> LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        isPlaying = false,
                        onPlay = { onPlayTracks(tracks, index) },
                        onFavorite = { viewModel.toggleFavorite(track) },
                        onQueueNext = { onQueueNext(track) },
                    )
                }
            }
            1 -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(albums.size, key = { albums[it].id }) { index ->
                    val album = albums[index]
                    val cover = tracks.firstOrNull { it.album.equals(album.title, ignoreCase = true) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        AlbumArt(cover, modifier = androidx.compose.foundation.layout.size(52.dp))
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
                items(folders.size, key = { it.path }) { index ->
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
        Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(16.dp)) {
            Text("Refresh library")
        }
    }
}
