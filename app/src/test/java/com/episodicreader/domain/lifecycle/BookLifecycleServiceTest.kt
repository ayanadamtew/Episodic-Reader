package com.episodicreader.domain.lifecycle

import com.episodicreader.data.db.entity.BookEntity
import com.episodicreader.data.db.entity.ImportStatus
import com.episodicreader.data.db.entity.SourceType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookLifecycleServiceTest {

    private class FakeLifecycleStore : BookLifecycleStore {
        val books = mutableMapOf<String, BookEntity>()
        val deletedIds = mutableListOf<String>()
        val resetIds = mutableListOf<String>()

        override suspend fun getBookById(bookId: String): BookEntity? = books[bookId]

        override suspend fun deleteBookById(bookId: String) {
            deletedIds += bookId
            books.remove(bookId)
        }

        override suspend fun resetStarterClassic(bookId: String) {
            resetIds += bookId
            books[bookId] = books[bookId]!!.copy(
                maxReadSeq = -1,
                currentParagraphSeq = 0,
                completedEpisodesCount = 0,
                isHidden = 0,
            )
        }

        override suspend fun setBookHidden(bookId: String, hidden: Boolean) {
            books[bookId] = books[bookId]!!.copy(isHidden = if (hidden) 1 else 0)
        }
    }

    @Test
    fun deletesImportedBookAndPrivateFileButRejectsDeletingStarterClassic() = runTest {
        val store = FakeLifecycleStore()
        val deletedPaths = mutableListOf<String>()
        val service = BookLifecycleService(store) { path ->
            deletedPaths += path
            true
        }

        store.books["starter"] = BookEntity("starter", "White Nights", "Dostoevsky", SourceType.STARTER_CLASSIC, "h1", null, ImportStatus.READY, maxReadSeq = 25, importedAtUtc = 1L)
        store.books["epub1"] = BookEntity("epub1", "Imported", "Author", SourceType.EPUB, "h2", "/app/books/epub1.epub", ImportStatus.READY, importedAtUtc = 2L)

        // Starter classic cannot be deleted
        val starterDelete = service.deleteImportedBook("starter")
        assertTrue(starterDelete is LifecycleResult.Rejected)
        assertTrue("starter" in store.books)

        // Starter classic can be reset and hidden/unhidden
        assertEquals(LifecycleResult.Success, service.resetStarterClassic("starter"))
        assertEquals(-1, store.books["starter"]!!.maxReadSeq)
        assertEquals(LifecycleResult.Success, service.hideStarterClassic("starter"))
        assertEquals(1, store.books["starter"]!!.isHidden)
        assertEquals(LifecycleResult.Success, service.unhideStarterClassic("starter"))
        assertEquals(0, store.books["starter"]!!.isHidden)

        // Imported book is deleted cleanly along with private file
        assertEquals(LifecycleResult.Success, service.deleteImportedBook("epub1"))
        assertEquals(listOf("/app/books/epub1.epub"), deletedPaths)
        assertEquals(listOf("epub1"), store.deletedIds)
    }
}
