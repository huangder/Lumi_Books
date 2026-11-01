package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.domain.model.ReaderBackgroundPreset
import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import com.huangder.lumibooks.domain.model.ReaderLayoutTarget
import com.huangder.lumibooks.domain.model.ReaderThemeSettings
import com.huangder.lumibooks.domain.model.ReaderThemeSuite
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderBackgroundAssetPersistenceTest {
    private val original = ReaderBackgroundPreset("old", ReaderBackgroundType.IMAGE, "/old.jpg")
    private val added = ReaderBackgroundPreset("new", ReaderBackgroundType.IMAGE, "/new.jpg")
    private val processed = original.copy(processedValue = "/old-blurred.jpg", processedBlurDp = 5f)

    @Test fun finishingOldBlurPreservesNewlyAddedImageAndSelection() = fixture { manager ->
        manager.saveCustomReaderBackgrounds(listOf(original))
        manager.saveReaderThemeSuiteState(listOf(ReaderThemeSuite("photo", "Photo", ReaderThemeSettings())),
            "photo", applyActiveSuite = false)
        manager.addReaderThemeSuiteBackground("photo", ReaderLayoutTarget.READER_LAYOUT, added, false)
        manager.saveProcessedReaderBackground(original, processed)
        assertEquals(listOf(processed, added), manager.customReaderBackgrounds.first())
        val saved = manager.readerThemeSuiteState.first().suites.first { it.id == "photo" }
        assertEquals(added.selectionKey, saved.settingsFor(ReaderLayoutTarget.READER_LAYOUT, false).backgroundSelection)
    }

    @Test fun finishingOldBlurDoesNotResurrectRemovedOrReplaceNewerProcessing() = fixture { manager ->
        manager.saveCustomReaderBackgrounds(listOf(original))
        assertEquals(original, manager.removeUnusedReaderBackground(original.id))
        manager.saveProcessedReaderBackground(original, processed)
        assertEquals(emptyList<ReaderBackgroundPreset>(), manager.customReaderBackgrounds.first())
        val newer = processed.copy(processedBlurDp = 10f)
        manager.saveCustomReaderBackgrounds(listOf(newer))
        manager.saveProcessedReaderBackground(original, processed)
        assertEquals(listOf(newer), manager.customReaderBackgrounds.first())
    }

    @Test fun finishingOldColorSamplingPreservesImagesAddedDuringSampling() = fixture { manager ->
        manager.saveCustomReaderBackgrounds(listOf(original))
        // The reader started sampling the old list before a new photo was saved.
        manager.saveCustomReaderBackgrounds(listOf(original, added))
        val sampled = original.copy(dominantColor = 0xFF123456.toInt())
        manager.saveProcessedReaderBackground(original, sampled)
        assertEquals(listOf(sampled, added), manager.customReaderBackgrounds.first())
    }

    @Test fun cleanupChecksFallbackBookLayoutAndBothModesOfEverySuite() = fixture { manager ->
        val plain = ReaderThemeSettings()
        val photo = plain.copy(backgroundSelection = original.selectionKey)
        val variants = listOf(
            ReaderThemeSuite("photo", "Photo", photo),
            ReaderThemeSuite("photo", "Photo", plain, bookLayoutSettings = photo),
            ReaderThemeSuite("photo", "Photo", plain, lightSettings = photo),
            ReaderThemeSuite("photo", "Photo", plain, darkSettings = photo)
        )
        for (suite in variants) {
            manager.saveCustomReaderBackgrounds(listOf(original))
            manager.saveReaderThemeSuiteState(listOf(suite), "photo", applyActiveSuite = false)
            assertNull(manager.removeUnusedReaderBackground(original.id))
            assertEquals(listOf(original), manager.customReaderBackgrounds.first())
        }
    }

    private fun fixture(block: suspend (DataStoreManager) -> Unit) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        block(DataStoreManager(context))
    }
}
