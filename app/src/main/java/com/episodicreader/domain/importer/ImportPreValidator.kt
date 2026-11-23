package com.episodicreader.domain.importer

import com.episodicreader.data.db.entity.SourceType
import java.io.InputStream
import java.security.MessageDigest

data class FileInspection(
    val displayName: String,
    val mimeType: String?,
    val fileSizeBytes: Long,
    val headerBytes: ByteArray,
    val availableStorageBytes: Long,
)

sealed interface PreValidationResult {
    data class Valid(val detectedSourceType: String) : PreValidationResult
    data class Invalid(val error: ImportErrorCode) : PreValidationResult
}

/**
 * Deterministic pre-validator enforcing FR-001, BR-008, ERR-004, ERR-005, and ERR-008:
 *  - Size must satisfy 0 < size <= 50 MB (52,428,800 bytes).
 *  - Free device storage must be >= (3 * size) + 20 MB headroom.
 *  - Magic bytes verified directly (ZIP/EPUB `PK\x03\x04` or PDF `%PDF-`), not trusting filename alone.
 */
object ImportPreValidator {
    const val MAX_FILE_SIZE_BYTES: Long = 50L * 1024L * 1024L
    const val MIN_FREE_STORAGE_HEADROOM_BYTES: Long = 20L * 1024L * 1024L

    fun validate(inspection: FileInspection): PreValidationResult {
        if (inspection.fileSizeBytes <= 0L || inspection.fileSizeBytes > MAX_FILE_SIZE_BYTES) {
            return PreValidationResult.Invalid(ImportErrorCode.ERR_005_FILE_TOO_LARGE_OR_EMPTY)
        }

        val requiredFreeBytes = (inspection.fileSizeBytes * 3L) + MIN_FREE_STORAGE_HEADROOM_BYTES
        if (inspection.availableStorageBytes < requiredFreeBytes) {
            return PreValidationResult.Invalid(ImportErrorCode.ERR_008_INSUFFICIENT_STORAGE)
        }

        val detected = detectFormatByMagicBytes(
            headerBytes = inspection.headerBytes,
            displayName = inspection.displayName,
            mimeType = inspection.mimeType,
        ) ?: return PreValidationResult.Invalid(ImportErrorCode.ERR_004_CORRUPT_FILE)

        return PreValidationResult.Valid(detected)
    }

    fun detectFormatByMagicBytes(
        headerBytes: ByteArray,
        displayName: String,
        mimeType: String?,
    ): String? {
        if (headerBytes.size >= 5 &&
            headerBytes[0] == 0x25.toByte() && // %
            headerBytes[1] == 0x50.toByte() && // P
            headerBytes[2] == 0x44.toByte() && // D
            headerBytes[3] == 0x46.toByte() && // F
            headerBytes[4] == 0x2D.toByte()    // -
        ) {
            return SourceType.PDF
        }

        if (headerBytes.size >= 4 &&
            headerBytes[0] == 0x50.toByte() && // P
            headerBytes[1] == 0x4B.toByte() && // K
            headerBytes[2] == 0x03.toByte() &&
            headerBytes[3] == 0x04.toByte()
        ) {
            val lowerName = displayName.lowercase()
            val isEpubHint = lowerName.endsWith(".epub") ||
                mimeType?.contains("epub", ignoreCase = true) == true ||
                mimeType == "application/octet-stream" ||
                mimeType == "application/zip" ||
                mimeType.isNullOrBlank()
            if (isEpubHint) {
                return SourceType.EPUB
            }
        }
        return null
    }

    fun computeSha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
