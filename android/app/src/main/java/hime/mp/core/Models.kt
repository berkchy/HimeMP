package hime.mp.core

import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.str(key: String) = optString(key, "")
private fun JSONObject.lng(key: String) = optLong(key, 0L)
private fun JSONObject.num(key: String) = optInt(key, 0)
private fun JSONObject.bool(key: String) = optBoolean(key, false)

private fun arrayOf(json: String): JSONArray = runCatching { JSONArray(json) }.getOrDefault(JSONArray())

data class Track(
    val id: String = "",
    val path: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val year: Int = 0,
    val trackNumber: Int = 0,
    val durationMs: Long = 0L,
    val size: Long = 0L,
    val mtime: Long = 0L,
    val dateAdded: Long = 0L,
    val favorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayed: Long = 0L,
) {
    companion object {
        fun from(json: JSONObject) = Track(
            id = json.str("id"),
            path = json.str("path"),
            title = json.str("title"),
            artist = json.str("artist"),
            album = json.str("album"),
            albumArtist = json.str("albumArtist"),
            genre = json.str("genre"),
            year = json.num("year"),
            trackNumber = json.num("trackNumber"),
            durationMs = json.lng("durationMs"),
            size = json.lng("size"),
            mtime = json.lng("mtime"),
            dateAdded = json.lng("dateAdded"),
            favorite = json.bool("favorite"),
            playCount = json.num("playCount"),
            lastPlayed = json.lng("lastPlayed"),
        )

        fun list(json: String): List<Track> = arrayOf(json).let { array ->
            (0 until array.length()).mapNotNull { runCatching { from(array.getJSONObject(it)) }.getOrNull() }
        }
    }
}

data class Album(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val trackCount: Int = 0,
    val durationMs: Long = 0L,
) {
    companion object {
        fun list(json: String): List<Album> = arrayOf(json).let { array ->
            (0 until array.length()).mapNotNull {
                runCatching {
                    val o = array.getJSONObject(it)
                    Album(o.str("id"), o.str("title"), o.str("artist"), o.num("trackCount"), o.lng("durationMs"))
                }.getOrNull()
            }
        }
    }
}

data class Artist(
    val id: String = "",
    val name: String = "",
    val albumCount: Int = 0,
    val trackCount: Int = 0,
) {
    companion object {
        fun list(json: String): List<Artist> = arrayOf(json).let { array ->
            (0 until array.length()).mapNotNull {
                runCatching {
                    val o = array.getJSONObject(it)
                    Artist(o.str("id"), o.str("name"), o.num("albumCount"), o.num("trackCount"))
                }.getOrNull()
            }
        }
    }
}

data class FolderInfo(
    val path: String = "",
    val name: String = "",
    val trackCount: Int = 0,
) {
    companion object {
        fun list(json: String): List<FolderInfo> = arrayOf(json).let { array ->
            (0 until array.length()).mapNotNull {
                runCatching {
                    val o = array.getJSONObject(it)
                    FolderInfo(o.str("path"), o.str("name"), o.num("trackCount"))
                }.getOrNull()
            }
        }
    }
}

data class Playlist(
    val id: String = "",
    val name: String = "",
    val trackIds: List<String> = emptyList(),
    val createdAt: Long = 0L,
) {
    companion object {
        fun from(json: JSONObject): Playlist {
            val ids = json.optJSONArray("trackIds")
            return Playlist(
                json.str("id"),
                json.str("name"),
                (0 until (ids?.length() ?: 0)).map { ids!!.optString(it) },
                json.lng("createdAt"),
            )
        }

        fun list(json: String): List<Playlist> = arrayOf(json).let { array ->
            (0 until array.length()).mapNotNull { runCatching { from(array.getJSONObject(it)) }.getOrNull() }
        }
    }
}

data class ScanReport(
    val total: Int = 0,
    val added: Int = 0,
    val updated: Int = 0,
    val removed: Int = 0,
) {
    companion object {
        fun from(json: String): ScanReport = runCatching {
            val o = JSONObject(json)
            ScanReport(o.num("total"), o.num("added"), o.num("updated"), o.num("removed"))
        }.getOrDefault(ScanReport())
    }
}

data class LibraryStats(
    val tracks: Int = 0,
    val albums: Int = 0,
    val artists: Int = 0,
    val playlists: Int = 0,
    val favorites: Int = 0,
    val totalDurationMs: Long = 0L,
    val totalBytes: Long = 0L,
) {
    companion object {
        fun from(json: String): LibraryStats = runCatching {
            val o = JSONObject(json)
            LibraryStats(
                o.num("tracks"), o.num("albums"), o.num("artists"), o.num("playlists"),
                o.num("favorites"), o.lng("totalDurationMs"), o.lng("totalBytes"),
            )
        }.getOrDefault(LibraryStats())
    }
}

data class PlaybackState(
    val queue: List<String> = emptyList(),
    val index: Int = -1,
    val trackId: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playing: Boolean = false,
    val repeat: Int = 0,
    val shuffle: Boolean = false,
    val speed: Float = 1f,
    val sleepRemainingMs: Long = -1L,
) {
    companion object {
        fun from(json: String): PlaybackState = runCatching {
            val o = JSONObject(json)
            val ids = o.optJSONArray("queue")
            PlaybackState(
                queue = (0 until (ids?.length() ?: 0)).map { ids!!.optString(it) },
                index = o.num("index"),
                trackId = o.optString("trackId", "").ifBlank { null },
                positionMs = o.lng("positionMs"),
                durationMs = o.lng("durationMs"),
                playing = o.bool("playing"),
                repeat = o.num("repeat"),
                shuffle = o.bool("shuffle"),
                speed = o.optDouble("speed", 1.0).toFloat(),
                sleepRemainingMs = o.optLong("sleepRemainingMs", -1L),
            )
        }.getOrDefault(PlaybackState())
    }
}
