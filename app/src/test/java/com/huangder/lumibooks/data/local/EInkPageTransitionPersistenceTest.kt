package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.domain.model.ReaderPageTransition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EInkPageTransitionPersistenceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clear() = runBlocking {
        context.dataStore.edit { it.clear() }
        Unit
    }

    @Test fun supportedTransitionsSurviveReopeningInEInkMode() = runBlocking {
        val manager = DataStoreManager(context)
        manager.saveEInkModeEnabled(true)
        for (mode in listOf("wave", "fade", "none")) {
            manager.savePageTransition(mode)
            val reloaded = DataStoreManager(context)
            assertEquals(mode, reloaded.pageTransition().first())
            val snapshot = reloaded.readerPreferences("book").first()
            assertEquals(mode, ReaderPageTransition.effectiveKey(snapshot.pageTransition, snapshot.eInkModeEnabled))
        }
    }

    @Test fun togglingEInkKeepsThePreferredTransitionAndItsDuration() = runBlocking {
        val manager = DataStoreManager(context)
        for (mode in listOf("slide", "scroll", "continuous", "curl", "wave", "fade")) {
            manager.savePageTransition(mode)
            manager.savePageTransitionDuration(mode, 600)
            val duration = manager.readerPageAnimationSettings.first().durationFor(mode)
            for (eInk in listOf(true, false)) {
                manager.saveEInkModeEnabled(eInk)
                val snapshot = DataStoreManager(context).readerPreferences("book").first()
                assertEquals(mode, snapshot.pageTransition)
                val expected = if (eInk && mode !in setOf("fade", "wave")) "none" else mode
                assertEquals(expected, ReaderPageTransition.effectiveKey(snapshot.pageTransition, snapshot.eInkModeEnabled))
                assertEquals(duration, snapshot.pageAnimationSettings.durationFor(mode))
            }
        }
    }
}
