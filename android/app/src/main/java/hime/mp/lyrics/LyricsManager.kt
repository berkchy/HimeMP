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

private data class FetchResult(val body: String? = null, val notFound: Boolean = false)

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

    suspend fun refresh(track: Track?): LyricsData = withContext(Dispatchers.IO) {
        val context = appContext
        if (track == null || context == null) return@withContext LyricsData("error", emptyList())
        runCatching { cacheFile(context, track).delete() }
        loadBlocking(track)
    }

    private fun loadBlocking(track: Track?): LyricsData {
        if (track == null) return LyricsData("none", emptyList())
        val context = appContext ?: return LyricsData("error", emptyList())
        val file = cacheFile(context, track)

        readCache(file)?.let { return it }

        val artist = track.artist.ifBlank { track.albumArtist }
        val duration = (track.durationMs / 1_000L).coerceAtLeast(1L)

        var betterNotFound = false
        for (host in listOf("lyrics-api.boidu.dev", "api.betterlyrics.org")) {
            val betterUrl = HttpUrl.Builder()
                .scheme("https")
                .host(host)
                .addPathSegment("getLyrics")
                .addQueryParameter("s", track.title)
                .addQueryParameter("a", artist)
                .addQueryParameter("al", track.album)
                .addQueryParameter("d", duration.toString())
                .build()

            val better = fetch(betterUrl.toString())
            betterNotFound = better.notFound
            if (better.body != null) {
                val lines = runCatching {
                    parseTtml(JSONObject(better.body).optString("ttml").orEmpty())
                }.getOrDefault(emptyList())
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

        val lrclib = fetch(lrclibUrl.toString())
        if (lrclib.body != null) {
            val lines = runCatching {
                val json = JSONObject(lrclib.body)
                val synced = json.optString("syncedLyrics").orEmpty()
                val plain = json.optString("plainLyrics").orEmpty()
                if (synced.isNotBlank()) parseLrc(synced) else parsePlainLyrics(plain)
            }.getOrDefault(emptyList())
            if (lines.isNotEmpty()) return writeCache(file, LyricsData("LRCLIB", lines))
        }

        val notFound = betterNotFound && lrclib.notFound
        return writeCache(file, LyricsData(if (notFound) "none" else "error", emptyList()), cacheMiss = notFound)
    }

    private fun fetch(url: String): FetchResult {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "HimeMP/0.5.17 (Android)")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> FetchResult(body = response.body?.string())
                    response.code == 404 -> FetchResult(notFound = true)
                    else -> FetchResult()
                }
            }
        }.getOrDefault(FetchResult())
    }

    private fun readCache(file: File): LyricsData? {
        if (!file.exists()) return null
        val age = System.currentTimeMillis() - file.lastModified()
        val cached = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
        val miss = cached.optBoolean("miss", false)
        if (age > if (miss) 24L * 60 * 60 * 1_000 else 30L * 24 * 60 * 60 * 1_000) return null
        if (miss) return LyricsData("none", emptyList())
        val array = cached.optJSONArray("lines") ?: return null
        val lines = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONArray(index) ?: return@mapNotNull null
            val wordArray = item.optJSONArray(2)
            val words = if (wordArray != null) {
                (0 until wordArray.length()).mapNotNull { wordIndex ->
                    val word = wordArray.optJSONArray(wordIndex) ?: return@mapNotNull null
                    LyricWord(word.optString(0), word.optLong(1, -1L), word.optLong(2, -1L))
                }
            } else {
                emptyList()
            }
            LyricLine(item.optLong(0, -1L), item.optString(1), words)
        }.filter { it.text.isNotBlank() }
        if (lines.isEmpty()) return null
        return LyricsData(cached.optString("source", "cache"), lines)
    }

    private fun writeCache(file: File, data: LyricsData, cacheMiss: Boolean = data.lines.isNotEmpty()): LyricsData {
        if (data.lines.isEmpty() && !cacheMiss) return data
        runCatching {
            file.parentFile?.mkdirs()
            val array = JSONArray()
            data.lines.forEach { line ->
                val words = JSONArray()
                line.words.forEach { word ->
                    words.put(JSONArray().put(word.text).put(word.timeMs).put(word.endMs))
                }
                array.put(JSONArray().put(line.timeMs).put(line.text).put(words))
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

    private fun cacheFile(context: Context, track: Track): File =
        File(File(context.filesDir, "lyrics"), "${cacheKey(track)}.json")

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
