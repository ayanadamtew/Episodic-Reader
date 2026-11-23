package com.episodicreader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.episodicreader.data.db.entity.ThemeMode

@Immutable
data class ReaderColorTokens(
    val themeMode: String,
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val primaryInk: Color,
    val secondaryInk: Color,
    val accent: Color,
    val onAccent: Color,
    val highlightTint: Color,
    val border: Color,
    val error: Color,
)

object ReaderPalettes {
    val Light = ReaderColorTokens(
        themeMode = ThemeMode.LIGHT,
        isDark = false,
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFF7F8FA),
        primaryInk = Color(0xFF1A1C1E),
        secondaryInk = Color(0xFF525866),
        accent = Color(0xFF0F5C5C),
        onAccent = Color(0xFFFFFFFF),
        highlightTint = Color(0xFFE6F4F1),
        border = Color(0xFFE2E5EA),
        error = Color(0xFFB91C1C),
    )

    val Sepia = ReaderColorTokens(
        themeMode = ThemeMode.SEPIA,
        isDark = false,
        background = Color(0xFFF5EFE6),
        surface = Color(0xFFECE3D4),
        primaryInk = Color(0xFF2C2218),
        secondaryInk = Color(0xFF5C4D3C),
        accent = Color(0xFF8C4A1C),
        onAccent = Color(0xFFFFFFFF),
        highlightTint = Color(0xFFE5D5C0),
        border = Color(0xFFD8CCBA),
        error = Color(0xFFA61B1B),
    )

    val DarkHighContrast = ReaderColorTokens(
        themeMode = ThemeMode.DARK_HIGH_CONTRAST,
        isDark = true,
        background = Color(0xFF000000),
        surface = Color(0xFF141414),
        primaryInk = Color(0xFFF4F4F5),
        secondaryInk = Color(0xFFB4B4BC),
        accent = Color(0xFFF59E0B),
        onAccent = Color(0xFF141414),
        highlightTint = Color(0xFF2B2110),
        border = Color(0xFF2E2E32),
        error = Color(0xFFF87171),
    )

    fun forMode(mode: String): ReaderColorTokens = when (mode) {
        ThemeMode.SEPIA -> Sepia
        ThemeMode.DARK_HIGH_CONTRAST -> DarkHighContrast
        else -> Light
    }
}

val LocalReaderColors = staticCompositionLocalOf { ReaderPalettes.Light }

private val ReaderTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun EpisodicReaderTheme(
    themeMode: String = ThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    val palette = ReaderPalettes.forMode(themeMode)
    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            background = palette.background,
            onBackground = palette.primaryInk,
            surface = palette.surface,
            onSurface = palette.primaryInk,
            surfaceVariant = palette.surface,
            onSurfaceVariant = palette.secondaryInk,
            outline = palette.border,
            error = palette.error,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            background = palette.background,
            onBackground = palette.primaryInk,
            surface = palette.surface,
            onSurface = palette.primaryInk,
            surfaceVariant = palette.surface,
            onSurfaceVariant = palette.secondaryInk,
            outline = palette.border,
            error = palette.error,
        )
    }

    CompositionLocalProvider(LocalReaderColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ReaderTypography,
            content = content,
        )
    }
}
