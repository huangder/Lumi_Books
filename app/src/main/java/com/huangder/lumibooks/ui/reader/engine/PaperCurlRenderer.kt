package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.view.View
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Draws one frozen page turn for a [CurlPose]. */
internal interface PaperCurlRenderer {
    /** Freezes the turning sheet and the page beneath it for one turn. */
    fun capture(surface: PageAnimationSurface, turning: View, under: View): Boolean
    fun draw(canvas: Canvas, pose: CurlPose, look: CurlLook)
    /** Ends the turn and returns borrowed page frames. */
    fun release()
    fun destroy()
}

/** Per-turn appearance shared by both renderers. */
internal class CurlLook {
    var width = 1f
    var height = 1f
    var mirror = false
    var paper = 0
    var shadowReach = 1f

    companion object {
        /** Strength of the mirrored ink visible through the back of the sheet. */
        const val BACK_INK = 0.3f
        fun frontShade(theta: Float): Float = 1f - 0.22f * sin(theta).let { it * it * it * it }
        fun backShade(theta: Float): Float = 0.86f + 0.14f * sqrt(max(-kotlin.math.cos(theta), 0f))
    }
}

/** One page role for the duration of a turn: a leased or cached bitmap, or the live view. */
internal class CurlPageFrame {
    var bitmap: Bitmap? = null
        private set
    var view: View? = null
        private set
    private var lease: RenderResourceLease<Bitmap>? = null

    fun capture(page: View) {
        clear()
        view = page
        (page as? CurlFrameSource)?.acquireCurlFrame()?.let { borrowed ->
            if (!borrowed.resource.isRecycled) {
                lease = borrowed
                bitmap = borrowed.resource
                return
            }
            borrowed.close()
        }
        bitmap = (page as? PageBitmapSource)?.pageBitmap?.takeUnless { it.isRecycled }
    }

    /** Draws the page at the surface size, ignoring the view's live translation or alpha. */
    fun draw(surface: PageAnimationSurface, canvas: Canvas, paint: Paint, destination: Rect) {
        canvas.drawColor(surface.bgColor)
        val page = view ?: return
        val frozen = bitmap
        if (frozen != null && !frozen.isRecycled) {
            canvas.drawBitmap(frozen, null, destination, paint)
            return
        }
        // A slot filled during a fast sequence may not have had its layout pass yet.
        if (page.isLayoutRequested || page.width != surface.width || page.height != surface.height) {
            page.measure(View.MeasureSpec.makeMeasureSpec(surface.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(surface.height, View.MeasureSpec.EXACTLY))
            page.layout(0, 0, surface.width, surface.height)
        }
        if (surface.drawPageDirectly(canvas, page)) return
        page.draw(canvas)
    }

    fun clear() {
        lease?.close()
        lease = null
        bitmap = null
        view = null
    }
}

/**
 * Canvas fallback: bends the page bitmap with two vertex meshes. The front pass
 * holds the flat sheet and the rising roll; the back pass holds the roll past
 * its apex and the flap, drawn on top with faded mirrored ink.
 */
internal class PaperCurlMeshRenderer : PaperCurlRenderer {
    private val turning = CurlPageFrame()
    private val under = CurlPageFrame()
    private var turningBitmap: Bitmap? = null
    private var underBitmap: Bitmap? = null
    private var ownedTurning: Bitmap? = null
    private var ownedUnder: Bitmap? = null
    private var paperDot: Bitmap? = null
    private var paperDotColor = 0
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val inkPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint()
    private val destination = Rect()
    private val bend = CurlBend()
    private var columns = 0
    private var rows = 0
    private var front = FloatArray(0)
    private var back = FloatArray(0)
    private var frontColors = IntArray(0)
    private var backColors = IntArray(0)

    override fun capture(surface: PageAnimationSurface, turning: View, under: View): Boolean {
        this.turning.capture(turning)
        this.under.capture(under)
        turningBitmap = this.turning.bitmap ?: render(surface, this.turning, ownedTurning).also { ownedTurning = it }
        underBitmap = this.under.bitmap ?: render(surface, this.under, ownedUnder).also { ownedUnder = it }
        return turningBitmap != null && underBitmap != null
    }

    private fun render(surface: PageAnimationSurface, frame: CurlPageFrame, reusable: Bitmap?): Bitmap? {
        val width = surface.width
        val height = surface.height
        if (width <= 0 || height <= 0) return null
        val bitmap = reusable?.takeIf { !it.isRecycled && it.width == width && it.height == height }
            ?: run {
                reusable?.recycle()
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            }
        destination.set(0, 0, width, height)
        frame.draw(surface, Canvas(bitmap), paint, destination)
        return bitmap
    }

