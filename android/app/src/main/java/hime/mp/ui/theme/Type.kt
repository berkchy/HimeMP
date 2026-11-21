package hime.mp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import hime.mp.R

private val GoogleSansFamily = FontFamily(
    Font(R.font.googlesans_regular, FontWeight.Normal),
    Font(R.font.googlesans_medium, FontWeight.Medium),
    Font(R.font.googlesans_medium, FontWeight.SemiBold),
    Font(R.font.googlesans_bold, FontWeight.Bold),
)

val HimeTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = GoogleSansFamily, fontWeight = FontWeight.Bold,
        fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = GoogleSansFamily, fontWeight = FontWeight.Bold,
        fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = GoogleSansFamily, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 34.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = GoogleSansFamily, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 32.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = GoogleSansFamily, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 28.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = GoogleSansFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = GoogleSansFamily, fontWeight = FontWeight.Bold,
        fontSize = 20.sp, lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = GoogleSansFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = GoogleSansFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = GoogleSansFamily, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = GoogleSansFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = GoogleSansFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = GoogleSansFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = GoogleSansFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = GoogleSansFamily, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp,
    ),
)
