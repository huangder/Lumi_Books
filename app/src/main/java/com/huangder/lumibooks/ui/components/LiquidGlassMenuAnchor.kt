package com.huangder.lumibooks.ui.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.clearAndSetSemantics

enum class LiquidGlassMenuAnchorKind { Standalone, Embedded }

@Stable
internal class LiquidGlassMenuAnchor(
    val id: Any,
    val layer: GraphicsLayer,
    val kind: LiquidGlassMenuAnchorKind,
    val cornerRadius: Dp?
) {
    var rootBounds: Rect = Rect.Zero
    var windowBounds by mutableStateOf(Rect.Zero)
    var recorded = false
}

/** Put this before the trigger's background/clip so the entire visible control is handed off. */
fun Modifier.liquidGlassMenuAnchor(
    id: Any? = null,
    kind: LiquidGlassMenuAnchorKind = LiquidGlassMenuAnchorKind.Standalone,
    cornerRadius: Dp? = null
): Modifier = composed {
    val host = LocalLiquidGlassMenuHost.current ?: return@composed this
    val generatedId = remember { Any() }
    val layer = rememberGraphicsLayer()
    val anchor = remember(host, id, kind, cornerRadius, layer) {
        LiquidGlassMenuAnchor(id ?: generatedId, layer, kind, cornerRadius)
    }
    DisposableEffect(host, anchor) {
        host.register(anchor)
        onDispose { host.unregister(anchor) }
    }
    this
        .then(if (host.ownsDrawing(anchor.id)) Modifier.clearAndSetSemantics {} else Modifier)
        .onGloballyPositioned {
            anchor.rootBounds = it.boundsInRoot()
            anchor.windowBounds = it.boundsInWindow()
        }
        .drawWithContent {
            // Freeze the source RenderNode during the handoff. Never capture the overlay
            // back into the source, or glass sampling can form a recursive render tree.
            if (!host.ownsDrawing(anchor.id)) {
                layer.record { this@drawWithContent.drawContent() }
                anchor.recorded = true
                drawLayer(layer)
            }
        }
}
