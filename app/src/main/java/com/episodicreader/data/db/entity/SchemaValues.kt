package com.episodicreader.data.db.entity

object SourceFormat {
    const val EPUB = "EPUB"
    const val PDF = "PDF"
    const val BUNDLED = "BUNDLED"
}

object BookStatus {
    const val PROCESSING = "PROCESSING"
    const val READY = "READY"
    const val COMPLETED = "COMPLETED"
}

object ParagraphType {
    const val BODY = "BODY"
    const val HEADING = "HEADING"
    const val BLOCKQUOTE = "BLOCKQUOTE"
    const val SCENE_BREAK = "SCENE_BREAK"
}

object LineHeightMode {
    const val COMPACT = "COMPACT"
    const val STANDARD = "STANDARD"
    const val RELAXED = "RELAXED"
}

object ReaderThemeName {
    const val LIGHT = "LIGHT"
    const val SEPIA = "SEPIA"
    const val DARK_HIGH_CONTRAST = "DARK_HIGH_CONTRAST"
}

