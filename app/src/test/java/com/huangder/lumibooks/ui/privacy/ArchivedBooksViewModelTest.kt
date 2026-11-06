package com.huangder.lumibooks.ui.privacy

import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.repository.BookRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArchivedBooksViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private fun book(id: String, time: Long = 1, hidden: Boolean = false, archived: Boolean = true) = Book(
        id, id, "Author", "book.epub", null, BookFormat.EPUB, time, .4f, createdAt = 1,
        isHidden = hidden, isArchived = archived)
    private val books = MutableStateFlow(listOf(book("older"), book("newer", 2), book("secret", hidden = true), book("shelf", archived = false)))
    private var failRestore = false
    private var failLoad = false
    private var writes = 0
    private val repository = Proxy.newProxyInstance(BookRepository::class.java.classLoader, arrayOf(BookRepository::class.java)) { _, method, args ->
        when (method.name) {
            "getAllBooks" -> if (failLoad) flow<List<Book>> { error("offline") } else books
            "setBooksArchived" -> {
                writes++
                if (failRestore) error("disk error")
                val ids = args!![0] as Collection<*>
                val archived = args[1] as Boolean
                books.value = books.value.map { if (it.id in ids) it.copy(isArchived = archived) else it }
                Unit
            }
            else -> error("Unexpected repository operation: ${method.name}")
        }
    } as BookRepository

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun onlyArchivesAreShownInMostRecentlyReadOrderAndRestoreRemovesTheRow() = runTest(dispatcher) {
        val model = ArchivedBooksViewModel(repository)
        runCurrent()
        assertEquals(listOf("newer", "older"), model.uiState.value.books.map { it.id })
        model.restore("newer"); model.restore("newer"); runCurrent()
        assertEquals(1, writes)
        assertEquals(listOf("older"), model.uiState.value.books.map { it.id })
        assertEquals(R.string.archive_restore_success, model.uiState.value.messageRes)
        assertTrue(books.value.single { it.id == "secret" }.isHidden)
    }

    @Test fun failedRestoreKeepsTheBookAndAllowsAnotherAttempt() = runTest(dispatcher) {
        val model = ArchivedBooksViewModel(repository); runCurrent()
        failRestore = true; model.restore("older"); runCurrent()
        assertTrue(model.uiState.value.books.any { it.id == "older" })
        assertTrue(model.uiState.value.restoringIds.isEmpty())
        assertEquals(R.string.archive_restore_failed, model.uiState.value.errorRes)
        failRestore = false; model.restore("older"); runCurrent()
        assertFalse(model.uiState.value.books.any { it.id == "older" })
        assertNull(model.uiState.value.errorRes)
    }

    @Test fun failedLoadCanBeRetried() = runTest(dispatcher) {
        failLoad = true
        val model = ArchivedBooksViewModel(repository); runCurrent()
        assertEquals(R.string.archive_load_failed, model.uiState.value.errorRes)
        assertFalse(model.uiState.value.isLoading)
        failLoad = false; model.reload(); runCurrent()
        assertEquals(2, model.uiState.value.books.size)
        assertNull(model.uiState.value.errorRes)
    }
}
