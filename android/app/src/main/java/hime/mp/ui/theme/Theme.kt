package hime.mp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class HimeMotion(val quick: Int = 120, val standard: Int = 260, val pressScale: Float = 0.96f)

val LocalHimeMotion = staticCompositionLocalOf { HimeMotion() }

val HimeShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun HimeTheme(
    themeMode: String = "system",
    dynamicColor: Boolean = true,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val palette = AlbumThemeController.palette.collectAsState().value
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val seed = if (dynamicColor) palette?.primary ?: 0 else 0xFF6750A4.toInt()
    val base = schemeForSeed(seed, dark, false, context)
    val scheme = if (dynamicColor && palette != null) {
        val secondary = Color(palette.secondary)
        val tertiary = Color(palette.tertiary)
        val secondaryContainer = containerOf(secondary, dark)
        val tertiaryContainer = containerOf(tertiary, dark)
        base.copy(
            secondary = secondary,
            onSecondary = contrastingOn(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = contrastingOn(secondaryContainer),
            tertiary = tertiary,
            onTertiary = contrastingOn(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = contrastingOn(tertiaryContainer),
        )
    } else {
        base
    }
    val colors = scheme.animatedColors()
    val motion = if (reducedMotion) HimeMotion(0, 0, 1f) else HimeMotion()
    androidx.compose.runtime.CompositionLocalProvider(LocalHimeMotion provides motion) {
        MaterialTheme(
            colorScheme = colors,
            typography = HimeTypography,
            shapes = HimeShapes,
            content = content,
        )
    }
}
