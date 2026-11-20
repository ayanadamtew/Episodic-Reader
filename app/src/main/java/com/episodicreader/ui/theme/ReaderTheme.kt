package com.episodicreader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightReaderColors = lightColorScheme(
    primary = Color(0xFF0F5C5C),
    onPrimary = Color.White,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFF7F8FA),
    onSurface = Color(0xFF1A1C1E),
    onSurfaceVariant = Color(0xFF525866),
    outlineVariant = Color(0xFFE2E5EA),
    error = Color(0xFFB91C1C),
)

@Composable
fun EpisodicReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightReaderColors,
        content = content,
    )
}

