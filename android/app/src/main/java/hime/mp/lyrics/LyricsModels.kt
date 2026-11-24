package hime.mp.lyrics

data class LyricLine(val timeMs: Long, val text: String)

data class LyricsData(val source: String, val lines: List<LyricLine>) {
    val synced: Boolean get() = lines.any { it.timeMs >= 0 }
}

private val lrcTime = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")

fun parseLrc(input: String): List<LyricLine> {
    val lines = mutableListOf<LyricLine>()
    input.lineSequence().forEach { raw ->
        val matches = lrcTime.findAll(raw).toList()
        if (matches.isEmpty()) return@forEach
        val text = raw.substring(matches.last().range.last + 1).trim()
        if (text.isEmpty() || text.startsWith("offset:", ignoreCase = true)) return@forEach
        matches.forEach { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val fraction = match.groupValues[3].ifBlank { "0" }.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            lines += LyricLine(minutes * 60_000L + seconds * 1_000L + fraction, text)
        }
    }
    return lines.sortedBy { it.timeMs }
}

fun parsePlainLyrics(input: String): List<LyricLine> =
    input.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { LyricLine(-1L, it) }
        .toList()

fun parseTtml(input: String): List<LyricLine> {
    val lines = mutableListOf<LyricLine>()
    val parser = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()
    parser.setInput(java.io.StringReader(input))
    var event = parser.eventType
    while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
        if (event == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name.equals("p", ignoreCase = true)) {
            val timeMs = parseTtmlTime(parser.getAttributeValue(null, "begin"))
            val text = StringBuilder()
            while (true) {
                when (parser.next()) {
                    org.xmlpull.v1.XmlPullParser.TEXT -> text.append(parser.text)
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        if (parser.name.equals("p", ignoreCase = true)) break
                    }
                    org.xmlpull.v1.XmlPullParser.END_DOCUMENT -> break
                }
            }
            val value = text.toString().replace(Regex("""\s+"""), " ").trim()
            if (value.isNotEmpty()) lines += LyricLine(timeMs, value)
        }
        event = parser.next()
    }
    return lines.sortedBy { it.timeMs }
}

private fun parseTtmlTime(value: String?): Long {
    if (value.isNullOrBlank()) return -1L
    val clock = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?""").matchEntire(value.trim())
    if (clock != null) {
        val hours = clock.groupValues[1].toLongOrNull() ?: 0L
        val minutes = clock.groupValues[2].toLongOrNull() ?: 0L
        val seconds = clock.groupValues[3].toLongOrNull() ?: 0L
        val millis = clock.groupValues[4].ifBlank { "0" }.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return ((hours * 60L + minutes) * 60L + seconds) * 1_000L + millis
    }
    val millis = Regex("""(\d+)ms""").matchEntire(value.trim())?.groupValues?.get(1)?.toLongOrNull()
    return millis ?: -1L
}
