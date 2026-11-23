package com.episodicreader.domain.importer

import android.content.Context
import android.net.Uri
import com.episodicreader.data.db.entity.BookEntity
import com.episodicreader.data.db.entity.EpisodeEntity
import com.episodicreader.data.db.entity.ImportStatus
import com.episodicreader.data.db.entity.NameAliasEntity
import com.episodicreader.data.db.entity.NameEntity
import com.episodicreader.data.db.entity.NameMentionEntity
import com.episodicreader.data.db.entity.ParagraphEntity
import com.episodicreader.data.db.entity.SourceType
import com.episodicreader.data.repository.AtomicBookIngestionBundle
import com.episodicreader.data.repository.BookRepository
import com.episodicreader.domain.parser.EpubParser
import com.episodicreader.domain.parser.ParseBookResult
import com.episodicreader.domain.parser.PdfTextExtractor
import com.episodicreader.domain.slicer.EpisodeSlicer
import com.episodicreader.domain.slicer.SlicedEpisode
import com.episodicreader.platform.AppClock
import com.episodicreader.platform.SafDocumentHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

data class IndexedNamesOutput(
    val entities: List<NameEntity> = emptyList(),
    val aliases: List<NameAliasEntity> = emptyList(),
    val mentions: List<NameMentionEntity> = emptyList(),
)

fun interface BookNameIndexerHook {
    fun indexBook(bookId: String, episodes: List<EpisodeEntity>, paragraphs: List<ParagraphEntity>): IndexedNamesOutput
}

sealed interface ImportOutcome {
    data class Imported(val bookId: String, val title: String) : ImportOutcome
    data class DuplicateDetected(
        val existingBookId: String,
        val existingTitle: String,
        val stagedFilePath: String,
        val displayName: String,
        val detectedSourceType: String,
        val sha256: String,
    ) : ImportOutcome
    data class Failed(val error: ImportErrorCode) : ImportOutcome
}

/**
 * Coordinates end-to-end offline book ingestion off the main thread (T-14):
 *  1. SAF pre-validation (size, free space, magic bytes).
 *  2. Streaming copy into app-private filesDir/books/ while computing SHA-256.
 *  3. Duplicate detection (BR-009 / DLG-01c) unless forceSeparateCopy = true.
 *  4. PROCESSING placeholder row -> EPUB/PDF parsing -> BR-002 slicing -> T-15 name indexing -> Atomic READY commit.
 */
