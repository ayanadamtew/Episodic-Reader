package com.episodicreader.data.db

import com.episodicreader.data.db.entity.LineHeightMode
import com.episodicreader.data.db.entity.TypographyBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaValuesTest {
    @Test
    fun clampFontSizeSnapsTo2SpStepsWithin12To28Range() {
        assertEquals(12, TypographyBounds.clampFontSizeSp(8))
        assertEquals(12, TypographyBounds.clampFontSizeSp(12))
        assertEquals(14, TypographyBounds.clampFontSizeSp(15))
        assertEquals(16, TypographyBounds.clampFontSizeSp(16))
        assertEquals(28, TypographyBounds.clampFontSizeSp(28))
        assertEquals(28, TypographyBounds.clampFontSizeSp(36))
    }

    @Test
    fun lineHeightMultipliersMatchSpec() {
        assertEquals(1.35f, LineHeightMode.multiplierFor(LineHeightMode.COMPACT), 0.001f)
        assertEquals(1.55f, LineHeightMode.multiplierFor(LineHeightMode.STANDARD), 0.001f)
        assertEquals(1.75f, LineHeightMode.multiplierFor(LineHeightMode.RELAXED), 0.001f)
    }

    @Test
    fun schemaGuardsIncludeSingletonAndBoundsTriggers() {
        assertTrue(SchemaGuards.TRIGGER_STATEMENTS.any { it.contains("trg_user_preferences_singleton_insert") })
        assertTrue(SchemaGuards.TRIGGER_STATEMENTS.any { it.contains("trg_user_preferences_font_bounds_insert") })
        assertTrue(SchemaGuards.TRIGGER_STATEMENTS.any { it.contains("trg_paragraphs_seq_non_negative") })
    }
}
