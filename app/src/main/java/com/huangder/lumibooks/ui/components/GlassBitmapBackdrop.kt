package com.huangder.lumibooks.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** An independently owned pixel lease. It never references a live View/RenderNode. */
internal class GlassBitmapBackdrop(
    bitmap: Bitmap,
    val screenOrigin: Offset,
    val viewportSize: IntSize
) : Backdrop, AutoCloseable {
    private var pixels: ImageBitmap? = bitmap.asImageBitmap()
    internal val isClosed: Boolean get() = pixels == null
    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val image = pixels ?: return
        val origin = coordinates?.takeIf { it.isAttached }?.localToScreen(Offset.Zero) ?: return
        if (!origin.x.isFinite() || !origin.y.isFinite()) return
        drawImage(image, dstOffset = glassSnapshotOffset(screenOrigin, origin), dstSize = viewportSize)
    }

    override fun close() {
        // Display lists may still own the previous image. Let their native references release
        // the pixels; Bitmap.recycle() here can invalidate an outgoing glass animation.
        pixels = null
    }
}

internal fun glassSnapshotOffset(source: Offset, surface: Offset): IntOffset =
    IntOffset((source.x - surface.x).roundToInt(), (source.y - surface.y).roundToInt())

private fun Context.snapshotActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.takeUnless { it === this }?.snapshotActivity()
    else -> null
}

/** Capture before displaying an overlay. PixelCopy does not record native Views into Compose. */
internal suspend fun captureWindowGlassBackdrop(view: View): GlassBitmapBackdrop? {
    val window = view.context.snapshotActivity()?.window ?: return null
    if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return null
    val inWindow = IntArray(2).also(view::getLocationInWindow)
    val onScreen = IntArray(2).also(view::getLocationOnScreen)
    val rect = Rect(inWindow[0], inWindow[1], inWindow[0] + view.width, inWindow[1] + view.height)
    val scale = minOf(1f, 1440f / maxOf(view.width, view.height))
    val bitmap = Bitmap.createBitmap(
        (view.width * scale).roundToInt().coerceAtLeast(1),
        (view.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888
    )
    return suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(window, rect, bitmap, { result ->
                if (result == PixelCopy.SUCCESS && continuation.isActive) {
                    continuation.resume(GlassBitmapBackdrop(bitmap,
                        Offset(onScreen[0].toFloat(), onScreen[1].toFloat()), IntSize(view.width, view.height)))
                } else {
                    bitmap.recycle()
                    if (continuation.isActive) {
                        Log.w("GlassSnapshot", "Window snapshot unavailable: $result")
                        continuation.resume(null)
                    }
                }
            }, Handler(Looper.getMainLooper()))
        } catch (error: IllegalArgumentException) {
            bitmap.recycle()
            Log.w("GlassSnapshot", "Window snapshot unavailable", error)
            continuation.resume(null)
        }
    }
}

internal class GlassSnapshotState {
    var backdrop by mutableStateOf<GlassBitmapBackdrop?>(null)
    var ready by mutableStateOf(false)
}

/** No polling: refresh on a page/viewport change, or when a previously frozen overlay closes. */
@Composable
internal fun rememberGlassSnapshot(
    enabled: Boolean,
    identity: Any?,
    frozen: Boolean = false,
    provider: suspend () -> GlassBitmapBackdrop?
): GlassSnapshotState {
    val state = remember(identity) { GlassSnapshotState() }
    val latestProvider by rememberUpdatedState(provider)
    LaunchedEffect(state, enabled, frozen) {
        if (!enabled) {
            state.backdrop = null
            state.ready = false
            return@LaunchedEffect
        }
        if (frozen && state.backdrop != null) return@LaunchedEffect
        withFrameNanos { }
        var next = latestProvider()
        if (next == null) {
            withFrameNanos { }
            next = latestProvider()
        }
        if (next != null) state.backdrop = next
        else Log.w("GlassSnapshot", "No current-page pixels; keeping readable fallback")
        state.ready = true
    }
    val snapshot = state.backdrop
    DisposableEffect(snapshot) { onDispose { snapshot?.close() } }
    return state
}

@Composable
internal fun rememberWindowGlassSnapshot(enabled: Boolean, identity: Any?): GlassSnapshotState {
    val view = LocalView.current
    return rememberGlassSnapshot(enabled, identity) { captureWindowGlassBackdrop(view) }
}
