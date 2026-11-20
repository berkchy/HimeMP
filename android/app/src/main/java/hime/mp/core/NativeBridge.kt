package hime.mp.core

object NativeBridge {
    interface EventListener {
        fun onEvent(event: String, payload: String)
    }

    init {
        System.loadLibrary("himemp_core")
    }

    external fun init(filesDir: String): Boolean
    external fun shutdown()
    external fun setEventListener(listener: EventListener): Boolean

    external fun getAllowedFolders(): String
    external fun setAllowedFolders(folders: Array<String>): String
    external fun scan(incremental: Boolean): String
    external fun getTracks(): String
    external fun getTrack(id: String): String
    external fun search(query: String): String
    external fun getAlbums(): String
    external fun getArtists(): String
    external fun getFolders(): String

    external fun getPlaylists(): String
    external fun createPlaylist(name: String): String
    external fun renamePlaylist(id: String, name: String): String
    external fun deletePlaylist(id: String): String
    external fun addToPlaylist(id: String, trackIds: Array<String>): String
    external fun removeFromPlaylist(id: String, trackId: String): String
    external fun reorderPlaylist(id: String, from: Int, to: Int): String
    external fun getPlaylistTracks(id: String): String
    external fun setFavorite(id: String, favorite: Boolean): String
    external fun removeFromLibrary(ids: Array<String>): String
    external fun getFavorites(): String
    external fun getRecentlyPlayed(): String

    external fun setQueue(ids: Array<String>, startIndex: Int): String
    external fun queueAdd(ids: Array<String>, at: Int): String
    external fun queueMove(from: Int, to: Int): String
    external fun queueRemove(index: Int): String
    external fun queueClear(): String
    external fun getQueue(): String

    external fun play()
    external fun pause()
    external fun stop()
    external fun next()
    external fun previous()
    external fun seek(positionMs: Long)
    external fun setRepeat(mode: Int)
    external fun setShuffle(enabled: Boolean)
    external fun setSpeed(speed: Float)
    external fun setSleepTimer(durationMs: Long)

    external fun getState(): String
    external fun setSetting(key: String, value: String): String
    external fun getSettings(): String
    external fun clearCaches(): String
    external fun libraryStats(): String
}
