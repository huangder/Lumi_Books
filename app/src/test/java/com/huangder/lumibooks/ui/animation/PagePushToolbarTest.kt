package com.huangder.lumibooks.ui.animation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.LocalPageVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w200dp-h200dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PagePushToolbarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun overflowBadgeStaysRoundBeforeDuringAndAfterMotion() {
        val scene = PageScene(Robolectric.buildActivity(ComponentActivity::class.java).create().get(), false)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            Box(Modifier.fillMaxSize().background(Color.White).drawWithContent {
                drawContent()
                scene.toolbarCapture?.takeIf { it.mode == PagePushToolbarCapture.Mode.OVERLAY }
                    ?.let { drawToolbar(scene, it, scene.progress, true) }
            }) {
                Box(Modifier.fillMaxSize().onPlaced { scene.pageCoordinates = it }) {
                    CompositionLocalProvider(LocalPageScene provides scene) {
                        Box(Modifier.offset(30.dp, 30.dp).size(40.dp).drawWithContent {
                            // Force invalidation at every progress step, as a live backdrop can.
                            scene.progress
                            drawContent()
                        }.pagePushToolbarItem()) {
                            Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Gray))
                            Box(Modifier.align(Alignment.TopEnd).offset(3.dp, (-3).dp)
                                .size(17.dp).clip(CircleShape).background(Color.Green))
                        }
                    }
                }
            }
        }
        fun snapshot(): Bitmap {
            lateinit var image: Bitmap
            compose.runOnIdle {
                image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(image))
            }
            return image
        }
        fun assertBadge() {
            val image = snapshot()
            assertEquals("Badge above the button must remain visible", android.graphics.Color.GREEN, image.getPixel(64, 28))
            assertEquals("Badge's upper left corner must stay round", android.graphics.Color.WHITE, image.getPixel(56, 27))
            image.recycle()
        }
        assertBadge()
        // Robolectric does not deliver HardwareRenderer's ImageReader completion.
        // Supply the same padded pixel snapshot to exercise the overlay's handoff,
        // opacity and blur independently of that device-only callback.
        val source = snapshot()
        val captured = Bitmap.createBitmap(source, 6, 6, 88, 88).asImageBitmap()
        source.recycle()
        val capture = PagePushToolbarCapture(Long.MAX_VALUE)
        compose.runOnIdle {
            scene.toolbarCapture = capture
            capture.submit(scene.controls.values.single(), captured)
        }
        // One control finishing capture must not hide it before the whole group hands off.
        assertBadge()
        compose.runOnIdle { capture.complete(ready = true) }
        assertBadge()
        for (step in 1..5) {
            compose.runOnIdle { scene.progress = 1f - step * 0.1f }
            val image = snapshot()
            compose.runOnIdle {
                assertEquals(if (step == 1) 1f else 0.5f, scene.controls.values.single().renderedScale, 0f)
            }
            var ink = 0
            for (y in 15..85) for (x in 15..85) {
                if (image.getPixel(x, y) != android.graphics.Color.WHITE) ink++
            }
            assertTrue("The fading/blurred control must still contain pixels at step $step", ink > 20)
            image.recycle()
        }
        compose.runOnIdle { scene.progress = 1f }
        assertBadge()
        compose.runOnIdle {
            assertEquals(1f, scene.controls.values.single().renderedScale, 0f)
            scene.toolbarCapture = null
        }
        assertBadge()
    }

    @Test fun retainedInvisibleTabDoesNotRegisterControlsInTheWindowOverlay() {
        val scene = PageScene(Robolectric.buildActivity(ComponentActivity::class.java).create().get(), false)
        val homeVisible = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalPageScene provides scene) {
                Box {
                    CompositionLocalProvider(LocalPageVisible provides homeVisible.value) {
                        Row {
                            Box(Modifier.size(40.dp).pagePushToolbarItem())
                            Box(Modifier.size(40.dp).pagePushToolbarItem())
                        }
                    }
                    Box(Modifier.size(40.dp).pagePushToolbarItem())
                }
            }
        }
        compose.runOnIdle { assertEquals(3, scene.controls.size); homeVisible.value = false }
        compose.runOnIdle { assertEquals("Only the visible tab's control belongs to the overlay", 1, scene.controls.size) }
        compose.runOnIdle { homeVisible.value = true }
        compose.runOnIdle { assertEquals(3, scene.controls.size) }
    }

    @Test fun actionLoadedAfterHandoffStaysHiddenUntilThePageSettles() {
        val scene = PageScene(Robolectric.buildActivity(ComponentActivity::class.java).create().get(), false)
        val loaded = mutableStateOf(false)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            Box(Modifier.fillMaxSize().background(Color.White).drawWithContent {
                drawContent()
                scene.toolbarCapture?.takeIf { it.mode == PagePushToolbarCapture.Mode.OVERLAY }
                    ?.let { drawToolbar(scene, it, scene.progress, false) }
            }.onPlaced { scene.pageCoordinates = it }) {
                CompositionLocalProvider(LocalPageScene provides scene) {
                    Row(Modifier.offset(30.dp, 30.dp).pagePushToolbarItem()) {
                        if (loaded.value) Box(Modifier.size(40.dp).background(Color.Green))
                    }
                }
            }
        }
        val capture = PagePushToolbarCapture(Long.MAX_VALUE)
        compose.runOnIdle { scene.toolbarCapture = capture; capture.complete(ready = true); scene.progress = .5f }
        compose.runOnIdle { loaded.value = true }
        fun pixel(): Int {
            var color = 0
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                color = bitmap.getPixel(40, 40)
                bitmap.recycle()
            }
            return color
        }
        assertEquals("A late action cannot bypass the fixed overlay", android.graphics.Color.WHITE, pixel())
        val bitmap = Bitmap.createBitmap(88, 88, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(24f, 24f, 64f, 64f, android.graphics.Paint().apply { color = android.graphics.Color.GREEN })
        compose.runOnIdle { capture.submit(scene.controls.values.single(), bitmap.asImageBitmap()) }
        assertEquals("A late snapshot cannot join a sealed overlay", android.graphics.Color.WHITE, pixel())
        compose.runOnIdle { scene.toolbarCapture = null }
        assertEquals(android.graphics.Color.GREEN, pixel())
    }

    @Test fun timedOutCaptureDrawsLiveAndIgnoresLateResults() {
        val scene = PageScene(Robolectric.buildActivity(ComponentActivity::class.java).create().get(), false)
        val capture = PagePushToolbarCapture(0)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalPageScene provides scene) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.offset(30.dp, 30.dp).size(40.dp)
                        .pagePushToolbarItem().background(Color.Green))
                }
            }
        }
        compose.runOnIdle {
            val item = scene.controls.values.single()
            assertEquals(false, item.isReadyFor(capture))
            scene.toolbarCapture = capture
            capture.complete(ready = false)
            capture.submit(item, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap())
            assertEquals(PagePushToolbarCapture.Mode.LIVE, capture.mode)
            assertEquals(null, capture[item])
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            assertEquals(android.graphics.Color.GREEN, bitmap.getPixel(40, 40))
            bitmap.recycle()
        }
    }

    @Test fun failedCopyDiscardsThePartialGroup() {
        val capture = PagePushToolbarCapture(Long.MAX_VALUE)
        compose.setContent {
            val scene = androidx.compose.runtime.remember {
                PageScene(Robolectric.buildActivity(ComponentActivity::class.java).create().get(), false)
            }
            CompositionLocalProvider(LocalPageScene provides scene) {
                Row {
                    Box(Modifier.size(40.dp).pagePushToolbarItem())
                    Box(Modifier.size(40.dp).pagePushToolbarItem())
                }
            }
            androidx.compose.runtime.SideEffect {
                val (available, broken) = scene.controls.values.toList()
                capture.submit(available, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap())
                capture.submit(broken, null)
                capture.complete(ready = true)
                assertEquals(null, capture[available])
            }
        }
        compose.runOnIdle { assertEquals(PagePushToolbarCapture.Mode.LIVE, capture.mode) }
    }
}
