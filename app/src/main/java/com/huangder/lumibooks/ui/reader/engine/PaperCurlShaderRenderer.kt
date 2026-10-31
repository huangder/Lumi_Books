package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi

/**
 * GPU renderer: the turning sheet is recorded once into a [RenderNode] and bent
 * per pixel by a runtime shader, so a turn starts without copying page pixels
 * and every frame stays inside the normal HWUI frame.
 */
@RequiresApi(33)
internal class PaperCurlShaderRenderer : PaperCurlRenderer {
    private val turning = CurlPageFrame()
    private val under = CurlPageFrame()
    /** Page content in its own cached layer: a new effect each frame must not redraw the text. */
    private val contentNode = RenderNode("PaperCurlPage").apply { setUseCompositingLayer(true, null) }
    private val turningNode = RenderNode("PaperCurlSheet")
    private val underNode = RenderNode("PaperCurlUnder")
    private val shader = RuntimeShader(PAPER_CURL_SHADER)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val destination = Rect()
    private var surface: PageAnimationSurface? = null
    private var recorded = false

    override fun capture(surface: PageAnimationSurface, turning: View, under: View): Boolean {
        val width = surface.width
        val height = surface.height
        if (width <= 0 || height <= 0) return false
        this.surface = surface
        this.turning.capture(turning)
        this.under.capture(under)
        destination.set(0, 0, width, height)
        record(contentNode, this.turning, surface, width, height)
        record(underNode, this.under, surface, width, height)
        turningNode.setRenderEffect(null)
        turningNode.setPosition(0, 0, width, height)
        val canvas = turningNode.beginRecording(width, height)
        try {
            canvas.drawRenderNode(contentNode)
        } finally {
            turningNode.endRecording()
        }
        recorded = true
        return true
    }

    private fun record(node: RenderNode, frame: CurlPageFrame, surface: PageAnimationSurface, width: Int, height: Int) {
        node.setRenderEffect(null)
        node.setPosition(0, 0, width, height)
        val canvas = node.beginRecording(width, height)
        try {
            frame.draw(surface, canvas, paint, destination)
        } finally {
            node.endRecording()
        }
    }

    override fun draw(canvas: Canvas, pose: CurlPose, look: CurlLook) {
        if (!recorded) return
        if (!canvas.isHardwareAccelerated) {
            // Software snapshots of the reader cannot replay render nodes.
            surface?.let { turning.draw(it, canvas, paint, destination) }
            return
        }
        if (pose.isFlat) {
            turningNode.setRenderEffect(null)
            canvas.drawRenderNode(turningNode)
            return
        }
        canvas.drawRenderNode(underNode)
        shader.setFloatUniform("size", look.width, look.height)
        shader.setFloatUniform("mirror", if (look.mirror) 1f else 0f)
        shader.setFloatUniform("foldN", pose.nx, pose.ny)
        shader.setFloatUniform("foldC", pose.c)
        shader.setFloatUniform("radius", pose.r)
        shader.setFloatUniform("wrap", pose.alpha)
        shader.setFloatUniform("backInk", CurlLook.BACK_INK)
        shader.setFloatUniform("shadowReach", look.shadowReach)
        shader.setColorUniform("paper", look.paper or (0xFF shl 24))
        // Uniforms are copied into each effect, so a new effect carries this frame's pose.
        turningNode.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "page"))
        canvas.drawRenderNode(turningNode)
    }

    override fun release() {
        recorded = false
        turningNode.setRenderEffect(null)
        turningNode.discardDisplayList()
        contentNode.discardDisplayList()
        underNode.discardDisplayList()
        turning.clear()
        under.clear()
        surface = null
    }

    override fun destroy() = release()

    companion object {
        @Volatile private var warmed = false

        /**
         * Compiles the curl's GPU program before the first turn: one 1×1 frame with
         * the same layer and effect structure, rendered off screen on a worker thread.
         * HWUI shares its program cache across renderers, so the first real frame
         * does not stall (about 100 ms on a Snapdragon 8 Elite otherwise).
         */
        fun warmUp() {
            if (warmed) return
            warmed = true
            val worker = android.os.HandlerThread("PaperCurlWarmUp").apply { start() }
            android.os.Handler(worker.looper).post {
                var reader: android.media.ImageReader? = null
                var renderer: android.graphics.HardwareRenderer? = null
                try {
                    reader = android.media.ImageReader.newInstance(1, 1, android.graphics.PixelFormat.RGBA_8888, 2,
                        android.hardware.HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or
                            android.hardware.HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE)
                    reader.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, null)
                    val content = RenderNode("PaperCurlWarmUpPage").apply {
                        setPosition(0, 0, 1, 1)
                        setUseCompositingLayer(true, null)
                        beginRecording(1, 1).drawColor(android.graphics.Color.WHITE)
                        endRecording()
                    }
                    val shader = RuntimeShader(PAPER_CURL_SHADER).apply {
                        setFloatUniform("size", 1f, 1f)
                        setFloatUniform("mirror", 0f)
                        setFloatUniform("foldN", 1f, 0f)
                        setFloatUniform("foldC", 0.5f)
                        setFloatUniform("radius", 0.25f)
                        setFloatUniform("wrap", PaperCurl.WRAP)
                        setFloatUniform("backInk", CurlLook.BACK_INK)
                        setFloatUniform("shadowReach", 0.1f)
                        setColorUniform("paper", android.graphics.Color.WHITE)
                    }
                    val sheet = RenderNode("PaperCurlWarmUpSheet").apply {
                        setPosition(0, 0, 1, 1)
                        beginRecording(1, 1).drawRenderNode(content)
                        endRecording()
                        setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "page"))
                    }
                    val root = RenderNode("PaperCurlWarmUp").apply {
                        setPosition(0, 0, 1, 1)
                        beginRecording(1, 1).drawRenderNode(sheet)
                        endRecording()
                    }
                    renderer = android.graphics.HardwareRenderer().apply {
                        setSurface(reader.surface)
                        setContentRoot(root)
                    }
                    renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
                } catch (error: RuntimeException) {
                    Log.w("PaperCurl", "Curl shader warm-up skipped", error)
                } finally {
                    renderer?.destroy()
                    reader?.close()
                    worker.quitSafely()
                }
            }
        }
    }
}

