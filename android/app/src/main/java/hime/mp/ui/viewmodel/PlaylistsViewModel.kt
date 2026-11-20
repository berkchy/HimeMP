package hime.mp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hime.mp.core.Playlist
import hime.mp.core.Track
import hime.mp.data.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlaylistsViewModel : ViewModel() {
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _favorites = MutableStateFlow<List<Track>>(emptyList())
    val favorites: StateFlow<List<Track>> = _favorites.asStateFlow()

    private val _recent = MutableStateFlow<List<Track>>(emptyList())
    val recent: StateFlow<List<Track>> = _recent.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching {
                _playlists.value = MusicRepository.playlists()
                _favorites.value = MusicRepository.favorites()
                _recent.value = MusicRepository.recentlyPlayed()
            }
        }
    }

    fun create(name: String) {
        viewModelScope.launch {
            runCatching { MusicRepository.createPlaylist(name) }
            refresh()
        }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch {
            runCatching { MusicRepository.renamePlaylist(id, name) }
            refresh()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { MusicRepository.deletePlaylist(id) }
            refresh()
        }
    }

    fun addTracks(id: String, trackIds: List<String>) {
        viewModelScope.launch {
            runCatching { MusicRepository.addToPlaylist(id, trackIds) }
            refresh()
        }
    }

    fun moveTrack(id: String, from: Int, to: Int) {
        viewModelScope.launch {
            runCatching { MusicRepository.reorderPlaylist(id, from, to) }
            refresh()
        }
    }

    fun removeTrack(id: String, trackId: String) {
        viewModelScope.launch {
            runCatching { MusicRepository.removeFromPlaylist(id, trackId) }
            refresh()
        }
    }
}
