package com.eazaz.smartnotes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFFB06000),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEEFC3),
    onPrimaryContainer = Color(0xFF3C2800),
    secondaryContainer = Color(0xFFFEEFC3),
    onSecondaryContainer = Color(0xFF3C2800),
    background = Color.White,
    surface = Color.White,
    surfaceContainer = Color(0xFFF1F3F4),
    surfaceContainerHigh = Color(0xFFF1F3F4),
    surfaceContainerLow = Color.White,
    onSurface = Color(0xFF202124),
    onSurfaceVariant = Color(0xFF5F6368),
    outline = Color(0xFFDADCE0),
    outlineVariant = Color(0xFFE0E0E0),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFDD663),
    onPrimary = Color(0xFF3C2800),
    primaryContainer = Color(0xFF41331C),
    onPrimaryContainer = Color(0xFFFEEFC3),
    secondaryContainer = Color(0xFF41331C),
    onSecondaryContainer = Color(0xFFFEEFC3),
    background = Color(0xFF202124),
    surface = Color(0xFF202124),
    surfaceContainer = Color(0xFF2D2E31),
    surfaceContainerHigh = Color(0xFF2D2E31),
    surfaceContainerLow = Color(0xFF202124),
    onSurface = Color(0xFFE8EAED),
    onSurfaceVariant = Color(0xFF9AA0A6),
    outline = Color(0xFF5F6368),
    outlineVariant = Color(0xFF3C4043),
)

/** Keep-style soft note colors. Index 0 = no color. */
object NoteColors {
    val light = listOf(
        Color.Unspecified, Color(0xFFFAAFA8), Color(0xFFF39F76), Color(0xFFFFF8B8),
        Color(0xFFE2F6D3), Color(0xFFB4DDD3), Color(0xFFD4E4ED), Color(0xFFAECCDC),
        Color(0xFFD3BFDB), Color(0xFFF6E2DD), Color(0xFFE9E3D4), Color(0xFFEFEFF1),
    )
    val dark = listOf(
        Color.Unspecified, Color(0xFF77172E), Color(0xFF692B17), Color(0xFF7C4A03),
        Color(0xFF264D3B), Color(0xFF0C625D), Color(0xFF256377), Color(0xFF284255),
        Color(0xFF472E5B), Color(0xFF6C394F), Color(0xFF4B443A), Color(0xFF232427),
    )
    val count get() = light.size

    @Composable
    fun of(index: Int): Color {
        val list = if (isSystemInDarkTheme()) dark else light
        val c = list.getOrElse(index) { Color.Unspecified }
        return if (c == Color.Unspecified) MaterialTheme.colorScheme.surface else c
    }

    /** Colors used for shared images/PDFs (always the light version, easy to read). */
    fun shareArgb(index: Int): Int {
        val c = light.getOrElse(index) { Color.Unspecified }
        return if (c == Color.Unspecified) 0xFFFFFFFF.toInt() else c.toArgb()
    }
}

private val AppTypography = Typography().let { t ->
    t.copy(
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Medium, fontSize = 22.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp),
    )
}

@Composable
fun SmartNotesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}
