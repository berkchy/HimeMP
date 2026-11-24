package hime.mp.lyrics

import android.content.Context
import hime.mp.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object LyricsManager {
    @Volatile
    private var appContext: Context? = null

    private val memory = ConcurrentHashMap<String, LyricsData>()
    private var providers: List<LyricsProvider> = emptyList()

    fun init(context: Context) {
        appContext = context.applicationContext
        providers = defaultLyricsProviders(appContext!!)
    }

    suspend fun load(track: Track?): LyricsData = withContext(Dispatchers.IO) {
        if (track == null) return@withContext LyricsData("none", emptyList(), LyricsKind.NONE)
        val context = appContext ?: return@withContext LyricsData("error", emptyList(), LyricsKind.ERROR)
        val key = cacheKey(track)
        memory[key]?.let { return@withContext it }

        val file = cacheFile(context, track)
        readDisk(file)?.let {
            memory[key] = it
            return@withContext it
        }

        val externalPath = runCatching {
            context.getSharedPreferences("himemp_ui", Context.MODE_PRIVATE)
                .getString("lyricsExternalPath", null)
                .orEmpty()
        }.getOrDefault("")
        val query = queryFromTrack(track, externalPath)

        var found = search(providers, query)
        if (found == null) {
            found = search(
                providers.filter { it.supportsRelaxed },
                relaxedLyricsQuery(query),
            )
        }

        val result = if (found != null && found.data.lines.isNotEmpty()) {
            found.data.copy(confidence = found.confidence)
        } else {
            LyricsData("none", emptyList(), LyricsKind.NONE)
        }
        writeCache(file, key, result)
        result
    }

    suspend fun refresh(track: Track?): LyricsData = withContext(Dispatchers.IO) {
        if (track == null) return@withContext LyricsData("none", emptyList(), LyricsKind.NONE)
        val context = appContext ?: return@withContext LyricsData("error", emptyList(), LyricsKind.ERROR)
        val key = cacheKey(track)
        memory.remove(key)
        runCatching { cacheFile(context, track).delete() }
        load(track)
    }

    private suspend fun search(
        candidates: List<LyricsProvider>,
        query: LyricsQuery,
    ): LyricsCandidate? {
        for (provider in candidates) {
            val result = runCatching { provider.fetch(query) }.getOrNull() ?: continue
            if (result.data.lines.isNotEmpty()) return result
        }
        return null
    }

    private fun readDisk(file: File): LyricsData? {
        if (!file.exists()) return null
        val age = System.currentTimeMillis() - file.lastModified()
        val cached = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
        val miss = cached.optBoolean("miss", false)
        if (age > if (miss) 24L * 60 * 60 * 1_000 else 30L * 24 * 60 * 60 * 1_000) return null
        if (miss) return LyricsData("none", emptyList(), LyricsKind.NONE)

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

        val kind = runCatching {
            LyricsKind.valueOf(cached.optString("kind", "").uppercase(Locale.ROOT))
        }.getOrDefault(
            if (lines.any { it.timeMs >= 0 }) LyricsKind.SYNCED else LyricsKind.PLAIN,
        )
        return LyricsData(
            source = cached.optString("source", "cache"),
            lines = lines,
            kind = kind,
            confidence = cached.optDouble("confidence", 0.0).toFloat(),
        )
    }

    private fun writeCache(file: File, key: String, data: LyricsData) {
        memory[key] = data
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
                .put("kind", data.kind.name)
                .put("confidence", data.confidence.toDouble())
                .put("at", System.currentTimeMillis())
                .put("lines", array)
            file.writeText(json.toString())
        }
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
