package com.huangder.lumibooks.ui.components

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
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

    @Test fun frozenOverlayRetainsItsLeaseAndPageChangesInvalidateOldPixels() {
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
        compose.waitUntil { result.ready }
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
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        val second = compose.runOnIdle { result.backdrop!! }
        compose.runOnIdle {
            assertNotSame(first, second)
            assertTrue(first.isClosed)
            assertFalse(second.isClosed)
            enabled.value = false
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertNull(result.backdrop)
            assertTrue(second.isClosed)
        }
    }

    @Test fun failedCaptureRetriesOnceAndNeverReusesAnotherPagesPixels() {
        val page = mutableStateOf(1)
        var captures = 0
        lateinit var result: GlassSnapshotState
        compose.setContent {
            val currentPage = page.value
            result = rememberGlassSnapshot(true, currentPage) {
                captures++
                if (currentPage == 1) GlassBitmapBackdrop(
                    Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888), Offset.Zero, IntSize(4, 4)
                ) else null
            }
        }
        compose.waitUntil { result.ready }
        val previous = result.backdrop!!
        compose.runOnIdle { page.value = 2 }
        compose.waitUntil { result.ready }
        compose.runOnIdle {
            assertEquals(3, captures)
            assertNull(result.backdrop)
            assertTrue(previous.isClosed)
        }
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
