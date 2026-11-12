package com.huangder.lumibooks.ui.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.dataStore
import com.huangder.lumibooks.domain.model.ReaderBackgroundPreset
import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import com.huangder.lumibooks.domain.model.ReaderLayoutTarget
import com.huangder.lumibooks.domain.model.ReaderThemeSettings
import com.huangder.lumibooks.domain.model.ReaderThemeSuite
import com.huangder.lumibooks.domain.model.ReaderThemeSuites
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderThemeBackgroundPersistenceTest {
    private val layout = ReaderLayoutTarget.READER_LAYOUT

    @Test fun acceptedImageEditSurvivesClosingEditor() = fixture { manager, model, await ->
        model.beginEditing("photo")
        val updated = model.uiState.value.editingSettings!!.copy(backgroundImageOpacity = 0.42f)
        model.updateTheme(updated)
        model.closeEditor()
        await { manager.readerThemeSuiteState.first().suites.first { it.id == "photo" }
            .settingsFor(layout, false) == updated }
        val reloaded = DataStoreManager(context).readerThemeSuiteState.first().suites.first { it.id == "photo" }
        assertEquals("custom:shared", reloaded.settingsFor(layout, false).backgroundSelection)
        assertEquals(0.42f, reloaded.settingsFor(layout, false).backgroundImageOpacity)
    }

    @Test fun rapidModeChangesPersistBothAcceptedImageEdits() = fixture { manager, model, await ->
        model.beginEditing("photo")
        model.updateTheme(model.uiState.value.editingSettings!!.copy(backgroundImageOpacity = 0.3f))
        model.selectEditingMode(true)
        model.updateTheme(model.uiState.value.editingSettings!!.copy(backgroundImageOpacity = 0.8f))
        model.closeEditor()
        await {
            val suite = manager.readerThemeSuiteState.first().suites.first { it.id == "photo" }
            suite.settingsFor(layout, false).backgroundImageOpacity == 0.3f &&
                suite.settingsFor(layout, true).backgroundImageOpacity == 0.8f
        }
    }

    @Test fun publisherLayoutModesPersistAcrossEditorCloseAndResetIndependently() = fixture { manager, model, await ->
        val book = ReaderLayoutTarget.BOOK_LAYOUT
        model.beginEditing("photo")
        model.selectEditingLayout(book)
        val light = model.uiState.value.editingSettings!!.copy(fontSize = 19f, marginLeft = 24f)
        model.updateTheme(light)
        model.selectEditingMode(true)
        val dark = model.uiState.value.editingSettings!!.copy(fontSize = 25f, marginLeft = 12f)
        model.updateTheme(dark)
        model.selectEditingLayout(layout)
        model.closeEditor()
        await {
            val saved = manager.readerThemeSuiteState.first().suites.first { it.id == "photo" }
            saved.settingsFor(book, false) == light && saved.settingsFor(book, true) == dark
        }
        val saved = DataStoreManager(context).readerThemeSuiteState.first().suites.first { it.id == "photo" }
        assertEquals("custom:shared", saved.settingsFor(layout, false).backgroundSelection)
        manager.resetAdvancedReaderSettings("photo", book, true)
        val reset = manager.readerThemeSuiteState.first().suites.first { it.id == "photo" }
        assertEquals(light, reset.settingsFor(book, false))
        assertEquals(38f, reset.settingsFor(book, true).marginLeft)
        assertEquals(25f, reset.settingsFor(book, true).fontSize)
        assertEquals(saved.settingsFor(layout, true), reset.settingsFor(layout, true))
    }

    @Test fun photoCopyTargetsOriginalPublisherModeAndKeepsOtherModeAsset() = fixture { manager, model, await ->
        val book = ReaderLayoutTarget.BOOK_LAYOUT
        model.beginEditing("photo")
        model.selectEditingLayout(book)
        val input = File(context.cacheDir, "book-background.jpg").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        model.addBackgroundPhoto(Uri.fromFile(input))
        model.selectEditingMode(true)
        await {
            manager.readerThemeSuiteState.first().suites.first { it.id == "photo" }
                .settingsFor(book, false).backgroundSelection.startsWith("custom:")
        }
        val suite = manager.readerThemeSuiteState.first().suites.first { it.id == "photo" }
        val selection = suite.settingsFor(book, false).backgroundSelection
        assertEquals(ReaderThemeSuites.DAY_ID, suite.settingsFor(book, true).backgroundSelection)
        assertEquals(null, manager.removeUnusedReaderBackground(selection.removePrefix("custom:")))
        model.selectEditingMode(false)
        await { model.uiState.value.editingSettings?.backgroundSelection == selection }
        model.removeBackgroundPhoto()
        await {
            manager.readerThemeSuiteState.first().suites.first { it.id == "photo" }
                .settingsFor(book, false).backgroundSelection == ReaderThemeSuites.DAY_ID
        }
        assertEquals("custom:shared", manager.readerThemeSuiteState.first().suites.first { it.id == "photo" }
            .settingsFor(layout, false).backgroundSelection)
    }

    @Test fun photoCopyKeepsItsOriginalModeAndLatestTypography() = fixture { manager, model, await ->
        model.beginEditing("photo")
        val input = File(context.cacheDir, "picked-background.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        model.addBackgroundPhoto(Uri.fromFile(input))
        model.selectEditingMode(true)
        // Simulate another accepted edit while the photo is being copied.
        val original = manager.readerThemeSuiteState.first().suites.first { it.id == "photo" }
        manager.updateReaderThemeSuite("photo", layout,
            original.settingsFor(layout, false).copy(fontSize = 23f), modeDark = false)
        await {
            val suite = manager.readerThemeSuiteState.first().suites.first { it.id == "photo" }
            suite.settingsFor(layout, false).backgroundSelection != "custom:shared"
        }
        val saved = DataStoreManager(context).readerThemeSuiteState.first().suites.first { it.id == "photo" }
        assertEquals(23f, saved.settingsFor(layout, false).fontSize)
        assertEquals("custom:shared", saved.settingsFor(layout, true).backgroundSelection)
        val backgrounds = manager.customReaderBackgrounds.first()
        val preset = backgrounds.first { it.selectionKey == saved.settingsFor(layout, false).backgroundSelection }
        assertTrue(File(preset.value).readBytes().contentEquals(input.readBytes()))
        assertTrue(backgrounds.any { it.id == "shared" })
    }

    @Test fun removingPhotoKeepsAssetsReferencedByOtherModesAndSuites() = fixture { manager, model, await ->
        model.beginEditing("photo")
        model.removeBackgroundPhoto()
        await {
            model.uiState.value.editingSettings?.backgroundSelection == ReaderThemeSuites.DAY_ID
        }
        // Drain the removal job as well as its preference emissions.
        await { model.uiState.value.backgrounds == manager.customReaderBackgrounds.first() }
        val shared = manager.customReaderBackgrounds.first().first { it.id == "shared" }
        assertTrue(File(shared.value).isFile)
        val suites = DataStoreManager(context).readerThemeSuiteState.first().suites
        assertEquals(shared.selectionKey, suites.first { it.id == "photo" }.settingsFor(layout, true).backgroundSelection)
        assertEquals(shared.selectionKey, suites.first { it.id == "other" }.settingsFor(layout, false).backgroundSelection)
    }

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun fixture(block: suspend (DataStoreManager, ReaderSettingsPreviewViewModel,
        suspend (suspend () -> Boolean) -> Unit) -> Unit) = runBlocking {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            context.dataStore.edit { it.clear() }
            val manager = DataStoreManager(context)
            val image = File(context.filesDir, "reader_backgrounds/shared.jpg").apply {
                parentFile!!.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
            val preset = ReaderBackgroundPreset("shared", ReaderBackgroundType.IMAGE, image.absolutePath)
            val plain = ReaderThemeSettings()
            val photo = plain.copy(backgroundSelection = preset.selectionKey)
            manager.saveCustomReaderBackgrounds(listOf(preset))
            manager.saveReaderThemeSuiteState(listOf(
                ReaderThemeSuite("photo", "Photo", plain, lightSettings = photo, darkSettings = photo),
                ReaderThemeSuite("other", "Other", plain, lightSettings = photo, darkSettings = plain)
            ), "photo", applyActiveSuite = false)
            val model = ReaderSettingsPreviewViewModel(manager, context)
            store.put("preview", model)
            val await: suspend (suspend () -> Boolean) -> Unit = { predicate ->
                withTimeout(10_000) {
                    do {
                        dispatcher.scheduler.runCurrent()
                        delay(10)
                    } while (!predicate())
                    dispatcher.scheduler.runCurrent()
                }
            }
            await { model.uiState.value.suites.any { it.id == "photo" } && model.uiState.value.backgrounds.isNotEmpty() }
            block(manager, model, await)
        } finally {
            store.clear()
            dispatcher.scheduler.runCurrent()
            Dispatchers.resetMain()
        }
    }
}
