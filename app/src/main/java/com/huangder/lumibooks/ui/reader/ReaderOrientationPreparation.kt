package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.WindowInsets
import kotlinx.coroutines.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

internal data class ReaderViewport(val width: Int, val height: Int) {
    val landscape get() = width >= height
}

/** Learns real content bounds; predictions subtract chrome from window bounds, not text bounds. */
internal class ReaderViewportPredictor {
    private var windowShape: List<Int>? = null
    private val measured = mutableMapOf<Boolean, ReaderViewport>()

    fun observe(view: View, width: Int, height: Int): ReaderViewport? {
        if (width <= 0 || height <= 0) return null
        val activity = view.context.readerActivity()
        val bounds = if (Build.VERSION.SDK_INT >= 30 && activity != null) {
            activity.windowManager.currentWindowMetrics.bounds.let { ReaderViewport(it.width(), it.height()) }
        } else ReaderViewport(view.rootView.width.coerceAtLeast(width), view.rootView.height.coerceAtLeast(height))
        val insets = if (Build.VERSION.SDK_INT >= 30) view.rootWindowInsets?.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()) else null
        return observeBounds(ReaderViewport(width, height), bounds, view.resources.displayMetrics.densityDpi,
            (insets?.left ?: 0) + (insets?.right ?: 0), (insets?.top ?: 0) + (insets?.bottom ?: 0),
            activity?.isInMultiWindowMode == true)
    }

    internal fun observeBounds(current: ReaderViewport, bounds: ReaderViewport, densityDpi: Int,
        insetX: Int = 0, insetY: Int = 0, multiWindow: Boolean = false): ReaderViewport? {
        val width = current.width; val height = current.height
        val shape = listOf(minOf(bounds.width, bounds.height), maxOf(bounds.width, bounds.height), densityDpi)
        if (windowShape != shape) { measured.clear(); windowShape = shape }
        measured[current.landscape] = current
        measured[!current.landscape]?.let { return it }
        // Resizable windows/fold transitions have no predictable opposite orientation.
        if (multiWindow) return null
        // Insets which the reader draws behind are not consumed chrome.
        val horizontal = insetX.coerceAtMost((bounds.width - width).coerceAtLeast(0))
        val vertical = insetY.coerceAtMost((bounds.height - height).coerceAtLeast(0))
        val chromeX = (bounds.width - width - horizontal).coerceAtLeast(0)
        val chromeY = (bounds.height - height - vertical).coerceAtLeast(0)
        return ReaderViewport((bounds.height - horizontal - chromeX).coerceAtLeast(1),
            (bounds.width - vertical - chromeY).coerceAtLeast(1)).takeIf { it.landscape != current.landscape }
    }

    fun clear() { measured.clear(); windowShape = null }
}

internal fun Context.readerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.readerActivity()
    else -> null
}

/** One cancellable, low-priority preparation per reader. No foreground callbacks run here. */
internal class ReaderOrientationPreparation(private val scope: CoroutineScope) {
    private var job: Job? = null
    private var generation = 0L
    fun schedule(prepare: suspend () -> Unit) {
        cancel()
        val token = generation
        job = scope.launch {
            delay(350)
            if (token == generation) prepare()
        }
    }
    fun cancel() { generation++; job?.cancel(); job = null }
}

/** A resource-owning pair. Eviction never disposes the active value. */
internal class ReaderOrientationCache<K, V>(private val dispose: (V) -> Unit = {}) {
    private val values = LinkedHashMap<K, V>(2, 0.75f, true)
    var activeKey: K? = null
        private set
    operator fun get(key: K): V? = values[key]
    fun activate(key: K): V? { activeKey = key; return values[key] }
    fun put(key: K, value: V) {
        values.put(key, value)?.takeIf { it !== value }?.let(dispose)
        while (values.size > 2) {
            val victim = values.keys.first { it != activeKey && it != key }
            values.remove(victim)?.let(dispose)
        }
    }
    fun trim() { values.keys.filter { it != activeKey }.forEach { values.remove(it)?.let(dispose) } }
    fun clear() { values.values.toList().forEach(dispose); values.clear(); activeKey = null }
}

