package com.berkchy.himemp.data

import com.berkchy.himemp.core.Album
import com.berkchy.himemp.core.Artist
import com.berkchy.himemp.core.FolderInfo
import com.berkchy.himemp.core.LibraryStats
import com.berkchy.himemp.core.NativeBridge
import com.berkchy.himemp.core.PlaybackState
import com.berkchy.himemp.core.Playlist
import com.berkchy.himemp.core.ScanReport
import com.berkchy.himemp.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** All native calls happen on Dispatchers.IO so scanning/parsing never blocks Compose. */
object MusicRepository {
    suspend fun allowedFolders(): List<String> = io { NativeBridge.getAllowedFolders().toStringList() }

    suspend fun setAllowedFolders(folders: List<String>) = io {
        NativeBridge.setAllowedFolders(folders.toTypedArray())
        Unit
    }

    suspend fun scan(incremental: Boolean): ScanReport = io {
        ScanReport.from(NativeBridge.scan(incremental))
    }

    suspend fun tracks(): List<Track> = io { Track.list(NativeBridge.getTracks()) }

    suspend fun track(id: String): Track? = io {
        runCatching { Track.from(org.json.JSONObject(NativeBridge.getTrack(id))) }.getOrNull()
    }

    suspend fun search(query: String): List<Track> = io { Track.list(NativeBridge.search(query)) }

    suspend fun albums(): List<Album> = io { Album.list(NativeBridge.getAlbums()) }
    suspend fun artists(): List<Artist> = io { Artist.list(NativeBridge.getArtists()) }
    suspend fun folders(): List<FolderInfo> = io { FolderInfo.list(NativeBridge.getFolders()) }

    suspend fun playlists(): List<Playlist> = io { Playlist.list(NativeBridge.getPlaylists()) }
    suspend fun playlistTracks(id: String): List<Track> = io { Track.list(NativeBridge.getPlaylistTracks(id)) }

    suspend fun createPlaylist(name: String): Playlist? = io {
        runCatching { Playlist.from(org.json.JSONObject(NativeBridge.createPlaylist(name))) }.getOrNull()
    }

    suspend fun renamePlaylist(id: String, name: String) = io {
        NativeBridge.renamePlaylist(id, name)
        Unit
    }

    suspend fun deletePlaylist(id: String) = io {
        NativeBridge.deletePlaylist(id)
        Unit
    }

    suspend fun addToPlaylist(id: String, trackIds: List<String>) = io {
        NativeBridge.addToPlaylist(id, trackIds.toTypedArray())
        Unit
    }

    suspend fun removeFromPlaylist(id: String, trackId: String) = io {
        NativeBridge.removeFromPlaylist(id, trackId)
        Unit
    }

    suspend fun favorites(): List<Track> = io { Track.list(NativeBridge.getFavorites()) }
    suspend fun recentlyPlayed(): List<Track> = io { Track.list(NativeBridge.getRecentlyPlayed()) }

    suspend fun setFavorite(id: String, favorite: Boolean) = io {
        NativeBridge.setFavorite(id, favorite)
        Unit
    }

    suspend fun state(): PlaybackState = io { PlaybackState.from(NativeBridge.getState()) }
    suspend fun settings(): Map<String, String> = io {
        runCatching {
            val json = org.json.JSONObject(NativeBridge.getSettings())
            json.keys().asSequence().associateWith { json.optString(it) }
        }.getOrDefault(emptyMap())
    }

    suspend fun setSetting(key: String, value: String) = io {
        NativeBridge.setSetting(key, value)
        Unit
    }

    suspend fun stats(): LibraryStats = io { LibraryStats.from(NativeBridge.libraryStats()) }

    suspend fun clearCaches(): Int = io {
        runCatching { org.json.JSONObject(NativeBridge.clearCaches()).optInt("removed") }.getOrDefault(0)
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        runCatching(block).getOrThrow()
    }

    private fun String.toStringList(): List<String> = runCatching {
        val array = org.json.JSONArray(this)
        (0 until array.length()).map { array.optString(it) }
    }.getOrDefault(emptyList())
}