/**
 * Inverse of [PaperCurl.bend] per pixel. Layers from top: the flap and the roll
 * past its apex (paper back, mirrored ink), the flat sheet and rising roll
 * (front ink, compressed across the fold), then the cast shadow on the page
 * beneath. Coordinates are canonical: the spine sits at x = 0.
 */
private const val PAPER_CURL_SHADER = """
uniform shader page;
uniform float2 size;
uniform float mirror;
uniform float2 foldN;
uniform float foldC;
uniform float radius;
uniform float wrap;
uniform float backInk;
uniform float shadowReach;
layout(color) uniform half4 paper;

const float HALF_PI = 1.5707963;
const float PI = 3.1415927;

float2 toLayer(float2 q) {
    return float2(mirror > 0.5 ? size.x - q.x : q.x, q.y);
}

half4 ink(float2 q) {
    half4 c = page.eval(toLayer(q));
    return c + paper * (1.0 - c.a);
}

// Box filter across the fold where the paper is compressed k:1.
half4 inkAcross(float2 q, float k) {
    if (k < 1.6) return ink(q);
    float2 delta = foldN * (min(k, 10.0) * 0.33);
    return (ink(q - delta) + ink(q) + ink(q + delta)) * 0.33333;
}

// Signed screen distance to the sheet's edges for a page point compressed k:1 along the fold normal.
float edgeDist(float2 q, float k) {
    float gx = length(float2(foldN.x * k, foldN.y));
    float gy = length(float2(foldN.y * k, foldN.x));
    return min(min(q.x, size.x - q.x) / gx, min(q.y, size.y - q.y) / gy);
}

// Paper length beyond the fold on the row through tangential coordinate qt.
float rowReach(float qt) {
    float lim = (size.x + qt * foldN.y) / foldN.x;
    if (foldN.y > 0.0001) lim = min(lim, (size.y - qt * foldN.x) / foldN.y);
    if (foldN.y < -0.0001) lim = min(lim, -qt * foldN.x / foldN.y);
    return lim - foldC;
}

half3 shade(half3 c, float f) { return c * f; }

half4 main(float2 fragCoord) {
    float2 p = float2(mirror > 0.5 ? size.x - fragCoord.x : fragCoord.x, fragCoord.y);
    float2 n = foldN;
    float d = dot(p, n) - foldC;
    float r = radius;
    float sa = sin(wrap);
    float ca = cos(wrap);
    float reach = rowReach(dot(p, float2(-n.y, n.x)));

    // How high this row of paper is lifted; scales both cast shadows.
    float tip = reach <= wrap * r ? r * (1.0 - cos(clamp(reach / r, 0.0, PI)))
        : r * (1.0 - ca) + (reach - wrap * r) * sa;
    float lift = clamp(tip / (2.0 * r), 0.0, 1.0);

    // Page beneath: transparent, darkened just past the sheet's silhouette.
    half4 under = half4(0.0);
    float silhouette = reach >= HALF_PI * r ? r : r * sin(max(reach, 0.0) / r);
    if (reach > 0.0 && d > silhouette) {
        under = half4(0.0, 0.0, 0.0, 0.17 * lift * exp(-(d - silhouette) / (0.36 * shadowReach)));
    }

    // Front of the sheet: flat, or rising into the roll.
    half4 mid = half4(0.0);
    float midCover = 0.0;
    if (d <= 0.0) {
        mid = ink(p);
        midCover = 1.0;
    } else if (d < r) {
        float th = asin(d / r);
        float2 q = p + n * (r * th - d);
        float k = min(1.0 / max(cos(th), 0.02), 50.0);
        midCover = clamp(edgeDist(q, k) + 0.5, 0.0, 1.0) * clamp(r - d + 0.5, 0.0, 1.0);
        half4 c = inkAcross(q, k);
        mid = half4(shade(c.rgb, 1.0 - 0.22 * pow(sin(th), 4.0)), c.a);
    }

    // Back of the sheet: the roll past its apex, then the flap.
    half4 top = half4(0.0);
    float topCover = 0.0;
    if (wrap > HALF_PI + 0.001 && d < r) {
        float th = wrap;
        float s = wrap * r + (r * sa - d) / max(-ca, 0.02);
        if (d > r * sa) {
            th = PI - asin(clamp(d / r, -1.0, 1.0));
            s = r * th;
        }
        float2 q = p + n * (s - d);
        float k = min(1.0 / max(abs(cos(th)), 0.02), 50.0);
        float e = edgeDist(q, k);
        topCover = clamp(e + 0.5, 0.0, 1.0) * clamp(r - d + 0.5, 0.0, 1.0);
        if (topCover > 0.0) {
            half4 c = inkAcross(q, k);
            half3 back = mix(paper.rgb, c.rgb, backInk);
            top = half4(shade(back, 0.86 + 0.14 * sqrt(max(-cos(th), 0.0))), 1.0);
        }
        // The lifted back shades the paper below it.
        float strength = 0.16 * lift;
        float darken = strength * exp(-max(-e, 0.0) / (0.42 * shadowReach));
        mid = half4(mid.rgb * (1.0 - darken), mid.a);
    }

    half4 color = mix(under, mid, midCover);
    return mix(color, top, topCover);
}
"""
