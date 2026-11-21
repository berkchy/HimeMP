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

@Composable val Accent: Color get() = MaterialTheme.colorScheme.primary
@Composable val AccentDim: Color get() = MaterialTheme.colorScheme.primaryContainer
@Composable val AccentSurface: Color get() = MaterialTheme.colorScheme.primaryContainer
@Composable val Secondary: Color get() = MaterialTheme.colorScheme.secondary
@Composable val SecondaryDim: Color get() = MaterialTheme.colorScheme.secondaryContainer
@Composable val SecondarySurface: Color get() = MaterialTheme.colorScheme.secondaryContainer
@Composable val Tertiary: Color get() = MaterialTheme.colorScheme.tertiary
@Composable val TertiaryDim: Color get() = MaterialTheme.colorScheme.tertiaryContainer
@Composable val Gray99: Color get() = MaterialTheme.colorScheme.surfaceContainerLowest
@Composable val Gray95: Color get() = MaterialTheme.colorScheme.surfaceContainerLow
@Composable val Gray90: Color get() = MaterialTheme.colorScheme.surfaceContainer
@Composable val Gray85: Color get() = MaterialTheme.colorScheme.surfaceContainerHigh
@Composable val Gray80: Color get() = MaterialTheme.colorScheme.surfaceContainerHighest
@Composable val Gray70: Color get() = MaterialTheme.colorScheme.outlineVariant
@Composable val Gray60: Color get() = MaterialTheme.colorScheme.outline
@Composable val Gray50: Color get() = MaterialTheme.colorScheme.outline
@Composable val Gray40: Color get() = MaterialTheme.colorScheme.onSurfaceVariant
@Composable val Gray30: Color get() = MaterialTheme.colorScheme.onSurface
