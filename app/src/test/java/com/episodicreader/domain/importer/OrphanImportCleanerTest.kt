package com.episodicreader.domain.importer

import com.episodicreader.data.db.entity.BookEntity
import com.episodicreader.data.db.entity.ImportStatus
import com.episodicreader.data.db.entity.SourceType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OrphanImportCleanerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun cleansStaleProcessingBooksAndTemporaryStageFilesOnStartup() = runTest {
        val booksDir = tempFolder.newFolder("books")
        val orphanEpub = File(booksDir, "orphan.epub").apply { writeText("partial") }
        val tmpStage = File(booksDir, "stage_123.epub.tmp").apply { writeText("stage") }

        val stale = mutableListOf(
            BookEntity(
                bookId = "b_stale",
                title = "Interrupted",
                author = "Preparing...",
                sourceType = SourceType.EPUB,
                sourceHashSha256 = "abc",
                localFilePath = orphanEpub.absolutePath,
                importStatus = ImportStatus.PROCESSING,
                importedAtUtc = 100L,
            ),
        )
        val deletedIds = mutableListOf<String>()

        val store = object : OrphanImportStore {
            override suspend fun getProcessingBooks(): List<BookEntity> = stale.toList()
            override suspend fun deleteBookById(bookId: String) {
                deletedIds += bookId
                stale.removeAll { it.bookId == bookId }
            }
        }

        val cleaner = OrphanImportCleaner(
            store = store,
            fileDeleter = { path -> File(path).delete() },
            privateBooksDir = booksDir,
        )

        val cleanedCount = cleaner.cleanStaleImports()
        assertEquals(1, cleanedCount)
        assertEquals(listOf("b_stale"), deletedIds)
        assertFalse(orphanEpub.exists())
        assertFalse(tmpStage.exists())
    }
}
