package com.episodicreader.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * T-21: WCAG 2.1 AA Contrast Verification across Light, Sepia, and Dark High Contrast palettes.
 * Enforces >= 4.5:1 contrast ratio for all body/UI ink, accent, error, and button foreground pairs.
 */
class ThemeContrastTest {

    private fun relativeLuminance(color: Color): Double {
        fun linearize(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linearize(color.red) +
            0.7152 * linearize(color.green) +
            0.0722 * linearize(color.blue)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val l1 = relativeLuminance(foreground)
        val l2 = relativeLuminance(background)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertPaletteMeetsWcagAa(name: String, palette: ReaderColorTokens) {
        val pairs = listOf(
            "primaryInk on background" to (palette.primaryInk to palette.background),
            "primaryInk on surface" to (palette.primaryInk to palette.surface),
            "secondaryInk on background" to (palette.secondaryInk to palette.background),
            "secondaryInk on surface" to (palette.secondaryInk to palette.surface),
            "accent on background" to (palette.accent to palette.background),
            "accent on surface" to (palette.accent to palette.surface),
            "onAccent on accent" to (palette.onAccent to palette.accent),
            "error on background" to (palette.error to palette.background),
            "error on surface" to (palette.error to palette.surface),
            "primaryInk on highlightTint" to (palette.primaryInk to palette.highlightTint),
        )

        for ((label, pair) in pairs) {
            val ratio = contrastRatio(pair.first, pair.second)
            assertTrue(
                "$name palette failed WCAG AA (>= 4.5:1) for $label: actual=${"%.2f".format(ratio)}:1",
                ratio >= 4.5,
            )
        }
    }

    @Test
    fun lightPalette_meetsWcagAa() {
        assertPaletteMeetsWcagAa("Light", ReaderPalettes.Light)
    }

    @Test
    fun sepiaPalette_meetsWcagAa() {
        assertPaletteMeetsWcagAa("Sepia", ReaderPalettes.Sepia)
    }

    @Test
    fun darkHighContrastPalette_meetsWcagAa() {
        assertPaletteMeetsWcagAa("DarkHighContrast", ReaderPalettes.DarkHighContrast)
    }
}
