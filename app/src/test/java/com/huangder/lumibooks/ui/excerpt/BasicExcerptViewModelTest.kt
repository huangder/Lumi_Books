package com.huangder.lumibooks.ui.excerpt

import androidx.lifecycle.ViewModelStore
import com.huangder.lumibooks.data.local.StoredExcerptPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BasicExcerptViewModelTest {
    @Test
    fun lockedTrialsDoNotPersistOrReplacePreviouslySavedChoice() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val saved = ExcerptSelection(ExcerptStyle.CALENDAR)
            val preferences = MutableStateFlow<StoredExcerptPreferences?>(saved.toStoredPreferences())
            val writes = mutableListOf<StoredExcerptPreferences>()
            var premium = false
            val viewModel = ExcerptViewModel(preferences, { premium }) { writes += it }
            store.put("excerpt", viewModel)

            viewModel.openSession()
            assertNull(viewModel.selection.value)
            runCurrent()
            assertEquals(ExcerptSelection(), viewModel.selection.value)
            viewModel.select(ExcerptSelection(ExcerptStyle.CLASSIC))
            runCurrent()
            assertEquals(ExcerptStyle.CLASSIC, viewModel.selection.value?.style)
            assertEquals(emptyList<StoredExcerptPreferences>(), writes)

            viewModel.openSession()
            runCurrent()
            assertEquals(ExcerptSelection(), viewModel.selection.value)
            assertEquals(saved.toStoredPreferences(), preferences.value)

            premium = true
            viewModel.openSession()
            runCurrent()
            assertEquals(saved, viewModel.selection.value)
            assertEquals(emptyList<StoredExcerptPreferences>(), writes)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun reopeningWaitsForRapidSelectionWritesAndDoesNotWriteDefaults() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val preferences = MutableStateFlow<StoredExcerptPreferences?>(null)
            val writes = mutableListOf<StoredExcerptPreferences>()
            val firstWrite = CompletableDeferred<Unit>()
            val viewModel = ExcerptViewModel(preferences, { true }) {
                if (writes.isEmpty()) firstWrite.await()
                writes += it
                preferences.value = it
            }
            store.put("excerpt", viewModel)
            viewModel.openSession()
            runCurrent()
            assertEquals(ExcerptSelection(), viewModel.selection.value)
            assertEquals(emptyList<StoredExcerptPreferences>(), writes)

            val first = ExcerptSelection(ExcerptStyle.CALENDAR)
            val last = ExcerptSelection(ExcerptStyle.COVER)
            viewModel.select(first)
            runCurrent()
            viewModel.select(ExcerptSelection(ExcerptStyle.ILLUSTRATION))
            viewModel.select(last)
            viewModel.openSession()
            firstWrite.complete(Unit)
            runCurrent()

            assertEquals(3, writes.size)
            assertEquals(first.toStoredPreferences(), writes.first())
            assertEquals(last.toStoredPreferences(), writes.last())
            assertEquals(last, viewModel.selection.value)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
