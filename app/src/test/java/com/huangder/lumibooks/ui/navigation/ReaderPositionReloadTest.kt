package com.huangder.lumibooks.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 35], application = Application::class)
class ReaderPositionReloadTest {
    @get:Rule val compose = createComposeRule()

    @Test fun coldWidgetRequestDoesNotReloadWhenMetadataReleasesTheReader() {
        val reader = Any()
        val metadataReady = mutableStateOf(false)
        val token = mutableStateOf(7L)
        val reloads = mutableListOf<Long>()
        compose.setContent {
            // ReaderRouter observes before its document gate; mounting the actual reader
            // later must not turn the launch request into a second cache-recovery load.
            ObserveReaderPositionReload(reader, token.value) { reloads.add(it) }
            if (metadataReady.value) Box(Modifier.size(10.dp))
        }
        compose.runOnIdle { assertTrue(reloads.isEmpty()); metadataReady.value = true }
        compose.runOnIdle { assertTrue(reloads.isEmpty()); token.value = 8L }
        compose.runOnIdle { assertEquals(listOf(8L), reloads) }
    }

    @Test fun retainedReaderReloadsNewRequestsButRecreatedReaderLoadsItsOwnAnchor() {
        val reader = mutableStateOf(Any())
        val token = mutableStateOf(0L)
        val reloads = mutableListOf<Long>()
        compose.setContent {
            ObserveReaderPositionReload(reader.value, token.value) { reloads.add(it) }
        }
        compose.runOnIdle { token.value = 1L }
        compose.runOnIdle { assertEquals(listOf(1L), reloads); token.value = 2L }
        compose.runOnIdle { assertEquals(listOf(1L, 2L), reloads); reader.value = Any() }
        compose.runOnIdle { assertEquals(listOf(1L, 2L), reloads); token.value = 3L }
        compose.runOnIdle { assertEquals(listOf(1L, 2L, 3L), reloads) }
    }
}
