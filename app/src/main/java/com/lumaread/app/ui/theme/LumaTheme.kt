package com.lumaread.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

enum class LumaThemeMode { PAPER, SEPIA, DARK }

private val PaperColors = lightColorScheme(
    primary = Color(0xFF7A4D16), onPrimary = Color.White,
    primaryContainer = Color(0xFFF4DFC0), onPrimaryContainer = Color(0xFF392300),
    background = Color(0xFFF8F3E9), onBackground = Color(0xFF27231D),
    surface = Color(0xFFFFFBF4), onSurface = Color(0xFF27231D),
    surfaceVariant = Color(0xFFECE4D6), onSurfaceVariant = Color(0xFF625B50),
    outline = Color(0xFF81786A)
)

private val SepiaColors = lightColorScheme(
    primary = Color(0xFF704214), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8CFA7), onPrimaryContainer = Color(0xFF342006),
    background = Color(0xFFE9D8B8), onBackground = Color(0xFF312719),
    surface = Color(0xFFF2E3C7), onSurface = Color(0xFF312719),
    surfaceVariant = Color(0xFFDDC7A2), onSurfaceVariant = Color(0xFF5F503C),
    outline = Color(0xFF7D6D55)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2BA6B), onPrimary = Color(0xFF402D0D),
    primaryContainer = Color(0xFF584018), onPrimaryContainer = Color(0xFFFFDFAE),
    background = Color(0xFF171511), onBackground = Color(0xFFEDE6DA),
    surface = Color(0xFF211E19), onSurface = Color(0xFFEDE6DA),
    surfaceVariant = Color(0xFF302C25), onSurfaceVariant = Color(0xFFCFC5B6),
    outline = Color(0xFF8E8578)
)

private val LumaTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 15.sp, lineHeight = 22.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 23.sp, lineHeight = 29.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 34.sp)
)

@Composable
fun LumaTheme(mode: LumaThemeMode = LumaThemeMode.PAPER, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = when (mode) {
            LumaThemeMode.PAPER -> PaperColors
            LumaThemeMode.SEPIA -> SepiaColors
            LumaThemeMode.DARK -> DarkColors
        },
        typography = LumaTypography,
        content = content
    )
}
