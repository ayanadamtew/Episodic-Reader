package com.episodicreader.data.db.entity

object SourceType {
    const val STARTER_CLASSIC = "STARTER_CLASSIC"
    const val EPUB = "EPUB"
    const val PDF = "PDF"
}

object ImportStatus {
    const val PROCESSING = "PROCESSING"
    const val READY = "READY"
    const val FAILED = "FAILED"
}

object ParagraphType {
    const val BODY = "BODY"
    const val HEADING = "HEADING"
    const val BLOCKQUOTE = "BLOCKQUOTE"
    const val SCENE_BREAK = "SCENE_BREAK"
}

object EntitySourceKind {
    const val CURATED = "CURATED"
    const val INDEXED = "INDEXED"
}

object ThemeMode {
    const val LIGHT = "LIGHT"
    const val SEPIA = "SEPIA"
    const val DARK_HIGH_CONTRAST = "DARK_HIGH_CONTRAST"
}

object LineHeightMode {
    const val COMPACT = "COMPACT"
    const val STANDARD = "STANDARD"
    const val RELAXED = "RELAXED"

    fun multiplierFor(mode: String): Float = when (mode) {
        COMPACT -> 1.35f
        RELAXED -> 1.75f
        else -> 1.55f
    }
}

object InlineMarkType {
    const val BOLD = "BOLD"
    const val ITALIC = "ITALIC"
}

object StarterClassicIds {
    const val WHITE_NIGHTS_BOOK_ID = "starter_white_nights_dostoevsky_v1"
}

object TypographyBounds {
    const val MIN_FONT_SIZE_SP = 12
    const val MAX_FONT_SIZE_SP = 28
    const val DEFAULT_FONT_SIZE_SP = 16
    const val FONT_SIZE_STEP_SP = 2

    fun clampFontSizeSp(value: Int): Int {
        val clamped = value.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
        val offset = clamped - MIN_FONT_SIZE_SP
        val snapped = MIN_FONT_SIZE_SP + (offset / FONT_SIZE_STEP_SP) * FONT_SIZE_STEP_SP
        return snapped.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
    }
}
