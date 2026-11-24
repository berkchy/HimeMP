package hime.mp.lyrics

import android.content.Context
import hime.mp.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

interface LyricsProvider {
    val name: String
    val priority: Int
    val supportsRelaxed: Boolean get() = true
    suspend fun fetch(query: LyricsQuery): LyricsCandidate?
}

private object LyricsHttp {
    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String? = withContext(Dispatchers.IO) {
        repeat(2) { attempt ->
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", "HimeMP/0.5.28 (Android)")
            headers.forEach { (key, value) -> builder.header(key, value) }
            val body = runCatching {
                client.newCall(builder.build()).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                }
            }.getOrNull()
            if (!body.isNullOrBlank()) return@withContext body
            if (attempt == 0) delay(180)
        }
        null
    }
}

private fun normalizeForMatch(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

private fun matchScore(
    title: String,
    artist: String,
    album: String,
    durationSec: Long,
    query: LyricsQuery,
): Float {
    val qTitle = normalizeForMatch(query.title)
    val qArtist = normalizeForMatch(query.artist)
    val qAlbum = normalizeForMatch(query.album)
    var score = 0f
    when {
        normalizeForMatch(title) == qTitle -> score += 0.45f
        qTitle.isNotBlank() && normalizeForMatch(title).contains(qTitle) -> score += 0.25f
    }
    when {
        normalizeForMatch(artist) == qArtist -> score += 0.3f
        qArtist.isNotBlank() && normalizeForMatch(artist).contains(qArtist) -> score += 0.15f
    }
    if (qAlbum.isNotBlank() && normalizeForMatch(album).contains(qAlbum)) score += 0.1f
    if (query.durationSec > 0 && durationSec > 0 && kotlin.math.abs(durationSec - query.durationSec) <= 3) score += 0.15f
    return score
}

fun relaxedLyricsQuery(query: LyricsQuery): LyricsQuery {
    fun clean(value: String): String = value
        .replace(Regex("""(?i)\s*\([^)]*(remaster|explicit|feat)[^)]*\)"""), " ")
        .replace(Regex("""(?i)\s*\b(remastered?|explicit)\b"""), " ")
        .replace(Regex("""(?i)\s*feat\.?[\s\S]+$"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
    return query.copy(title = clean(query.title), artist = clean(query.artist), album = clean(query.album))
}

private fun String.cleanEmbedded(): String = buildString {
    for (char in this@cleanEmbedded) {
        append(if (char.code in 32..126 || char == '\n' || char == '\r' || char == '\t') char else ' ')
    }
}.replace(Regex(""" {3,}"""), " ").trim()

private fun extractEmbedded(raw: String): String? {
    fun after(marker: String, limit: Int = 200_000): String? {
        val index = raw.indexOf(marker, ignoreCase = true)
        if (index < 0) return null
        val start = index + marker.length
        return raw.substring(start, kotlin.math.min(raw.length, start + limit)).cleanEmbedded()
    }
    fun untilNull(marker: String): String? {
        val index = raw.indexOf(marker, ignoreCase = true)
        if (index < 0) return null
        val start = index + marker.length
        return raw.substring(start, kotlin.math.min(raw.length, start + 200_000))
            .substringBefore("\u0000")
            .substringBefore("\n")
            .cleanEmbedded()
    }
    return listOfNotNull(untilNull("LYRICS="), after("USLT"), after("SYLT"), after("©lyr"), after("lyr"))
        .firstOrNull { it.length > 20 }
}

class EmbeddedLyricsProvider : LyricsProvider {
    override val name = "Embedded"
    override val priority = 100
    override val supportsRelaxed = false

    override suspend fun fetch(query: LyricsQuery): LyricsCandidate? = withContext(Dispatchers.IO) {
        if (query.path.isBlank()) return@withContext null
        val bytes = runCatching {
            RandomAccessFile(query.path, "r").use { file ->
                val size = kotlin.math.min(file.length(), 4_000_000L).toInt()
                val buffer = ByteArray(size)
                file.readFully(buffer, 0, size)
                buffer
            }
        }.getOrNull() ?: return@withContext null
        val text = extractEmbedded(String(bytes, Charsets.ISO_8859_1)) ?: return@withContext null
        val data = parseLyricsText(text).copy(source = name)
        if (data.lines.isEmpty()) null else LyricsCandidate(data, data.confidence)
    }
}

class LocalLrcProvider(private val context: Context) : LyricsProvider {
    override val name = "Local"
    override val priority = 90
    override val supportsRelaxed = false

    override suspend fun fetch(query: LyricsQuery): LyricsCandidate? = withContext(Dispatchers.IO) {
        val file = File(query.path)
        val base = file.nameWithoutExtension
        val external = context.getSharedPreferences("himemp_ui", Context.MODE_PRIVATE)
            .getString("lyricsExternalPath", null).orEmpty()
        val candidates = listOfNotNull(
            external.takeIf { it.isNotBlank() },
            file.parentFile?.let { File(it, "$base.lrc").absolutePath },
            file.parentFile?.let { File(it, "$base.txt").absolutePath },
        )
        for (path in candidates.distinct()) {
            val text = runCatching { File(path).readText(Charsets.UTF_8) }.getOrNull().orEmpty()
            if (text.isBlank()) continue
            val data = parseLyricsText(text).copy(source = name, confidence = 1f)
            if (data.lines.isNotEmpty()) return@withContext LyricsCandidate(data, data.confidence)
        }
        null
    }
}

class LrclibProvider : LyricsProvider {
    override val name = "LRCLIB"
    override val priority = 80

    override suspend fun fetch(query: LyricsQuery): LyricsCandidate? = withContext(Dispatchers.IO) {
        val exact = HttpUrl.Builder()
            .scheme("https").host("lrclib.net")
            .addPathSegment("api").addPathSegment("get")
            .addQueryParameter("artist_name", query.artist)
            .addQueryParameter("track_name", query.title)
            .addQueryParameter("album_name", query.album)
            .addQueryParameter("duration", query.durationSec.toString())
            .build().toString()
        var json = LyricsHttp.get(exact)?.let { runCatching { JSONObject(it) }.getOrNull() }
        var confidence = 1f
        if (json == null || json.lyricsText().isBlank()) {
            val search = HttpUrl.Builder()
                .scheme("https").host("lrclib.net")
                .addPathSegment("api").addPathSegment("search")
                .addQueryParameter("query", listOf(query.artist, query.title).filter { it.isNotBlank() }.joinToString(" "))
                .build().toString()
            val array = runCatching { JSONArray(LyricsHttp.get(search).orEmpty()) }.getOrDefault(JSONArray())
            var best: JSONObject? = null
            var bestScore = 0f
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val score = matchScore(
                    item.optString("trackName"),
                    item.optString("artistName"),
                    item.optString("albumName"),
                    item.optLong("duration"),
                    query,
                )
                if (score > bestScore) {
                    best = item
                    bestScore = score
                }
            }
            json = best
            confidence = bestScore
        }
        val result = json?.toLyricsData(name, confidence) ?: return@withContext null
        if (result.lines.isEmpty()) null else LyricsCandidate(result, result.confidence)
    }
}

class BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics"
    override val priority = 70

    override suspend fun fetch(query: LyricsQuery): LyricsCandidate? = withContext(Dispatchers.IO) {
        for (host in listOf("lyrics-api.boidu.dev", "api.betterlyrics.org")) {
            val url = HttpUrl.Builder()
                .scheme("https").host(host)
                .addPathSegment("getLyrics")
                .addQueryParameter("s", query.title)
                .addQueryParameter("a", query.artist)
                .addQueryParameter("al", query.album)
                .addQueryParameter("d", query.durationSec.toString())
                .build().toString()
            val body = LyricsHttp.get(url) ?: continue
            val ttml = runCatching { JSONObject(body).optString("ttml").orEmpty() }.getOrDefault("")
            if (ttml.isBlank()) continue
            val lines = runCatching { parseTtml(ttml) }.getOrDefault(emptyList())
            if (lines.isEmpty()) continue
            val data = LyricsData(name, lines, LyricsKind.SYNCED, 0.9f)
            return@withContext LyricsCandidate(data, data.confidence)
        }
        null
    }
}

class NeteaseProvider : LyricsProvider {
    override val name = "NetEase"
    override val priority = 60

    override suspend fun fetch(query: LyricsQuery): LyricsCandidate? = withContext(Dispatchers.IO) {
        val headers = mapOf("Referer" to "https://music.163.com/")
        val search = HttpUrl.Builder()
            .scheme("https").host("music.163.com")
            .addPathSegment("api").addPathSegment("search").addPathSegment("get").addPathSegment("web")
            .addQueryParameter("s", listOf(query.artist, query.title).filter { it.isNotBlank() }.joinToString(" "))
            .addQueryParameter("type", "1")
            .addQueryParameter("limit", "5")
            .build().toString()
        val searchBody = LyricsHttp.get(search, headers) ?: return@withContext null
        val songs = runCatching {
            JSONObject(searchBody).optJSONObject("result")?.optJSONArray("songs")
        }.getOrNull() ?: return@withContext null
        var best: JSONObject? = null
        var bestScore = 0f
        for (index in 0 until songs.length()) {
            val song = songs.optJSONObject(index) ?: continue
            val artist = song.optJSONArray("artists")?.optJSONObject(0)?.optString("name").orEmpty()
            val score = matchScore(
                song.optString("name"),
                artist,
                song.optJSONObject("album")?.optString("name").orEmpty(),
                song.optLong("duration") / 1_000L,
                query,
            )
            if (score > bestScore) {
                best = song
                bestScore = score
            }
        }
        val id = best?.optLong("id")?.takeIf { it > 0 } ?: return@withContext null
        val lyricUrl = HttpUrl.Builder()
            .scheme("https").host("music.163.com")
            .addPathSegment("api").addPathSegment("song").addPathSegment("lyric")
            .addQueryParameter("id", id.toString())
            .addQueryParameter("lv", "1")
            .addQueryParameter("kv", "1")
            .addQueryParameter("tv", "-1")
            .build().toString()
        val lyricBody = LyricsHttp.get(lyricUrl, headers) ?: return@withContext null
        val json = runCatching { JSONObject(lyricBody) }.getOrNull() ?: return@withContext null
        val synced = json.optJSONObject("lrc")?.optString("lyric").orEmpty()
        val translated = json.optJSONObject("tlyric")?.optString("lyric").orEmpty()
        val lines = parseLrc(synced).ifEmpty { parsePlainLyrics(translated) }
        if (lines.isEmpty()) return@withContext null
        val kind = if (lines.any { it.timeMs >= 0 }) LyricsKind.SYNCED else LyricsKind.PLAIN
        val data = LyricsData(name, lines, kind, bestScore)
        LyricsCandidate(data, data.confidence)
    }
}

class GeniusProvider : LyricsProvider {
    override val name = "Genius"
    override val priority = 50

    override suspend fun fetch(query: LyricsQuery): LyricsCandidate? = withContext(Dispatchers.IO) {
        val search = HttpUrl.Builder()
            .scheme("https").host("genius.com")
            .addPathSegment("api").addPathSegment("search").addPathSegment("multi")
            .addQueryParameter("q", listOf(query.artist, query.title).filter { it.isNotBlank() }.joinToString(" "))
            .build().toString()
        val body = LyricsHttp.get(search) ?: return@withContext null
        val sections = runCatching {
            JSONObject(body).optJSONObject("response")?.optJSONArray("sections")
        }.getOrNull() ?: return@withContext null
        var bestId = 0L
        var bestScore = 0f
        for (sIndex in 0 until sections.length()) {
            val hits = sections.optJSONObject(sIndex)?.optJSONArray("hits") ?: continue
            for (hIndex in 0 until hits.length()) {
                val result = hits.optJSONObject(hIndex)?.optJSONObject("result") ?: continue
                val artist = result.optJSONObject("primary_artist")?.optString("name").orEmpty()
                val score = matchScore(result.optString("full_title"), artist, "", 0L, query)
                if (score > bestScore) {
                    bestScore = score
                    bestId = result.optLong("id")
                }
            }
        }
        if (bestId <= 0) return@withContext null
        val detailUrl = HttpUrl.Builder()
            .scheme("https").host("genius.com")
            .addPathSegment("api").addPathSegment("songs").addPathSegment(bestId.toString())
            .build().toString()
        val detail = LyricsHttp.get(detailUrl) ?: return@withContext null
        val raw = runCatching {
            JSONObject(detail).optJSONObject("response")?.optJSONObject("song")?.optString("lyrics").orEmpty()
        }.getOrDefault("")
        if (raw.isBlank()) return@withContext null
        val clean = raw
            .replace(Regex("""<[^>]+>"""), "\n")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
        val lines = parsePlainLyrics(clean)
        if (lines.isEmpty()) return@withContext null
        val data = LyricsData(name, lines, LyricsKind.PLAIN, bestScore)
        LyricsCandidate(data, data.confidence)
    }
}

private fun JSONObject.lyricsText(): String {
    val synced = optString("syncedLyrics").orEmpty()
    val plain = optString("plainLyrics").orEmpty()
    return synced.ifBlank { plain }
}

private fun JSONObject.toLyricsData(source: String, confidence: Float): LyricsData {
    val synced = optString("syncedLyrics").orEmpty()
    val plain = optString("plainLyrics").orEmpty()
    val lines = if (synced.isNotBlank()) parseLrc(synced) else parsePlainLyrics(plain)
    val kind = if (synced.isNotBlank()) LyricsKind.SYNCED else LyricsKind.PLAIN
    return LyricsData(source, lines, kind, confidence)
}

fun defaultLyricsProviders(context: Context): List<LyricsProvider> = listOf(
    EmbeddedLyricsProvider(),
    LocalLrcProvider(context),
    LrclibProvider(),
    BetterLyricsProvider(),
    NeteaseProvider(),
    GeniusProvider(),
)

fun queryFromTrack(track: Track, externalPath: String = ""): LyricsQuery = LyricsQuery(
    title = track.title,
    artist = track.artist.ifBlank { track.albumArtist },
    album = track.album,
    durationSec = (track.durationMs / 1_000L).coerceAtLeast(1L),
    path = track.path,
    externalPath = externalPath,
)
