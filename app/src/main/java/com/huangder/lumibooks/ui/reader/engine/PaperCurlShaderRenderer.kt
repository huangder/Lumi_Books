package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import kotlin.math.cos
import kotlin.math.sin

/**
 * GPU renderer: bends a frozen page bitmap directly, or records live content
 * into a [RenderNode] when no viewport-sized bitmap is available.
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
    private val curlPaint = Paint()
    private val emptyInput = LinearGradient(0f, 0f, 1f, 0f, Color.TRANSPARENT, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    private val destination = Rect()
    private var surface: PageAnimationSurface? = null
    private var recorded = false
    private var directBitmap = false

    override fun capture(surface: PageAnimationSurface, turning: View, under: View): Boolean {
        val width = surface.width
        val height = surface.height
        if (width <= 0 || height <= 0) return false
        this.surface = surface
        this.turning.capture(turning)
        this.under.capture(under)
        destination.set(0, 0, width, height)
        record(underNode, this.under, surface, width, height)
        turningNode.setRenderEffect(null)
        val bitmap = this.turning.bitmap?.takeIf { it.width == width && it.height == height }
        if (bitmap != null) {
            shader.setPageBitmap(bitmap)
        } else {
            if (directBitmap) shader.setInputShader("page", emptyInput)
            recordTurning(surface, width, height)
        }
        directBitmap = bitmap != null
        recorded = true
        return true
    }

    private fun recordTurning(surface: PageAnimationSurface, width: Int, height: Int) {
        record(contentNode, this.turning, surface, width, height)
        turningNode.setPosition(0, 0, width, height)
        val canvas = turningNode.beginRecording(width, height)
        try {
            canvas.drawRenderNode(contentNode)
        } finally {
            turningNode.endRecording()
        }
    }

    /**
     * HWUI destroys a node's display list, including one recorded but not yet
     * synced, once a frame leaves it out of the tree. A quick re-lift captures
     * during the previous turn and its first flat frame omits [underNode], so
     * the page beneath would stay blank; re-record whatever was dropped.
     */
    private fun restoreDroppedRecordings() {
        val active = surface ?: return
        val width = destination.width()
        val height = destination.height()
        if (!underNode.hasDisplayList()) record(underNode, under, active, width, height)
        if (!directBitmap && (!turningNode.hasDisplayList() || !contentNode.hasDisplayList())) {
            recordTurning(active, width, height)
        }
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
        restoreDroppedRecordings()
        if (pose.isFlat) {
            if (directBitmap) {
                surface?.let { turning.draw(it, canvas, paint, destination) }
            } else {
                turningNode.setRenderEffect(null)
                canvas.drawRenderNode(turningNode)
            }
            return
        }
        canvas.drawRenderNode(underNode)
        shader.setFloatUniform("size", look.width, look.height)
        shader.setFloatUniform("mirror", if (look.mirror) 1f else 0f)
        shader.setFloatUniform("foldN", pose.nx, pose.ny)
        shader.setFloatUniform("foldC", pose.c)
        shader.setFloatUniform("radius", pose.r)
        shader.setWrapGeometry(pose.alpha)
        shader.setFloatUniform("backInk", CurlLook.BACK_INK)
        shader.setFloatUniform("shadowReach", look.shadowReach)
        shader.setColorUniform("paper", look.paper or (0xFF shl 24))
        if (directBitmap) {
            curlPaint.shader = shader
            try {
                canvas.drawRect(destination, curlPaint)
            } finally {
                curlPaint.shader = null
            }
        } else {
            turningNode.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "page"))
            canvas.drawRenderNode(turningNode)
        }
    }

    override fun release() {
        recorded = false
        if (directBitmap) shader.setInputShader("page", emptyInput)
        directBitmap = false
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
         * Warms both GPU draw paths in a tiny offscreen frame on a worker thread.
         */
        fun warmUp() {
            if (warmed) return
            warmed = true
            val worker = android.os.HandlerThread("PaperCurlWarmUp").apply { start() }
            android.os.Handler(worker.looper).post {
                var reader: android.media.ImageReader? = null
                var renderer: android.graphics.HardwareRenderer? = null
                try {
                    reader = android.media.ImageReader.newInstance(2, 1, android.graphics.PixelFormat.RGBA_8888, 2,
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
                        setWrapGeometry(PaperCurl.WRAP)
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
                    shader.setPageBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
                        eraseColor(Color.WHITE)
                    })
                    val bitmapPaint = Paint().apply { this.shader = shader }
                    val root = RenderNode("PaperCurlWarmUp").apply {
                        setPosition(0, 0, 2, 1)
                        val canvas = beginRecording(2, 1)
                        canvas.drawRenderNode(sheet)
                        canvas.translate(1f, 0f)
                        canvas.drawRect(0f, 0f, 1f, 1f, bitmapPaint)
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

@RequiresApi(33)
private fun RuntimeShader.setWrapGeometry(wrap: Float) =
    setFloatUniform("wrapGeometry", wrap, sin(wrap), cos(wrap))

@RequiresApi(33)
private fun RuntimeShader.setPageBitmap(bitmap: Bitmap) {
    setInputShader("page", BitmapShader(bitmap, Shader.TileMode.DECAL, Shader.TileMode.DECAL).apply {
        setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
    })
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
uniform float3 wrapGeometry;
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
    float wrap = wrapGeometry.x;
    float sa = wrapGeometry.y;
    float ca = wrapGeometry.z;
    bool hasBack = wrap > HALF_PI + 0.001;
    if (d <= 0.0 && !hasBack) return ink(p);
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
        float sine = d / r;
        float cosine = sqrt(max(1.0 - sine * sine, 0.0));
        float2 q = p + n * (r * asin(sine) - d);
        float k = min(1.0 / max(cosine, 0.02), 50.0);
        midCover = clamp(edgeDist(q, k) + 0.5, 0.0, 1.0) * clamp(r - d + 0.5, 0.0, 1.0);
        half4 c = inkAcross(q, k);
        float sineSquared = sine * sine;
        mid = half4(shade(c.rgb, 1.0 - 0.22 * sineSquared * sineSquared), c.a);
    }

    // Back of the sheet: the roll past its apex, then the flap.
    half4 top = half4(0.0);
    float topCover = 0.0;
    if (hasBack && d < r) {
        float cosine = ca;
        float s = wrap * r + (r * sa - d) / max(-ca, 0.02);
        if (d > r * sa) {
            float sine = clamp(d / r, -1.0, 1.0);
            s = r * (PI - asin(sine));
            cosine = -sqrt(max(1.0 - sine * sine, 0.0));
        }
        float2 q = p + n * (s - d);
        float k = min(1.0 / max(abs(cosine), 0.02), 50.0);
        float e = edgeDist(q, k);
        topCover = clamp(e + 0.5, 0.0, 1.0) * clamp(r - d + 0.5, 0.0, 1.0);
        if (topCover > 0.0) {
            half4 c = inkAcross(q, k);
            half3 back = mix(paper.rgb, c.rgb, backInk);
            top = half4(shade(back, 0.86 + 0.14 * sqrt(max(-cosine, 0.0))), 1.0);
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
