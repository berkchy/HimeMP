package hime.mp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hime.mp.core.Album
import hime.mp.core.Artist
import hime.mp.core.FolderInfo
import hime.mp.core.Track
import hime.mp.data.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel : ViewModel() {
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()

    private val _folders = MutableStateFlow<List<FolderInfo>>(emptyList())
    val folders: StateFlow<List<FolderInfo>> = _folders.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            runCatching {
                _tracks.value = MusicRepository.tracks()
                _albums.value = MusicRepository.albums()
                _artists.value = MusicRepository.artists()
                _folders.value = MusicRepository.folders()
            }
            _loading.value = false
        }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            runCatching { MusicRepository.setFavorite(track.id, !track.favorite) }
            refresh()
        }
    }

    fun tracksForAlbum(albumId: String): List<Track> =
        _tracks.value.filter { it.album.equals(albumId, ignoreCase = true) }
}
