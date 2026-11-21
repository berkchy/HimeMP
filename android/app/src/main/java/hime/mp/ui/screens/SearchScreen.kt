package hime.mp.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hime.mp.core.Album
import hime.mp.core.Artist
import hime.mp.core.Playlist
import hime.mp.core.Track
import hime.mp.data.MusicRepository
import hime.mp.ui.components.SectionHeader
import hime.mp.ui.components.TrackRow
import hime.mp.ui.components.getGroupedItemShape
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    onPlayTracks: (List<Track>, Int) -> Unit,
    onQueueNext: (Track) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var artists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    val listState = rememberLazyListState()

    LaunchedEffect(query) { listState.scrollToItem(0) }

    LaunchedEffect(query) {
        delay(180)
        val needle = query.trim()
        if (needle.isBlank()) {
            results = emptyList()
            playlists = emptyList()
            albums = emptyList()
            artists = emptyList()
            return@LaunchedEffect
        }
        results = runCatching { MusicRepository.search(needle) }.getOrDefault(emptyList())
        playlists = runCatching { MusicRepository.playlists() }
            .getOrDefault(emptyList())
            .filter { it.name.contains(needle, ignoreCase = true) }
        albums = runCatching { MusicRepository.albums() }
            .getOrDefault(emptyList())
            .filter { it.title.contains(needle, ignoreCase = true) || it.artist.contains(needle, ignoreCase = true) }
        artists = runCatching { MusicRepository.artists() }
            .getOrDefault(emptyList())
            .filter { it.name.contains(needle, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search tracks, artists, albums, playlists") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        if (results.isEmpty() && playlists.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
            Text(
                if (query.isBlank()) "Type to search your library" else "No results",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(modifier = Modifier.weight(1f), state = listState) {
            if (playlists.isNotEmpty()) {
                item { SectionTitle("Playlists") }
                items(playlists.size, key = { playlists[it].id }) { index ->
                    Text(playlists[index].name, modifier = Modifier.padding(16.dp).fillMaxWidth())
                }
            }
            if (artists.isNotEmpty()) {
                item { SectionTitle("Artists") }
                items(artists.size, key = { artists[it].id }) { index ->
                    Text(artists[index].name, modifier = Modifier.padding(16.dp).fillMaxWidth())
                }
            }
            if (albums.isNotEmpty()) {
                item { SectionTitle("Albums") }
                items(albums.size, key = { albums[it].id }) { index ->
                    val album = albums[index]
                    Text(
                        "${album.title} — ${album.artist}",
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    )
                }
            }
            if (results.isNotEmpty()) {
                item { SectionTitle("Tracks") }
                itemsIndexed(results, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        isPlaying = false,
                        onPlay = { onPlayTracks(results, index) },
                        onFavorite = {},
                        onQueueNext = { onQueueNext(track) },
                        shape = getGroupedItemShape(index, results.size),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    SectionHeader(title)
}
