package com.berkchy.himemp.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.berkchy.himemp.core.Track
import com.berkchy.himemp.data.MusicRepository
import com.berkchy.himemp.ui.components.TrackRow
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    onPlayTracks: (List<Track>, Int) -> Unit,
    onQueueNext: (Track) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }

    LaunchedEffect(query) {
        delay(180)
        results = runCatching { MusicRepository.search(query) }.getOrDefault(emptyList())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search tracks, artists, albums") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        if (results.isEmpty()) {
            Text(
                if (query.isBlank()) "Type to search your library" else "No results",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(results, key = { _, t -> t.id }) { index, track ->
                TrackRow(
                    track = track,
                    isPlaying = false,
                    onPlay = { onPlayTracks(results, index) },
                    onFavorite = { /* handled from library view */ },
                    onQueueNext = { onQueueNext(track) },
                )
            }
        }
    }
}
