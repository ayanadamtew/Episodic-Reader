package com.episodicreader.domain.importer

import com.episodicreader.data.db.entity.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class ImportPreValidatorTest {

    private val epubHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00)
    private val pdfHeader = "%PDF-1.7\n".toByteArray()

    @Test
    fun acceptsValidEpubAndPdfWithSufficientStorage() {
        val epub = ImportPreValidator.validate(
            FileInspection("classic.epub", "application/epub+zip", 2_000_000L, epubHeader, 200_000_000L),
        )
        assertEquals(PreValidationResult.Valid(SourceType.EPUB), epub)

        val pdf = ImportPreValidator.validate(
            FileInspection("novel.pdf", "application/pdf", 3_000_000L, pdfHeader, 200_000_000L),
        )
        assertEquals(PreValidationResult.Valid(SourceType.PDF), pdf)
    }

    @Test
    fun rejectsEmptyAndOver50MbFilesWithErr005() {
        val empty = ImportPreValidator.validate(
            FileInspection("empty.epub", "application/epub+zip", 0L, epubHeader, 200_000_000L),
        )
        assertEquals(PreValidationResult.Invalid(ImportErrorCode.ERR_005_FILE_TOO_LARGE_OR_EMPTY), empty)

        val oversized = ImportPreValidator.validate(
            FileInspection(
                "huge.pdf",
                "application/pdf",
                ImportPreValidator.MAX_FILE_SIZE_BYTES + 1L,
                pdfHeader,
                500_000_000L,
            ),
        )
        assertEquals(PreValidationResult.Invalid(ImportErrorCode.ERR_005_FILE_TOO_LARGE_OR_EMPTY), oversized)
    }

    @Test
    fun rejectsInsufficientStorageWithErr008() {
        val res = ImportPreValidator.validate(
            FileInspection("book.epub", "application/epub+zip", 10_000_000L, epubHeader, 25_000_000L),
        )
        assertEquals(PreValidationResult.Invalid(ImportErrorCode.ERR_008_INSUFFICIENT_STORAGE), res)
    }

    @Test
    fun rejectsSpoofedNonZipOrNonPdfMagicBytesWithErr004() {
        val fake = ImportPreValidator.validate(
            FileInspection("spoof.epub", "application/epub+zip", 10_000L, "NOT_A_BOOK".toByteArray(), 200_000_000L),
        )
        assertEquals(PreValidationResult.Invalid(ImportErrorCode.ERR_004_CORRUPT_FILE), fake)
    }

    @Test
    fun sha256HashIsDeterministicForDuplicateDetection() {
        val h1 = ImportPreValidator.computeSha256Hex(ByteArrayInputStream("hello book".toByteArray()))
        val h2 = ImportPreValidator.computeSha256Hex(ByteArrayInputStream("hello book".toByteArray()))
        assertEquals(64, h1.length)
        assertEquals(h1, h2)
    }
}
