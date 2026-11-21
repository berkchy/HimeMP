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
    displayLarge = TextStyle(GoogleSansFamily, FontWeight.Bold, 40.sp, 46.sp, (-0.5).sp),
    displayMedium = TextStyle(GoogleSansFamily, FontWeight.Bold, 34.sp, 40.sp, (-0.25).sp),
    displaySmall = TextStyle(GoogleSansFamily, FontWeight.Bold, 28.sp, 34.sp, 0.sp),
    headlineLarge = TextStyle(GoogleSansFamily, FontWeight.Bold, 26.sp, 32.sp, 0.sp),
    headlineMedium = TextStyle(GoogleSansFamily, FontWeight.Bold, 22.sp, 28.sp, 0.sp),
    headlineSmall = TextStyle(GoogleSansFamily, FontWeight.SemiBold, 20.sp, 26.sp, 0.sp),
    titleLarge = TextStyle(GoogleSansFamily, FontWeight.Bold, 20.sp, 26.sp, 0.sp),
    titleMedium = TextStyle(GoogleSansFamily, FontWeight.SemiBold, 16.sp, 22.sp, 0.15.sp),
    titleSmall = TextStyle(GoogleSansFamily, FontWeight.SemiBold, 14.sp, 20.sp, 0.1.sp),
    bodyLarge = TextStyle(GoogleSansFamily, FontWeight.Normal, 15.sp, 22.sp, 0.25.sp),
    bodyMedium = TextStyle(GoogleSansFamily, FontWeight.Normal, 14.sp, 20.sp, 0.2.sp),
    bodySmall = TextStyle(GoogleSansFamily, FontWeight.Normal, 12.sp, 16.sp, 0.2.sp),
    labelLarge = TextStyle(GoogleSansFamily, FontWeight.SemiBold, 14.sp, 20.sp, 0.1.sp),
    labelMedium = TextStyle(GoogleSansFamily, FontWeight.SemiBold, 12.sp, 16.sp, 0.2.sp),
    labelSmall = TextStyle(GoogleSansFamily, FontWeight.Medium, 11.sp, 14.sp, 0.3.sp),
)
