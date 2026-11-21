package hime.mp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AlertRed = Color(0xFFFF8A80)
val WarnYellow = Color(0xFFFFB871)
val SuccessGreen = Color(0xFF7BD88F)
val SuccessGreenDim = Color(0xFF173D24)
val Black = Color(0xFF0C0E13)
val White = Color(0xFFF7F9FC)

val Accent: Color
    @Composable get() = MaterialTheme.colorScheme.primary
val AccentDim: Color
    @Composable get() = MaterialTheme.colorScheme.primaryContainer
val AccentSurface: Color
    @Composable get() = MaterialTheme.colorScheme.primaryContainer
val Secondary: Color
    @Composable get() = MaterialTheme.colorScheme.secondary
val SecondaryDim: Color
    @Composable get() = MaterialTheme.colorScheme.secondaryContainer
val SecondarySurface: Color
    @Composable get() = MaterialTheme.colorScheme.secondaryContainer
val Tertiary: Color
    @Composable get() = MaterialTheme.colorScheme.tertiary
val TertiaryDim: Color
    @Composable get() = MaterialTheme.colorScheme.tertiaryContainer
val Gray99: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerLowest
val Gray95: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow
val Gray90: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainer
val Gray85: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
val Gray80: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest
val Gray70: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant
val Gray60: Color
    @Composable get() = MaterialTheme.colorScheme.outline
val Gray50: Color
    @Composable get() = MaterialTheme.colorScheme.outline
val Gray40: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Gray30: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface
