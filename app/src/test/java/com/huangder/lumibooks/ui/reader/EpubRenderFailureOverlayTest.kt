package com.huangder.lumibooks.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class EpubRenderFailureOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun scrollingProgressDoesNotCoverTheBookAndCanBeCancelled() {
        var cancelled = 0
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                EpubNavigationLoadingOverlay("chapter", EpubNavigationStage.CONFIGURING,
                    Color.White, Color.Black, onCancel = { cancelled++ }, compact = true)
            }
        }
        val bounds = compose.onNodeWithTag("epubNavigationLoading").getUnclippedBoundsInRoot()
        assertTrue((bounds.bottom - bounds.top).value < 150f)
        compose.onNodeWithText("Cancel", ignoreCase = true).performClick()
        compose.runOnIdle { assertEquals(1, cancelled) }
    }

    @Test fun failureNeverSwitchesModeUntilTheExplicitAction() {
        var retries = 0
        var switches = 0
        var closes = 0
        compose.setContent {
            EpubRenderFailureOverlay(
                EpubRenderFailure(EpubInitialLoadFailureReason.PAGE_READY_TIMEOUT, 1, null, false),
                Color.White, Color.Black,
                onRetry = { retries++ }, onSwitchLayout = { switches++ }, onClose = { closes++ }
            )
        }
        compose.onNodeWithTag("epubRenderFailure").assertExists()
        compose.runOnIdle { assertEquals(0, switches) }
        compose.onNodeWithText("Retry", ignoreCase = true).performClick()
        compose.runOnIdle { assertEquals(1, retries); assertEquals(0, switches) }
        compose.onNodeWithText("Switch to reader layout").performClick()
        compose.onNodeWithText("Close book").performClick()
        compose.runOnIdle { assertEquals(1, switches); assertEquals(1, closes) }
    }
}
