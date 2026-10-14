package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.domain.model.ReaderCornerContent
import com.huangder.lumibooks.domain.model.ReaderPageCorner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderPageNumberPersistenceTest {
    @Test fun existingPageNumberChoiceAndDefaultRemainChapterPages() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val store = DataStoreManager(context)
        assertEquals(ReaderCornerContent.PAGE_NUMBER, store.readerCornerContent(ReaderPageCorner.BOTTOM_RIGHT).first())
        context.dataStore.edit { it[stringPreferencesKey("reader_top_left_content")] = "page_number" }
        assertEquals(ReaderCornerContent.PAGE_NUMBER,
            DataStoreManager(context).readerCornerContent(ReaderPageCorner.TOP_LEFT).first())
    }

    @Test fun globalAndChapterNumbersCanBeSavedInDifferentCorners() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val store = DataStoreManager(context)
        store.saveReaderCornerContent(ReaderPageCorner.TOP_LEFT, ReaderCornerContent.GLOBAL_PAGE_NUMBER)
        val reloaded = DataStoreManager(context)
        assertEquals(ReaderCornerContent.GLOBAL_PAGE_NUMBER, reloaded.readerCornerContent(ReaderPageCorner.TOP_LEFT).first())
        assertEquals(ReaderCornerContent.PAGE_NUMBER, reloaded.readerCornerContent(ReaderPageCorner.BOTTOM_RIGHT).first())
        store.saveReaderCornerContent(ReaderPageCorner.BOTTOM_LEFT, ReaderCornerContent.GLOBAL_PAGE_NUMBER)
        assertEquals(ReaderCornerContent.NONE, reloaded.readerCornerContent(ReaderPageCorner.TOP_LEFT).first())
        assertEquals(ReaderCornerContent.GLOBAL_PAGE_NUMBER, reloaded.readerCornerContent(ReaderPageCorner.BOTTOM_LEFT).first())
    }
}
