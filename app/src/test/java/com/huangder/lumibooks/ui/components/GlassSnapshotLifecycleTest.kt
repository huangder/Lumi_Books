package com.huangder.lumibooks.ui.components

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GlassSnapshotLifecycleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun qualityChangesRefreshSnapshotWithoutChangingThePageIdentity() {
        val quality = mutableStateOf(com.huangder.lumibooks.domain.model.LiquidGlassSamplingQuality.CLEAR)
        var captures = 0
        lateinit var result: GlassSnapshotState
        compose.setContent {
            CompositionLocalProvider(com.huangder.lumibooks.ui.theme.LocalLiquidGlassSamplingQuality provides quality.value) {
                result = rememberGlassSnapshot(true, "same-page") {
                    captures++
                    GlassBitmapBackdrop(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888), Offset.Zero, IntSize(4, 4))
                }
            }
        }
        awaitSnapshot { result.ready }
        val first = result.backdrop!!
        compose.runOnIdle { quality.value = com.huangder.lumibooks.domain.model.LiquidGlassSamplingQuality.BALANCED }
        awaitSnapshot { result.backdrop !== first }
        compose.runOnIdle {
            assertEquals(2, captures)
            assertTrue(first.isClosed)
            assertFalse(result.backdrop!!.isClosed)
        }
    }

    @Test fun pageChangesKeepThePreviousLeaseUntilTheReplacementIsReady() {
        val enabled = mutableStateOf(true)
        val frozen = mutableStateOf(false)
        val page = mutableStateOf(1)
        var captures = 0
        lateinit var result: GlassSnapshotState
        compose.setContent {
            result = rememberGlassSnapshot(enabled.value, page.value, frozen.value) {
                captures++
                GlassBitmapBackdrop(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888), Offset.Zero, IntSize(4, 4))
            }
            Box {}
        }
        awaitSnapshot { result.ready }
        val first = result.backdrop!!
        compose.runOnIdle { frozen.value = true }
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(first, result.backdrop)
            assertEquals(1, captures)
            assertFalse(first.isClosed)
            page.value = 2
        }
        compose.waitForIdle()
        compose.runOnIdle { assertSame(first, result.backdrop) }
        compose.runOnIdle { frozen.value = false }
        awaitSnapshot { result.backdrop !== first }
        val second = compose.runOnIdle { result.backdrop!! }
        compose.runOnIdle {
            assertNotSame(first, second)
            assertTrue(first.isClosed)
            assertFalse(second.isClosed)
            frozen.value = true
            page.value = 3
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(second, result.backdrop)
            assertFalse(second.isClosed)
            frozen.value = false
        }
        awaitSnapshot { result.backdrop !== second }
        val third = compose.runOnIdle { result.backdrop!! }
        compose.runOnIdle {
            assertTrue(second.isClosed)
            assertFalse(third.isClosed)
            enabled.value = false
        }
        awaitSnapshot { result.backdrop == null }
        compose.runOnIdle {
            assertNull(result.backdrop)
            assertTrue(third.isClosed)
        }
    }

    @Test fun failedCaptureRetainsOldLeaseDuringRetryThenFallsBack() {
        val page = mutableStateOf(1)
        var captures = 0
        val retryStarted = CompletableDeferred<Unit>()
        val finishRetry = CompletableDeferred<Unit>()
        lateinit var result: GlassSnapshotState
        compose.setContent {
            val currentPage = page.value
            result = rememberGlassSnapshot(true, currentPage) {
                captures++
                if (currentPage == 1) GlassBitmapBackdrop(
                    Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888), Offset.Zero, IntSize(4, 4)
                ) else if (captures == 2) null else {
                    retryStarted.complete(Unit)
                    finishRetry.await()
                    null
                }
            }
        }
        awaitSnapshot { result.ready }
        val previous = result.backdrop!!
        compose.runOnIdle { page.value = 2 }
        awaitSnapshot { retryStarted.isCompleted }
        compose.runOnIdle {
            assertSame(previous, result.backdrop)
            assertFalse(previous.isClosed)
        }
        finishRetry.complete(Unit)
        awaitSnapshot { captures == 3 && result.backdrop == null }
        compose.runOnIdle {
            assertEquals(3, captures)
            assertNull(result.backdrop)
            assertTrue(previous.isClosed)
        }
    }

    private fun awaitSnapshot(condition: () -> Boolean) {
        compose.waitUntil(5_000) { compose.runOnIdle(condition) }
    }

    @Test fun closingLeaseDoesNotRecyclePixelsStillReferencedByDisplayLists() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val lease = GlassBitmapBackdrop(bitmap, Offset.Zero, IntSize(4, 4))
        lease.close()
        lease.close()
        assertTrue(lease.isClosed)
        assertFalse(bitmap.isRecycled)
    }
}