class BookImportCoordinator(
    private val context: Context,
    private val privateBooksDir: File,
    private val bookRepository: BookRepository,
    private val clock: AppClock,
    private val epubParser: EpubParser = EpubParser(),
    private val pdfTextExtractor: PdfTextExtractor = PdfTextExtractor(),
    private val episodeSlicer: EpisodeSlicer = EpisodeSlicer(),
    var nameIndexerHook: BookNameIndexerHook = BookNameIndexerHook { _, _, _ -> IndexedNamesOutput() },
) {

    suspend fun startImportFromUri(uri: Uri): ImportOutcome = withContext(Dispatchers.IO) {
        val inspection = try {
            SafDocumentHelper.inspectUri(context, uri, privateBooksDir)
        } catch (_: Throwable) {
            return@withContext ImportOutcome.Failed(ImportErrorCode.ERR_004_CORRUPT_FILE)
        }

        val validation = ImportPreValidator.validate(inspection)
        if (validation is PreValidationResult.Invalid) {
            return@withContext ImportOutcome.Failed(validation.error)
        }
        val sourceType = (validation as PreValidationResult.Valid).detectedSourceType
        val extension = if (sourceType == SourceType.PDF) "pdf" else "epub"

        privateBooksDir.mkdirs()
        val stagedFile = File(privateBooksDir, "stage_${UUID.randomUUID()}.$extension.tmp")
        val sha256 = try {
            copyUriAndHash(uri, stagedFile)
        } catch (_: Throwable) {
            stagedFile.delete()
            return@withContext ImportOutcome.Failed(ImportErrorCode.ERR_004_CORRUPT_FILE)
        }

        val existing = bookRepository.findReadyBookByHash(sha256)
        if (existing != null) {
            return@withContext ImportOutcome.DuplicateDetected(
                existingBookId = existing.bookId,
                existingTitle = existing.title,
                stagedFilePath = stagedFile.absolutePath,
                displayName = inspection.displayName,
                detectedSourceType = sourceType,
                sha256 = sha256,
            )
        }

        completeImportFromStagedFile(
            stagedFile = stagedFile,
            displayName = inspection.displayName,
            sourceType = sourceType,
            sha256 = sha256,
        )
    }

    suspend fun importSeparateDuplicateCopy(duplicate: ImportOutcome.DuplicateDetected): ImportOutcome =
        withContext(Dispatchers.IO) {
            val stagedFile = File(duplicate.stagedFilePath)
            if (!stagedFile.exists()) {
                return@withContext ImportOutcome.Failed(ImportErrorCode.ERR_004_CORRUPT_FILE)
            }
            completeImportFromStagedFile(
                stagedFile = stagedFile,
                displayName = duplicate.displayName,
                sourceType = duplicate.detectedSourceType,
                sha256 = duplicate.sha256,
            )
        }

    fun discardStagedFile(stagedFilePath: String) {
        runCatching { File(stagedFilePath).delete() }
    }

    internal suspend fun completeImportFromStagedFile(
        stagedFile: File,
        displayName: String,
        sourceType: String,
        sha256: String,
    ): ImportOutcome {
        val bookId = "book_${UUID.randomUUID()}"
        val ext = if (sourceType == SourceType.PDF) "pdf" else "epub"
        val finalFile = File(privateBooksDir, "$bookId.$ext")
        val nowUtc = clock.nowUtcMillis()

        if (!stagedFile.renameTo(finalFile)) {
            try {
                stagedFile.copyTo(finalFile, overwrite = true)
                stagedFile.delete()
            } catch (_: Throwable) {
                stagedFile.delete()
                finalFile.delete()
                return ImportOutcome.Failed(ImportErrorCode.ERR_008_INSUFFICIENT_STORAGE)
            }
        }

        val placeholderTitle = displayName
            .removeSuffix(".epub").removeSuffix(".EPUB")
            .removeSuffix(".pdf").removeSuffix(".PDF")
            .trim()
            .ifBlank { "Importing Book" }

        val placeholder = BookEntity(
            bookId = bookId,
            title = placeholderTitle,
            author = "Preparing...",
            sourceType = sourceType,
            sourceHashSha256 = sha256,
            localFilePath = finalFile.absolutePath,
            importStatus = ImportStatus.PROCESSING,
            importedAtUtc = nowUtc,
            pdfNoticeDismissed = if (sourceType == SourceType.PDF) 0 else 1,
        )

        bookRepository.insertProcessingPlaceholder(placeholder)

        val parseResult = when (sourceType) {
            SourceType.PDF -> pdfTextExtractor.parse(finalFile, displayName)
            else -> epubParser.parse(finalFile, displayName)
        }

        if (parseResult is ParseBookResult.Failure) {
            finalFile.delete()
            bookRepository.deleteBookById(bookId)
            return ImportOutcome.Failed(parseResult.error)
        }

        val parsedDoc = (parseResult as ParseBookResult.Success).document
        val sliced = episodeSlicer.slice(parsedDoc.paragraphs, fallbackTitle = parsedDoc.title)
        if (sliced.isEmpty()) {
            finalFile.delete()
            bookRepository.deleteBookById(bookId)
            return ImportOutcome.Failed(ImportErrorCode.ERR_011_EMPTY_CONTENT)
        }

        val (episodeEntities, paragraphEntities) = buildEntities(bookId, sliced)
        val indexedNames = nameIndexerHook.indexBook(bookId, episodeEntities, paragraphEntities)

        val readyBook = placeholder.copy(
            title = parsedDoc.title,
            author = parsedDoc.author,
            importStatus = ImportStatus.READY,
            currentEpisodeId = episodeEntities.first().episodeId,
            currentParagraphSeq = episodeEntities.first().startParagraphSeq,
            maxReadSeq = -1,
            completedEpisodesCount = 0,
        )

        bookRepository.commitBookBundleAtomically(
            AtomicBookIngestionBundle(
                book = readyBook,
                episodes = episodeEntities,
                paragraphs = paragraphEntities,
                entities = indexedNames.entities,
                aliases = indexedNames.aliases,
                mentions = indexedNames.mentions,
            ),
        )

        return ImportOutcome.Imported(bookId = bookId, title = readyBook.title)
    }

    private fun buildEntities(
        bookId: String,
        sliced: List<SlicedEpisode>,
    ): Pair<List<EpisodeEntity>, List<ParagraphEntity>> {
        val episodes = mutableListOf<EpisodeEntity>()
        val paragraphs = mutableListOf<ParagraphEntity>()

        for (ep in sliced) {
            val epId = "${bookId}_ep_${ep.episodeIndex}"
            episodes += EpisodeEntity(
                episodeId = epId,
                bookId = bookId,
                episodeIndex = ep.episodeIndex,
                sectionTitle = ep.sectionTitle,
                wordCount = ep.wordCount,
                estReadMinutes = ep.estReadMinutes,
                startParagraphSeq = ep.startParagraphSeq,
                endParagraphSeq = ep.endParagraphSeq,
                recapBulletsJson = null,
                isCompleted = 0,
                completedAtUtc = null,
            )

            for (p in ep.paragraphs) {
                val marksJson = if (p.inlineMarks.isEmpty()) {
                    null
                } else {
                    JsonArray(
                        p.inlineMarks.map { m ->
                            JsonObject(
                                mapOf(
                                    "start" to JsonPrimitive(m.start),
                                    "end" to JsonPrimitive(m.end),
                                    "style" to JsonPrimitive(m.style),
                                ),
                            )
                        },
                    ).toString()
                }
                paragraphs += ParagraphEntity(
                    paragraphId = "${bookId}_p_${p.seqIndex}",
                    bookId = bookId,
                    episodeId = epId,
                    seqIndex = p.seqIndex,
                    paragraphType = p.paragraphType,
                    textContent = p.textContent,
                    inlineMarksJson = marksJson,
                )
            }
        }
        return episodes to paragraphs
    }

    private fun copyUriAndHash(uri: Uri, destination: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Could not open SAF input stream")
        input.use { src ->
            FileOutputStream(destination).use { dst ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = src.read(buf)
                    if (n <= 0) break
                    dst.write(buf, 0, n)
                    digest.update(buf, 0, n)
                }
                dst.flush()
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun computeFileHash(file: File): String =
        FileInputStream(file).use { ImportPreValidator.computeSha256Hex(it) }
}
