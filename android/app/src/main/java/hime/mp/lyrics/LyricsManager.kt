package hime.mp.lyrics

import android.content.Context
import hime.mp.core.Track
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LyricsManager {
    @Volatile
    private var appContext: Context? = null

    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun load(track: Track?): LyricsData = withContext(Dispatchers.IO) { loadBlocking(track) }

    private fun loadBlocking(track: Track?): LyricsData {
        if (track == null) return LyricsData("none", emptyList())
        val context = appContext ?: return LyricsData("none", emptyList())
        val key = cacheKey(track)
        val file = File(File(context.filesDir, "lyrics"), "$key.json")

        readCache(file)?.let { return it }

        val artist = track.artist.ifBlank { track.albumArtist }
        val duration = (track.durationMs / 1_000L).coerceAtLeast(1L)

        val betterUrl = HttpUrl.Builder()
            .scheme("https")
            .host("api.betterlyrics.org")
            .addPathSegment("getLyrics")
            .addQueryParameter("s", track.title)
            .addQueryParameter("a", artist)
            .addQueryParameter("al", track.album)
            .addQueryParameter("d", duration.toString())
            .build()

        fetch(betterUrl.toString())?.let { body ->
            runCatching {
                val ttml = JSONObject(body).optString("ttml").orEmpty()
                val lines = parseTtml(ttml)
                if (lines.isNotEmpty()) return writeCache(file, LyricsData("BetterLyrics", lines))
            }
        }

        val lrclibUrl = HttpUrl.Builder()
            .scheme("https")
            .host("lrclib.net")
            .addPathSegment("api")
            .addPathSegment("get")
            .addQueryParameter("artist_name", artist)
            .addQueryParameter("track_name", track.title)
            .addQueryParameter("album_name", track.album)
            .addQueryParameter("duration", duration.toString())
            .build()

        fetch(lrclibUrl.toString())?.let { body ->
            runCatching {
                val json = JSONObject(body)
                val synced = json.optString("syncedLyrics").orEmpty()
                val plain = json.optString("plainLyrics").orEmpty()
                val lines = if (synced.isNotBlank()) parseLrc(synced) else parsePlainLyrics(plain)
                if (lines.isNotEmpty()) return writeCache(file, LyricsData("LRCLIB", lines))
            }
        }

        return writeCache(file, LyricsData("none", emptyList()))
    }

    private fun fetch(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "HimeMP/0.5.10 (Android)")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }

    private fun readCache(file: File): LyricsData? {
        if (!file.exists()) return null
        val age = System.currentTimeMillis() - file.lastModified()
        val cached = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
        val miss = cached.optBoolean("miss", false)
        if (age > if (miss) 7L * 24 * 60 * 60 * 1_000 else 30L * 24 * 60 * 60 * 1_000) return null
        if (miss) return LyricsData("none", emptyList())
        val array = cached.optJSONArray("lines") ?: return null
        val lines = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONArray(index) ?: return@mapNotNull null
            LyricLine(item.optLong(0, -1L), item.optString(1))
        }.filter { it.text.isNotBlank() }
        if (lines.isEmpty()) return null
        return LyricsData(cached.optString("source", "cache"), lines)
    }

    private fun writeCache(file: File, data: LyricsData): LyricsData {
        runCatching {
            file.parentFile?.mkdirs()
            val array = JSONArray()
            data.lines.forEach { line ->
                array.put(JSONArray().put(line.timeMs).put(line.text))
            }
            val json = JSONObject()
                .put("miss", data.lines.isEmpty())
                .put("source", data.source)
                .put("at", System.currentTimeMillis())
                .put("lines", array)
            file.writeText(json.toString())
        }
        return data
    }

    private fun cacheKey(track: Track): String {
        val raw = listOf(track.title, track.artist.ifBlank { track.albumArtist }, track.album, (track.durationMs / 1_000L).toString())
            .joinToString("|") { normalize(it) }
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}
