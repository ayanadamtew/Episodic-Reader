package com.episodicreader.domain.importer

import com.episodicreader.data.db.entity.BookEntity
import com.episodicreader.data.repository.BookRepository
import com.episodicreader.domain.lifecycle.PrivateFileDeleter
import java.io.File

interface OrphanImportStore {
    suspend fun getProcessingBooks(): List<BookEntity>
    suspend fun deleteBookById(bookId: String)
}

class RoomOrphanImportStore(
    private val bookRepository: BookRepository,
) : OrphanImportStore {
    override suspend fun getProcessingBooks(): List<BookEntity> = bookRepository.getProcessingBooks()
    override suspend fun deleteBookById(bookId: String) = bookRepository.deleteBookById(bookId)
}

/**
 * Startup cleanup guard (T-14 / ERR-006).
 * Removes any stale PROCESSING rows left behind by process death during import
 * and deletes their orphaned private files.
 */
class OrphanImportCleaner(
    private val store: OrphanImportStore,
    private val fileDeleter: PrivateFileDeleter,
    private val privateBooksDir: File? = null,
) {
    suspend fun cleanStaleImports(): Int {
        val staleBooks = store.getProcessingBooks()
        var cleaned = 0
        for (book in staleBooks) {
            book.localFilePath?.let { fileDeleter.deleteIfExists(it) }
            store.deleteBookById(book.bookId)
            cleaned++
        }

        // Also clean any *.tmp staging files left behind in privateBooksDir
        privateBooksDir?.listFiles { file -> file.name.endsWith(".tmp") }?.forEach { tmp ->
            tmp.delete()
        }
        return cleaned
    }
}
