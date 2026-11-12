package com.huangder.lumibooks.ui.reader

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.delay

/** Two measured renderer subtrees survive rotation, including single/spread branch changes. */
@Composable
internal fun ReaderOrientationContent(owner: Any, modifier: Modifier = Modifier,
    content: @Composable (viewport: ReaderViewport, active: Boolean, events: ReaderOrientationEvents) -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val predictor = remember(owner) { ReaderViewportPredictor() }
    val trace = remember(owner) { ReaderRotationTrace(owner.javaClass.simpleName) }
    var prepare by remember(owner) { mutableStateOf(false) }
    var suppressed by remember(owner) { mutableStateOf(false) }
    var contentReady by remember(owner) { mutableStateOf(false) }
    val eventBuckets = remember(owner) { mutableMapOf<Boolean, ReaderOrientationEvents>() }
    DisposableEffect(owner, context) {
        val drawObserver = android.view.ViewTreeObserver.OnDrawListener { trace.drawn() }
        view.viewTreeObserver.addOnDrawListener(drawObserver)
        val callback = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(config: Configuration) = Unit
            override fun onLowMemory() { suppressed = true }
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) suppressed = true
            }
        }
        context.registerComponentCallbacks(callback)
        onDispose {
            context.unregisterComponentCallbacks(callback)
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(drawObserver)
        }
    }
    BoxWithConstraints(modifier.clipToBounds()) {
        val current = ReaderViewport(with(density) { maxWidth.roundToPx() }, with(density) { maxHeight.roundToPx() })
        // Child composition may be skipped when only a parent callback changes. Set the
        // gates to their actual state, rather than temporarily disabling every child.
        eventBuckets.forEach { (landscape, events) -> events.active = landscape == current.landscape }
        val other = remember(current, owner) { predictor.observe(view, current.width, current.height) }
        LaunchedEffect(owner, contentReady) { if (contentReady) { delay(350); prepare = true } }
        val slots = buildList {
            add(current.landscape to current)
            if (prepare && !suppressed && other != null) add(other.landscape to other)
        }.sortedBy { it.first }
        Box(Modifier.fillMaxSize()) {
            for ((landscape, viewport) in slots) key(owner, landscape) {
                val active = landscape == current.landscape
                val events = eventBuckets.getOrPut(landscape) { ReaderOrientationEvents() }
                events.configureViewport(viewport)
                if (active) trace.begin(viewport, events.hasReadyContent)
                events.active = active
                events.onReady = { contentReady = true; trace.ready() }
                SideEffect { events.publishActivation() }
                DisposableEffect(events) { onDispose {
                    events.active = false
                    if (eventBuckets[landscape] === events) eventBuckets.remove(landscape)
                } }
                // Keep recording a real frame under the opaque active page: WebView draw fences
                // cannot complete for GONE subtrees. Hidden content is absent from accessibility.
                val size = with(density) { viewport.width.toDp() to viewport.height.toDp() }
                // Keep every WebView leaf inside the physical draw area. Measuring a landscape
                // spread at full size under a portrait root leaves its right leaf clipped out,
                // so Chromium's visual/draw fences never finish. Scale only the hidden drawing;
                // its measured size and CSS viewport must remain the target orientation's size.
                val preparationScale = if (active) 1f else minOf(1f,
                    current.width.toFloat() / viewport.width.coerceAtLeast(1),
                    current.height.toFloat() / viewport.height.coerceAtLeast(1))
                Box(modifier = Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(size.first, size.second)
                    .zIndex(if (active) 1f else 0f)
                    .graphicsLayer {
                        alpha = if (active) 1f else 0.001f
                        scaleX = preparationScale; scaleY = preparationScale
                        transformOrigin = TransformOrigin(0f, 0f)
                        clip = true
                    }
                    .then(if (active) Modifier else Modifier.clearAndSetSemantics { }.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }), propagateMinConstraints = true) { content(viewport, active, events) }
            }
        }
    }
}
