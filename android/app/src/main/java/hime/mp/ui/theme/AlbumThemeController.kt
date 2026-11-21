package hime.mp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.palette.graphics.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AlbumPalette(val primary: Int, val secondary: Int, val tertiary: Int)

object AlbumThemeController {
    private val _palette = MutableStateFlow<AlbumPalette?>(null)
    val palette: StateFlow<AlbumPalette?> = _palette

    fun update(palette: Palette) {
        val candidates = listOf(
            palette.vibrantSwatch,
            palette.darkVibrantSwatch,
            palette.lightVibrantSwatch,
            palette.mutedSwatch,
            palette.darkMutedSwatch,
            palette.lightMutedSwatch,
            palette.dominantSwatch,
        ).filterNotNull().sortedByDescending { it.population }

        val picked = mutableListOf<Int>()
        for (swatch in candidates) {
            if (picked.none { isSimilar(it, swatch.rgb) }) picked += swatch.rgb
            if (picked.size == 3) break
        }
        val fallbacks = listOf(0xFF6750A4.toInt(), 0xFF625B71.toInt(), 0xFF7D5260.toInt())
        val colors = (picked + fallbacks).distinct().take(3)
        _palette.value = AlbumPalette(colors[0], colors[1], colors[2])
    }

    fun clear() {
        _palette.value = null
    }

    private fun isSimilar(a: Int, b: Int): Boolean {
        val dr = android.graphics.Color.red(a) - android.graphics.Color.red(b)
        val dg = android.graphics.Color.green(a) - android.graphics.Color.green(b)
        val db = android.graphics.Color.blue(a) - android.graphics.Color.blue(b)
        return dr * dr + dg * dg + db * db < 40 * 40
    }
}

internal fun contrastingOn(color: Color): Color {
    val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    return if (luminance > 0.5f) Color(0xFF000000) else Color(0xFFFFFFFF)
}

internal fun containerOf(color: Color, dark: Boolean): Color =
    if (dark) lerp(color, Color.Black, 0.65f) else lerp(color, Color.White, 0.85f)
