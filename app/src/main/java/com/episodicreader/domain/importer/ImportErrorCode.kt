package com.episodicreader.domain.importer

/**
 * Canonical SRS v1.1 Error Matrix codes & calm user-facing messages (ERR-001 through ERR-012).
 */
enum class ImportErrorCode(
    val code: String,
    val title: String,
    val userMessage: String,
) {
    ERR_001_DRM_PROTECTED(
        code = "ERR-001",
        title = "DRM-Protected Book",
        userMessage = "This book is protected by DRM and cannot be opened in Episodic Reader. Only DRM-free EPUB and text-based PDF files are supported.",
    ),
    ERR_002_SCANNED_PDF(
        code = "ERR-002",
        title = "Scanned PDF Not Supported",
        userMessage = "This PDF appears to be a scanned image without selectable text. Episodic Reader supports text-based PDFs and DRM-free EPUBs.",
    ),
    ERR_003_PASSWORD_PDF(
        code = "ERR-003",
        title = "Password-Protected PDF",
        userMessage = "This PDF requires a password to open. Please remove password protection before importing.",
    ),
    ERR_004_CORRUPT_FILE(
        code = "ERR-004",
        title = "Unable to Read File",
        userMessage = "This file appears damaged or does not follow a valid EPUB or PDF structure.",
    ),
    ERR_005_FILE_TOO_LARGE_OR_EMPTY(
        code = "ERR-005",
        title = "Unsupported File Size",
        userMessage = "Please choose a non-empty EPUB or PDF file up to 50 MB in size.",
    ),
    ERR_006_INTERRUPTED_IMPORT(
        code = "ERR-006",
        title = "Previous Import Interrupted",
        userMessage = "An unfinished import was cleaned up automatically. You can import the file again anytime.",
    ),
    ERR_007_STARTER_SEED_FAILED(
        code = "ERR-007",
        title = "Starter Classic Unavailable",
        userMessage = "We couldn't prepare the starter classic on this launch. Please tap Retry to try again.",
    ),
    ERR_008_INSUFFICIENT_STORAGE(
        code = "ERR-008",
        title = "Low Device Storage",
        userMessage = "Not enough free storage is available to safely copy and index this book. Free up space and try again.",
    ),
    ERR_011_EMPTY_CONTENT(
        code = "ERR-011",
        title = "No Readable Text Found",
        userMessage = "We could not extract readable paragraphs from this file.",
    ),
}
