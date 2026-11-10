package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.domain.model.ReaderCornerContent
import com.huangder.lumibooks.domain.model.ReaderPageCorner
import com.huangder.lumibooks.domain.model.defaultReaderCornerContent
import com.huangder.lumibooks.ui.reader.ReaderUiState
import com.huangder.lumibooks.ui.reader.withReaderCornerContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderCornerCombinationPersistenceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun presetsRoundTripInEveryCornerAndMoveOnlyIdenticalChoices() = runBlocking {
        for (preset in ReaderCornerContent.entries.filter { it.isCombination }) {
            context.dataStore.edit { it.clear() }
            val store = DataStoreManager(context)
            var state = ReaderUiState()
            for (corner in ReaderPageCorner.entries) {
                store.saveReaderCornerContent(corner, preset)
                state = state.withReaderCornerContent(corner, preset)
                val reloaded = DataStoreManager(context)
                val visible = listOf(state.readerTopLeftContent, state.readerTopRightContent,
                    state.readerBottomLeftContent, state.readerBottomRightContent)
                ReaderPageCorner.entries.forEachIndexed { index, position ->
                    assertEquals(visible[index], reloaded.readerCornerContent(position).first())
                }
                assertEquals(preset, reloaded.readerCornerContent(corner).first())
                assertEquals(preset.key, context.dataStore.data.first()[stringPreferencesKey(
                    "reader_${corner.name.lowercase(java.util.Locale.ROOT)}_content")])
            }
        }
    }

    @Test fun overlappingSinglesAndOtherPresetsRemainInPlace() = runBlocking {
        val pairs = listOf(
            ReaderCornerContent.TIME_BATTERY to ReaderCornerContent.BATTERY,
            ReaderCornerContent.TIME_BATTERY to ReaderCornerContent.TIME,
            ReaderCornerContent.PAGE_NUMBER_BOOK_PROGRESS to ReaderCornerContent.PAGE_NUMBER,
            ReaderCornerContent.GLOBAL_PAGE_NUMBER_BOOK_PROGRESS to ReaderCornerContent.GLOBAL_PAGE_NUMBER,
            ReaderCornerContent.CHAPTER_INFO_PAGE_NUMBER to ReaderCornerContent.CHAPTER_INFO,
            ReaderCornerContent.CHAPTER_INFO_BOOK_PROGRESS to ReaderCornerContent.BOOK_PROGRESS,
            ReaderCornerContent.PAGE_NUMBER_BOOK_PROGRESS to ReaderCornerContent.CHAPTER_INFO_BOOK_PROGRESS
        )
        for ((preset, other) in pairs) {
            context.dataStore.edit { it.clear() }
            val store = DataStoreManager(context)
            store.saveReaderCornerContent(ReaderPageCorner.TOP_LEFT, other)
            store.saveReaderCornerContent(ReaderPageCorner.BOTTOM_RIGHT, preset)
            val state = ReaderUiState()
                .withReaderCornerContent(ReaderPageCorner.TOP_LEFT, other)
                .withReaderCornerContent(ReaderPageCorner.BOTTOM_RIGHT, preset)
            assertEquals(other, state.readerTopLeftContent)
            assertEquals(other, store.readerCornerContent(ReaderPageCorner.TOP_LEFT).first())
            // Selecting a single after the overlapping preset is also non-destructive.
            store.saveReaderCornerContent(ReaderPageCorner.TOP_LEFT, other)
            assertEquals(preset, store.readerCornerContent(ReaderPageCorner.BOTTOM_RIGHT).first())
        }
    }

    @Test fun legacyKeysDefaultsAndUnknownValuesKeepTheirMeaning() = runBlocking {
        context.dataStore.edit { it.clear() }
        val store = DataStoreManager(context)
        ReaderPageCorner.entries.forEach {
            assertEquals(defaultReaderCornerContent(it), store.readerCornerContent(it).first())
        }
        val oldKeys = listOf("none", "chapter_info", "book_progress", "chapter_progress",
            "page_number", "global_page_number", "battery", "time")
        for (key in oldKeys + "future_unknown") {
            context.dataStore.edit { it[stringPreferencesKey("reader_top_left_content")] = key }
            assertEquals(ReaderCornerContent.fromKey(key),
                store.readerCornerContent(ReaderPageCorner.TOP_LEFT).first())
        }
        assertEquals(ReaderCornerContent.NONE, ReaderCornerContent.fromKey("future_unknown"))
    }
}
