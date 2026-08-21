package com.huangder.lumibooks.ui.theme

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalView

/**
 * Runtime capability used to keep Liquid Glass out of unsupported surfaces.
 * API 31 is the first Android release with the RenderEffect primitive that
 * Backdrop uses for the real blur path. The HDR flag is intentionally
 * independent: a non-HDR display can still render Liquid Glass normally.
 */
data class LiquidGlassCapability(
    val supported: Boolean,
    val hdrSupported: Boolean
)

fun effectiveAppTheme(appTheme: String, capability: LiquidGlassCapability): String =
    if (appTheme == "liquid_glass" && !capability.supported) "lumi" else appTheme

fun detectLiquidGlassCapability(view: View, eInkMode: Boolean = false): LiquidGlassCapability {
    if (eInkMode || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return LiquidGlassCapability(supported = false, hdrSupported = false)
    }

    val renderEffectAvailable = runCatching {
        RenderEffect.createBlurEffect(1f, 1f, Shader.TileMode.CLAMP)
    }.isSuccess
    val supported = view.isHardwareAccelerated && renderEffectAvailable
    // Android 15 is the first release that lets the app request limited HDR headroom.
    // On older releases COLOR_MODE_HDR tone-maps the entire window, which can dim SDR
    // content and produce banding on some OEM pipelines. Keep Liquid Glass available
    // there, but hide the HDR-only pressed highlight option.
    val hdrSupported = supported &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
        view.display?.isHdr == true &&
        view.display?.isWideColorGamut == true
    return LiquidGlassCapability(supported = supported, hdrSupported = hdrSupported)
}

@Composable
fun rememberLiquidGlassCapability(
    eInkMode: Boolean = false,
    view: View = LocalView.current
): LiquidGlassCapability {
    var capability by remember(view, eInkMode) {
        mutableStateOf(detectLiquidGlassCapability(view, eInkMode))
    }
    LaunchedEffect(view, eInkMode) {
        // The first composition can happen before the window has attached its hardware
        // renderer/display. Re-check on the first frame so supported devices do not
        // temporarily lose the theme option.
        withFrameNanos { }
        capability = detectLiquidGlassCapability(view, eInkMode)
    }
    return capability
}