/** Hidden renderers retain state/providers, but can never publish reading side effects. */
internal class ReaderOrientationEvents {
    var active = false
    var onReady: () -> Unit = {}
    private var wasActive = false
    private val retained = linkedMapOf<String, () -> Unit>()
    private var expectedOperation: Long? = null
    private var completedOperation: Long? = null
    private var foregroundOperation: Long? = null
    var preparationFailed by mutableStateOf(false)
        private set
    var recoveryGeneration by mutableIntStateOf(0)
        private set
    private var viewport: ReaderViewport? = null
    var viewportChanged = false
        private set
    val hasReadyContent get() = !preparationPending && !viewportChanged &&
        retained.keys.any { it in setOf("onPage", "onPageChanged", "onFirstContentDrawn") }
    fun configureViewport(next: ReaderViewport) {
        if (viewport == next) return
        viewport = next; viewportChanged = true
        retained.keys.removeAll(setOf("onPage", "onPageChanged", "onSpreadChanged", "onViewportAnchor", "onChapterVisible", "onVisibleCharacters"))
    }
    val preparationPending get() = expectedOperation != null && expectedOperation != completedOperation
    fun expect(operation: Long) {
        if (expectedOperation == operation) return
        preparationFailed = false
        expectedOperation = operation
        retained.keys.removeAll(setOf("onPageChanged", "onPage", "onSpreadChanged", "onFocusedPageChanged", "onPageCountConfirmed", "onViewportAnchor", "onChapterVisible", "onVisibleCharacters", "onFocusedPageChanged", "onFirstContentDrawn"))
    }
    fun expectForeground(operation: Long) {
        if (foregroundOperation == operation) return
        foregroundOperation = operation
        expect(operation)
    }
    fun abandon(operation: Long): Boolean {
        if (expectedOperation != operation) return false
        expectedOperation = null
        completedOperation = null
        viewportChanged = true
        retained.keys.removeAll(setOf("onPageChanged", "onPage", "onSpreadChanged", "onFocusedPageChanged", "onPageCountConfirmed", "onViewportAnchor", "onChapterVisible", "onVisibleCharacters", "onFirstContentDrawn"))
        return true
    }
    fun fail(operation: Long) { if (abandon(operation)) preparationFailed = true }
    fun finishNavigation(result: EpubNavigationResult) {
        when (result) {
            is EpubNavigationResult.Committed -> complete(result.operationId)
            is EpubNavigationResult.Failed -> fail(result.operationId)
            // A viewport calibration restarts the same operation. Its old host work
            // is cancelled synchronously before the replacement starts preparing.
            is EpubNavigationResult.Cancelled -> if (!result.replaced) fail(result.operationId)
        }
    }
    fun retryOnActivation() {
        if (preparationFailed) { preparationFailed = false; recoveryGeneration++ }
    }
    fun complete(operation: Long) {
        if (expectedOperation != operation) return
        completedOperation = operation
        viewportChanged = false
        if (active) { publishContent(); onReady() }
    }
    private fun publishContent() {
        listOf("onPageCountConfirmed", "onPageIndex", "onPaginationReady", "onPage", "onPageChanged", "onSpreadChanged", "onViewportAnchor", "onChapterVisible", "onVisibleCharacters")
            .forEach { retained[it]?.invoke() }
    }
    fun dispatch(name: String, retain: Boolean = false, event: () -> Unit) {
        if (retain) retained[name] = event
        if (active && (!preparationPending || name !in setOf("onPage", "onPageChanged", "onSpreadChanged", "onPageCountConfirmed", "onViewportAnchor", "onChapterVisible", "onVisibleCharacters", "onFocusedPageChanged", "onFirstContentDrawn"))) {
            event()
            if (name in setOf("onPageChanged", "onPage", "onFirstContentDrawn")) { viewportChanged = false; onReady() }
        }
    }
    fun publishActivation() {
        if (active && !wasActive) retained.entries.filter { (name, _) -> !preparationPending || name !in setOf("onPage", "onPageChanged", "onSpreadChanged", "onPageCountConfirmed", "onViewportAnchor", "onChapterVisible", "onVisibleCharacters", "onFocusedPageChanged", "onFirstContentDrawn") }.sortedBy { (name, _) ->
            when (name) {
                "onPaginationConfigured", "onRendererEngineResolved" -> 0
                "onPageCountConfirmed", "onPageIndex", "onPaginationReady" -> 1
                "onPage", "onPageChanged", "onSpreadChanged", "onFocusedPageChanged" -> 3
                else -> 2
            }
        }.map { it.value }.forEach { it() }
        wasActive = active
        if (active && hasReadyContent) onReady()
    }
}

internal class ReaderRotationTrace(private val renderer: String) {
    private var viewport: ReaderViewport? = null
    private var started = 0L
    private var ready = false
    private var reused = false
    private var builds = 0
    fun begin(next: ReaderViewport, cacheHit: Boolean, layoutBuilds: Int = 0) {
        if (viewport == next) return
        viewport = next; started = android.os.SystemClock.elapsedRealtimeNanos()
        ready = false; reused = cacheHit; builds = layoutBuilds
    }
    fun ready() { ready = true }
    fun drawn(layoutBuilds: Int = builds) {
        if (!ready || started == 0L) return
        if (com.huangder.lumibooks.BuildConfig.DEBUG) android.util.Log.d("ReaderRotation",
            "renderer=$renderer viewport=$viewport cacheHit=$reused layoutBuilds=${layoutBuilds - builds} firstFrameMs=${(android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f}")
        started = 0L
    }
}
