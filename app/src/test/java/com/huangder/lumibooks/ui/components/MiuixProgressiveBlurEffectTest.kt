package com.huangder.lumibooks.ui.components

import android.app.Application
import com.kyant.backdrop.effects.MiuixProgressiveBlurEffect
import com.kyant.backdrop.effects.ProgressiveBlurDirection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Exercise native AGSL compilation and Android RenderEffect construction on SDK 35.
 * Robolectric's SDK 33/34 backend uses Android 12 Skia and cannot compile child.eval:
 * https://github.com/robolectric/robolectric/issues/9691
 * The API 32/33 gate itself is covered separately by ProgressiveTopBlurTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiuixProgressiveBlurEffectTest {
    @Test fun nativeShadersCompileForEveryDirectionAndCacheStableFrames() {
        val effect = MiuixProgressiveBlurEffect()
        for (direction in ProgressiveBlurDirection.entries) {
            for (radius in listOf(0f, 1f, 28f, 84f)) {
                val padding = MiuixProgressiveBlurEffect.padding(radius)
                val frame = effect.effect(radius, 320f, 120f, padding, direction)
                assertNotNull(frame)
                assertFalse(effect.failed)
                assertSame(frame, effect.effect(radius, 320f, 120f, padding, direction))
            }
        }
    }

    @Test fun resizingInvalidatesTheCachedGeometryAndUnmeasuredStripsDoNotFail() {
        val effect = MiuixProgressiveBlurEffect()
        assertNull(effect.effect(28f, 0f, 0f, 38f, ProgressiveBlurDirection.Top))
        assertFalse(effect.failed)
        val first = effect.effect(28f, 320f, 120f, 38f, ProgressiveBlurDirection.Top)
        val resized = effect.effect(28f, 320f, 180f, 38f, ProgressiveBlurDirection.Top)
        assertNotNull(first)
        assertNotNull(resized)
        assertNotSame(first, resized)
        val held = effect.effect(28f, 320f, 180f, 38f, ProgressiveBlurDirection.Top, 0.68f)
        assertNotNull(held)
        assertNotSame(resized, held)
    }
}

