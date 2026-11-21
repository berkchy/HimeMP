package hime.mp.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ThemeColorAnimation = tween<Color>(
    durationMillis = 300,
    easing = FastOutSlowInEasing,
)

@Composable
private fun animateThemeColor(target: Color, label: String): Color =
    animateColorAsState(
        targetValue = target,
        animationSpec = ThemeColorAnimation,
        label = label,
    ).value

@Composable
internal fun ColorScheme.animatedColors(): ColorScheme = ColorScheme(
    primary = animateThemeColor(primary, "primary"),
    onPrimary = animateThemeColor(onPrimary, "onPrimary"),
    primaryContainer = animateThemeColor(primaryContainer, "primaryContainer"),
    onPrimaryContainer = animateThemeColor(onPrimaryContainer, "onPrimaryContainer"),
    inversePrimary = animateThemeColor(inversePrimary, "inversePrimary"),
    secondary = animateThemeColor(secondary, "secondary"),
    onSecondary = animateThemeColor(onSecondary, "onSecondary"),
    secondaryContainer = animateThemeColor(secondaryContainer, "secondaryContainer"),
    onSecondaryContainer = animateThemeColor(onSecondaryContainer, "onSecondaryContainer"),
    tertiary = animateThemeColor(tertiary, "tertiary"),
    onTertiary = animateThemeColor(onTertiary, "onTertiary"),
    tertiaryContainer = animateThemeColor(tertiaryContainer, "tertiaryContainer"),
    onTertiaryContainer = animateThemeColor(onTertiaryContainer, "onTertiaryContainer"),
    background = animateThemeColor(background, "background"),
    onBackground = animateThemeColor(onBackground, "onBackground"),
    surface = animateThemeColor(surface, "surface"),
    onSurface = animateThemeColor(onSurface, "onSurface"),
    surfaceVariant = animateThemeColor(surfaceVariant, "surfaceVariant"),
    onSurfaceVariant = animateThemeColor(onSurfaceVariant, "onSurfaceVariant"),
    surfaceTint = animateThemeColor(surfaceTint, "surfaceTint"),
    inverseSurface = animateThemeColor(inverseSurface, "inverseSurface"),
    inverseOnSurface = animateThemeColor(inverseOnSurface, "inverseOnSurface"),
    error = animateThemeColor(error, "error"),
    onError = animateThemeColor(onError, "onError"),
    errorContainer = animateThemeColor(errorContainer, "errorContainer"),
    onErrorContainer = animateThemeColor(onErrorContainer, "onErrorContainer"),
    outline = animateThemeColor(outline, "outline"),
    outlineVariant = animateThemeColor(outlineVariant, "outlineVariant"),
    scrim = animateThemeColor(scrim, "scrim"),
    surfaceBright = animateThemeColor(surfaceBright, "surfaceBright"),
    surfaceDim = animateThemeColor(surfaceDim, "surfaceDim"),
    surfaceContainer = animateThemeColor(surfaceContainer, "surfaceContainer"),
    surfaceContainerHigh = animateThemeColor(surfaceContainerHigh, "surfaceContainerHigh"),
    surfaceContainerHighest = animateThemeColor(surfaceContainerHighest, "surfaceContainerHighest"),
    surfaceContainerLow = animateThemeColor(surfaceContainerLow, "surfaceContainerLow"),
    surfaceContainerLowest = animateThemeColor(surfaceContainerLowest, "surfaceContainerLowest"),
)