    override fun draw(canvas: Canvas, pose: CurlPose, look: CurlLook) {
        val sheet = turningBitmap?.takeUnless { it.isRecycled } ?: return
        val below = underBitmap?.takeUnless { it.isRecycled } ?: return
        destination.set(0, 0, look.width.roundToInt(), look.height.roundToInt())
        canvas.drawColor(look.paper)
        if (pose.isFlat) {
            canvas.drawBitmap(sheet, null, destination, paint)
            return
        }
        canvas.drawBitmap(below, null, destination, paint)
        drawUnderShadow(canvas, pose, look)
        val hasBack = buildMesh(pose, look)
        val dot = paperDot(look.paper)
        canvas.drawBitmapMesh(dot, columns, rows, front, 0, frontColors, 0, null)
        canvas.drawBitmapMesh(sheet, columns, rows, front, 0, frontColors, 0, paint)
        if (hasBack) {
            canvas.drawBitmapMesh(dot, columns, rows, back, 0, backColors, 0, null)
            inkPaint.alpha = (CurlLook.BACK_INK * 255f).roundToInt()
            canvas.drawBitmapMesh(sheet, columns, rows, back, 0, backColors, 0, inkPaint)
        }
    }

    /** Soft shadow the lifted sheet casts on the page beneath, just past the roll. */
    private fun drawUnderShadow(canvas: Canvas, pose: CurlPose, look: CurlLook) {
        val start = pose.c + pose.r
        val length = look.shadowReach * 0.84f
        fun sx(offset: Float) = (pose.nx * offset).let { if (look.mirror) look.width - it else it }
        val y0 = pose.ny * (start - 1f)
        val y1 = pose.ny * (start + length)
        shadowPaint.shader = LinearGradient(sx(start - 1f), y0, sx(start + length), y1,
            intArrayOf(0, 0x2E000000, 0x14000000, 0x07000000, 0),
            floatArrayOf(0f, 1f / (length + 1f), 0.25f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, look.width, look.height, shadowPaint)
        shadowPaint.shader = null
    }

    private fun buildMesh(pose: CurlPose, look: CurlLook): Boolean {
        val w = look.width
        val h = look.height
        val cols = 64
        val rowCount = (cols * h / w).roundToInt().coerceIn(48, 160)
        if (cols != columns || rowCount != rows) {
            columns = cols
            rows = rowCount
            val count = (cols + 1) * (rowCount + 1)
            front = FloatArray(count * 2); back = FloatArray(count * 2)
            frontColors = IntArray(count); backColors = IntArray(count)
        }
        val half = (PI / 2.0).toFloat()
        val apexFront = gray(CurlLook.frontShade(half))
        val apexBack = gray(CurlLook.backShade(half))
        var hasBack = false
        var v = 0
        for (j in 0..rows) {
            val sy = j * h / rows
            for (i in 0..columns) {
                val sx = i * w / columns
                val qx = if (look.mirror) w - sx else sx
                val qn = pose.nx * qx + pose.ny * sy
                val qt = -pose.ny * qx + pose.nx * sy
                PaperCurl.bend(pose, qn - pose.c, bend)
                val backFacing = bend.theta > half
                val frontOffset = if (backFacing) pose.r else bend.offset
                val backOffset = if (backFacing) bend.offset else pose.r
                place(front, v, pose, frontOffset, qt, look)
                place(back, v, pose, backOffset, qt, look)
                frontColors[v] = if (backFacing) apexFront else gray(CurlLook.frontShade(bend.theta))
                backColors[v] = if (backFacing) gray(CurlLook.backShade(bend.theta)) else apexBack
                if (backFacing) hasBack = true
                v++
            }
        }
        return hasBack
    }

    private fun place(out: FloatArray, vertex: Int, pose: CurlPose, offset: Float, qt: Float, look: CurlLook) {
        val n = pose.c + offset
        val x = pose.nx * n - pose.ny * qt
        out[vertex * 2] = if (look.mirror) look.width - x else x
        out[vertex * 2 + 1] = pose.ny * n + pose.nx * qt
    }

    private fun gray(shade: Float): Int {
        val g = (min(max(shade, 0f), 1f) * 255f).roundToInt()
        return (0xFF shl 24) or (g shl 16) or (g shl 8) or g
    }

    private fun paperDot(color: Int): Bitmap {
        val existing = paperDot
        if (existing != null && !existing.isRecycled && paperDotColor == color) return existing
        existing?.recycle()
        paperDotColor = color
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color or (0xFF shl 24))
        }.also { paperDot = it }
    }

    override fun release() {
        turning.clear()
        under.clear()
        turningBitmap = null
        underBitmap = null
    }

    override fun destroy() {
        release()
        ownedTurning?.recycle(); ownedTurning = null
        ownedUnder?.recycle(); ownedUnder = null
        paperDot?.recycle(); paperDot = null
    }
}
