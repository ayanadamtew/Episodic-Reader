package com.episodicreader.domain.lifecycle

import com.episodicreader.data.db.entity.BookEntity
import com.episodicreader.data.db.entity.SourceType
import com.episodicreader.data.repository.BookRepository
import java.io.File

interface BookLifecycleStore {
    suspend fun getBookById(bookId: String): BookEntity?
    suspend fun deleteBookById(bookId: String)
    suspend fun resetStarterClassic(bookId: String)
    suspend fun setBookHidden(bookId: String, hidden: Boolean)
}

class RoomBookLifecycleStore(
    private val bookRepository: BookRepository,
) : BookLifecycleStore {
    override suspend fun getBookById(bookId: String): BookEntity? = bookRepository.getBookById(bookId)
    override suspend fun deleteBookById(bookId: String) = bookRepository.deleteBookById(bookId)
    override suspend fun resetStarterClassic(bookId: String) = bookRepository.resetStarterClassic(bookId)
    override suspend fun setBookHidden(bookId: String, hidden: Boolean) = bookRepository.setBookHidden(bookId, hidden)
}

fun interface PrivateFileDeleter {
    fun deleteIfExists(path: String): Boolean
}

class SafePrivateFileDeleter(
    private val allowedRootDir: File,
) : PrivateFileDeleter {
    override fun deleteIfExists(path: String): Boolean {
        return try {
            val rootCanonical = allowedRootDir.canonicalFile
            val targetCanonical = File(path).canonicalFile
            if (!targetCanonical.path.startsWith(rootCanonical.path + File.separator)) {
                false
            } else if (targetCanonical.exists()) {
                targetCanonical.delete()
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }
}

sealed interface LifecycleResult {
    data object Success : LifecycleResult
    data class Rejected(val reason: String) : LifecycleResult
}

/**
 * Coordinates book deletion (BR-013), starter classic reset, and starter hide/restore (BR-015).
 */
class BookLifecycleService(
    private val store: BookLifecycleStore,
    private val fileDeleter: PrivateFileDeleter,
) {
    /**
     * Deletes an imported book's private source file and derived database records while
     * preserving ReadingSessionLogs (ON DELETE SET NULL). Refuses to delete Starter Classic.
     */
    suspend fun deleteImportedBook(bookId: String): LifecycleResult {
        val book = store.getBookById(bookId)
            ?: return LifecycleResult.Rejected("Book not found.")
        if (book.sourceType == SourceType.STARTER_CLASSIC) {
            return LifecycleResult.Rejected("The starter classic cannot be permanently deleted; hide or reset it instead.")
        }
        book.localFilePath?.let { path ->
            fileDeleter.deleteIfExists(path)
        }
        store.deleteBookById(bookId)
        return LifecycleResult.Success
    }

    suspend fun resetStarterClassic(bookId: String): LifecycleResult {
        val book = store.getBookById(bookId)
            ?: return LifecycleResult.Rejected("Starter classic not found.")
        if (book.sourceType != SourceType.STARTER_CLASSIC) {
            return LifecycleResult.Rejected("Reset is only supported for the starter classic.")
        }
        store.resetStarterClassic(bookId)
        return LifecycleResult.Success
    }

    suspend fun hideStarterClassic(bookId: String): LifecycleResult {
        val book = store.getBookById(bookId)
            ?: return LifecycleResult.Rejected("Starter classic not found.")
        if (book.sourceType != SourceType.STARTER_CLASSIC) {
            return LifecycleResult.Rejected("Only the starter classic can be hidden.")
        }
        store.setBookHidden(bookId, hidden = true)
        return LifecycleResult.Success
    }

    suspend fun unhideStarterClassic(bookId: String): LifecycleResult {
        val book = store.getBookById(bookId)
            ?: return LifecycleResult.Rejected("Starter classic not found.")
        store.setBookHidden(bookId, hidden = false)
        return LifecycleResult.Success
    }
}
