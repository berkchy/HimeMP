package hime.mp.lyrics

data class LyricWord(val text: String, val timeMs: Long, val endMs: Long)

data class LyricLine(val timeMs: Long, val text: String, val words: List<LyricWord> = emptyList())

data class LyricsData(val source: String, val lines: List<LyricLine>) {
    val synced: Boolean get() = lines.any { it.timeMs >= 0 }
}

private val lrcTime = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")
private val wordTokens = Regex("""\S+\s*""")
private val whitespace = Regex("""[ \t]+""")

fun distributeWordTiming(lines: List<LyricLine>): List<LyricLine> {
    val sorted = lines.sortedBy { it.timeMs }
    return sorted.mapIndexed { index, line ->
        if (line.timeMs < 0 || line.words.isNotEmpty()) return@mapIndexed line
        val tokens = wordTokens.findAll(line.text).map { it.value }.toList()
        if (tokens.isEmpty()) return@mapIndexed line
        val nextStart = sorted.getOrNull(index + 1)?.timeMs?.takeIf { it > line.timeMs }
        val end = nextStart ?: line.timeMs + tokens.size * 320L
        val span = (end - line.timeMs).coerceAtLeast(tokens.size.toLong())
        val step = span / tokens.size
        val words = tokens.mapIndexed { tokenIndex, token ->
            LyricWord(
                token,
                line.timeMs + tokenIndex.toLong() * step,
                line.timeMs + (tokenIndex + 1L) * step,
            )
        }
        line.copy(words = words)
    }
}

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
    return distributeWordTiming(lines)
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
            val lineEndMs = parseTtmlTime(parser.getAttributeValue(null, "end"))
            val text = StringBuilder()
            val words = mutableListOf<LyricWord>()
            var spanBegin = -1L
            var spanEnd = -1L
            while (true) {
                when (parser.next()) {
                    org.xmlpull.v1.XmlPullParser.TEXT -> {
                        val value = parser.text.orEmpty()
                        text.append(value)
                        if (spanBegin >= 0) {
                            words += LyricWord(value, spanBegin, spanEnd)
                        }
                    }

                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        if (parser.name.equals("span", ignoreCase = true)) {
                            spanBegin = parseTtmlTime(parser.getAttributeValue(null, "begin"))
                            spanEnd = parseTtmlTime(parser.getAttributeValue(null, "end"))
                        }
                    }

                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        if (parser.name.equals("span", ignoreCase = true)) {
                            spanBegin = -1L
                            spanEnd = -1L
                        } else if (parser.name.equals("p", ignoreCase = true)) {
                            break
                        }
                    }

                    org.xmlpull.v1.XmlPullParser.END_DOCUMENT -> break
                }
            }
            val timedWords = words.mapIndexed { index, word ->
                val nextStart = words.getOrNull(index + 1)?.timeMs
                val resolvedEnd = when {
                    word.endMs > word.timeMs -> word.endMs
                    nextStart != null && nextStart > word.timeMs -> nextStart
                    lineEndMs > word.timeMs -> lineEndMs
                    else -> word.timeMs + 200L
                }
                word.copy(endMs = resolvedEnd)
            }
            val lineText = if (timedWords.isNotEmpty()) {
                timedWords.joinToString("") { it.text }
            } else {
                text.toString().replace(whitespace, " ").trim()
            }
            if (lineText.isNotBlank()) {
                val line = LyricLine(timeMs, lineText, timedWords)
                lines += if (timedWords.isEmpty()) distributeWordTiming(listOf(line)).first() else line
            }
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
