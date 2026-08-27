package com.lumaread.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LumaDarkColors = darkColorScheme(
    primary = Color(0xFFF4B860),
    onPrimary = Color(0xFF2A1900),
    primaryContainer = Color(0xFF4B330E),
    onPrimaryContainer = Color(0xFFFFDDA8),
    secondary = Color(0xFFBFC8D8),
    background = Color(0xFF0D0F12),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF15181D),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF20242A),
    onSurfaceVariant = Color(0xFFBEC3CC),
    outline = Color(0xFF3A4048)
)

@Composable
fun LumaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LumaDarkColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
