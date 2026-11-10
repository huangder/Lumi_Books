package com.huangder.lumibooks.ui.animation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import com.huangder.lumibooks.BuildConfig
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A plain fade between the library and the reader. The reader's pages are not faded themselves
 * (its renderers keep their own layers and would show partial frames), so the fade runs on an
 * ordinary layer above them: the stored page (or paper) fades in over the still library before
 * the reader opens beneath it, and on the way back a capture of the screen (or its paper) fades
 * out over the library.
 */
@Stable
internal class ReaderFadeState(private val scope: CoroutineScope) {
    /** The reader opened through this fade; its back gesture fades out the same way. */
    var readerBookId by mutableStateOf<String?>(null)
        private set
    var image by mutableStateOf<ImageBitmap?>(null)
        private set
    var paperColor by mutableStateOf(Color.White)
        private set
    var visible by mutableStateOf(false)
        private set
    val alpha = Animatable(0f)
    private var job: Job? = null
    private var readerDrawn = false
    private var covering = false
    /** The cover shows the stored page, so revealing the reader is a near-identical crossfade. */
    private var coverShowsPage = false

    fun owns(bookId: String): Boolean = readerBookId == bookId

    /** Fades the page in over the library, then [navigate]s to the reader underneath it. */
    fun open(bookId: String, paper: Color, page: ImageBitmap?, navigate: () -> Unit) {
        job?.cancel()
        readerBookId = bookId
        paperColor = paper
        image = page
        coverShowsPage = page != null
        readerDrawn = false
        covering = false
        visible = true
        job = scope.launch {
            alpha.snapTo(0f)
            alpha.animateTo(1f, VisualMotion.tween(FADE_IN_MS, easing = FastOutSlowInEasing))
            covering = true
            navigate()
            if (readerDrawn) uncover()
        }
    }

    /** The reader has drawn its first page below the cover. */
    fun markReaderDrawn(bookId: String) {
        if (readerBookId != bookId || readerDrawn) return
        readerDrawn = true
        if (covering) job = scope.launch { uncover() }
    }

    /** The reader settled on another paper color; a capture-less close fades out in it. */
    fun updatePaper(bookId: String, paper: Color) {
        if (readerBookId == bookId) paperColor = paper
    }

    private suspend fun uncover() {
        covering = false
        // The reader's first frames after its page appears are still busy (insets, menus, the
        // page's neighbours); a fade started inside them would skip most of its frames.
        awaitSteadyFrames("reveal")
        // Over plain paper the text itself is what appears, so it needs a visible fade; over the
        // stored page the two layers match and a quick crossfade is enough.
        val reveal = if (coverShowsPage) {
            VisualMotion.tween<Float>(REVEAL_MS, easing = FastOutSlowInEasing)
        } else {
            VisualMotion.tween<Float>(REVEAL_TEXT_MS, easing = LinearOutSlowInEasing)
        }
        traced("reveal") { alpha.animateTo(0f, reveal) }
        visible = false
        image = null
    }

    /**
     * Covers the reader with [capture] of the screen, runs [pop] beneath it, then fades the capture
     * out over the library. Without a capture the reader's paper fades out instead, so the text
     * vanishes into its own page color rather than the library cutting in.
     */
    fun close(capture: ImageBitmap?, pop: () -> Unit) {
        job?.cancel()
        readerBookId = null
        image = capture
        coverShowsPage = false
        visible = true
        job = scope.launch {
            alpha.snapTo(1f)
            withFrameNanos { }
            pop()
            // The library is composed again from scratch right after the pop, which takes a few
            // long frames; starting the fade then would spend most of it inside them and read as
            // a cut. Hold the opaque cover until the library draws at a steady pace.
            awaitSteadyFrames("close")
            traced("close") {
                alpha.animateTo(0f, VisualMotion.tween(FADE_OUT_MS, easing = FastOutSlowInEasing))
            }
            visible = false
            image = null
        }
    }

    /** Waits, bounded, until [STEADY_FRAMES] consecutive frames arrive on time. */
    private suspend fun awaitSteadyFrames(label: String) {
        val start = withFrameNanos { it }
        var last = start
        var steady = 0
        val gaps = if (BuildConfig.DEBUG) StringBuilder() else null
        while (steady < STEADY_FRAMES && last - start < MAX_SETTLE_NS) {
            val now = withFrameNanos { it }
            val gap = now - last
            gaps?.append(gap / 1_000_000)?.append(',')
            steady = if (gap <= STEADY_FRAME_NS) steady + 1 else 0
            last = now
        }
        if (gaps != null) Log.d(TAG, "$label settle ${(last - start) / 1_000_000}ms gaps=$gaps")
    }

    /** Debug builds log each frame gap of [block]'s animation, to tell a smooth fade from a cut. */
    private suspend fun traced(label: String, block: suspend () -> Unit) = coroutineScope {
        if (!BuildConfig.DEBUG) return@coroutineScope block()
        val frames = StringBuilder()
        val started = System.nanoTime()
        val trace = launch {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                frames.append((now - last) / 1_000_000).append(',')
                last = now
            }
        }
        try {
            block()
        } finally {
            trace.cancel()
            Log.d(TAG, "$label fade ${(System.nanoTime() - started) / 1_000_000}ms frames=$frames")
        }
    }

    /** Navigation left the reader some other way (a notification, a link to another book). */
    fun forget(bookId: String) {
        if (readerBookId == bookId) readerBookId = null
    }

    companion object {
        const val FADE_IN_MS = 280
        const val REVEAL_MS = 200
        const val REVEAL_TEXT_MS = 380
        const val FADE_OUT_MS = 360
        private const val STEADY_FRAMES = 3
        private const val STEADY_FRAME_NS = 24_000_000L
        private const val MAX_SETTLE_NS = 600_000_000L
        private const val TAG = "ReaderFade"
    }
}

@Composable
internal fun rememberReaderFadeState(): ReaderFadeState {
    val scope = rememberCoroutineScope()
    return remember(scope) { ReaderFadeState(scope) }
}

@Composable
internal fun ReaderFadeOverlay(state: ReaderFadeState, modifier: Modifier = Modifier) {
    if (!state.visible) return
    Box(
        modifier
            .fillMaxSize()
            // Paper and page are opaque and stacked, so alpha can be applied per draw instead of
            // through a full-screen offscreen buffer every frame.
            .graphicsLayer {
                alpha = state.alpha.value
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            // While it covers the screen, a tap must reach neither the reader nor the library.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
            .background(state.paperColor)
    ) {
        state.image?.let { page ->
            Image(page, contentDescription = null, contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize())
        }
    }
}

/** The whole window as shown, SurfaceView content included; null when it cannot be read. */
internal suspend fun captureWindow(view: View): ImageBitmap? {
    val activity = view.context.findActivity() ?: return null
    val window = activity.window ?: return null
    val decor = window.decorView
    if (decor.width <= 0 || decor.height <= 0) return null
    val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
    return suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(window, bitmap, { result ->
                if (!continuation.isActive) return@request
                continuation.resume(if (result == PixelCopy.SUCCESS) bitmap.asImageBitmap() else null)
            }, Handler(Looper.getMainLooper()))
        } catch (error: IllegalArgumentException) {
            continuation.resume(null)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
