package hime.mp.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

val keyColorOptions = listOf(
    0xFFF44336.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(),
    0xFF673AB7.toInt(), 0xFF3F51B5.toInt(), 0xFF2196F3.toInt(),
    0xFF00BCD4.toInt(), 0xFF009688.toInt(), 0xFF4CAF50.toInt(),
    0xFFFFEB3B.toInt(), 0xFFFFC107.toInt(), 0xFFFF9800.toInt(),
    0xFF795548.toInt(), 0xFF607D8F.toInt(), 0xFFFF9CA8.toInt(),
)

fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0C0C0C),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    surfaceContainerHighest = Color(0xFF242424),
)

fun schemeForSeed(keyColor: Int, isDark: Boolean, isAmoled: Boolean, context: Context): ColorScheme {
    val base = if (keyColor == 0) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (isDark) darkColorScheme() else lightColorScheme()
        }
    } else {
        createM3SchemeFromSeed(Color(keyColor), isDark, isAmoled)
    }
    return if (isDark && isAmoled) base.toAmoled() else base
}

private fun createM3SchemeFromSeed(seed: Color, isDark: Boolean, isAmoled: Boolean): ColorScheme {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
    val h = hsv[0]
    val s = hsv[1].coerceIn(0.2f, 1f)
    return if (isDark) {
        val primary = Color.hsv(h, (s * 0.45f).coerceIn(0.15f, 0.6f), 0.95f)
        val primaryContainer = Color.hsv(h, (s * 0.75f).coerceIn(0.3f, 0.85f), 0.40f)
        val onPrimaryContainer = Color.hsv(h, (s * 0.25f).coerceIn(0.1f, 0.4f), 0.95f)
        val secondary = Color.hsv((h + 15f) % 360f, (s * 0.35f).coerceIn(0.15f, 0.5f), 0.85f)
        val secondaryContainer = Color.hsv((h + 15f) % 360f, (s * 0.6f).coerceIn(0.25f, 0.7f), 0.35f)
        val tertiary = Color.hsv((h + 45f) % 360f, (s * 0.35f).coerceIn(0.15f, 0.5f), 0.85f)
        val tertiaryContainer = Color.hsv((h + 45f) % 360f, (s * 0.6f).coerceIn(0.25f, 0.7f), 0.35f)
        val surfaceContainer = if (isAmoled) Color(0xFF141414) else Color.hsv(h, 0.08f, 0.14f)
        val surfaceContainerHigh = if (isAmoled) Color(0xFF1C1C1C) else Color.hsv(h, 0.08f, 0.18f)
        val surfaceContainerHighest = if (isAmoled) Color(0xFF242424) else Color.hsv(h, 0.08f, 0.22f)
        val bg = if (isAmoled) Color.Black else Color.hsv(h, 0.06f, 0.09f)
        darkColorScheme(
            primary = primary,
            onPrimary = Color.hsv(h, 0.9f, 0.2f),
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = Color.hsv((h + 15f) % 360f, 0.9f, 0.2f),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = Color.hsv((h + 15f) % 360f, 0.2f, 0.95f),
            tertiary = tertiary,
            onTertiary = Color.hsv((h + 45f) % 360f, 0.9f, 0.2f),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = Color.hsv((h + 45f) % 360f, 0.2f, 0.95f),
            background = bg,
            onBackground = Color(0xFFE6E0E9),
            surface = bg,
            onSurface = Color(0xFFE6E0E9),
            surfaceVariant = Color.hsv(h, 0.15f, 0.28f),
            onSurfaceVariant = Color(0xFFCAC4D0),
            surfaceContainerLowest = if (isAmoled) Color.Black else Color.hsv(h, 0.06f, 0.06f),
            surfaceContainerLow = if (isAmoled) Color(0xFF0C0C0C) else Color.hsv(h, 0.06f, 0.11f),
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
        )
    } else {
        val primary = Color.hsv(h, (s * 0.85f).coerceIn(0.5f, 1f), 0.65f)
        val primaryContainer = Color.hsv(h, (s * 0.25f).coerceIn(0.1f, 0.4f), 0.93f)
        val onPrimaryContainer = Color.hsv(h, (s * 0.95f).coerceIn(0.6f, 1f), 0.20f)
        val secondary = Color.hsv((h + 15f) % 360f, (s * 0.6f).coerceIn(0.3f, 0.8f), 0.50f)
        val secondaryContainer = Color.hsv((h + 15f) % 360f, (s * 0.2f).coerceIn(0.1f, 0.35f), 0.92f)
        val tertiary = Color.hsv((h + 45f) % 360f, (s * 0.6f).coerceIn(0.3f, 0.8f), 0.50f)
        val tertiaryContainer = Color.hsv((h + 45f) % 360f, (s * 0.2f).coerceIn(0.1f, 0.35f), 0.92f)
        val bg = Color.hsv(h, 0.02f, 0.99f)
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = Color.hsv((h + 15f) % 360f, 0.9f, 0.2f),
            tertiary = tertiary,
            onTertiary = Color.White,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = Color.hsv((h + 45f) % 360f, 0.9f, 0.2f),
            background = bg,
            onBackground = Color(0xFF1D1B20),
            surface = bg,
            onSurface = Color(0xFF1D1B20),
            surfaceVariant = Color.hsv(h, 0.1f, 0.90f),
            onSurfaceVariant = Color(0xFF49454F),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color.hsv(h, 0.03f, 0.97f),
            surfaceContainer = Color.hsv(h, 0.04f, 0.95f),
            surfaceContainerHigh = Color.hsv(h, 0.04f, 0.92f),
            surfaceContainerHighest = Color.hsv(h, 0.04f, 0.89f),
        )
    }
}
