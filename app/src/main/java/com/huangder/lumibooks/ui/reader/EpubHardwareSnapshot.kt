package com.huangder.lumibooks.ui.reader

import android.graphics.*
import android.media.ImageReader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi

/** One viewport-sized capture pipeline; exported frames remain owned by the host's lease pool. */
@RequiresApi(Build.VERSION_CODES.Q)
internal class EpubHardwareSnapshot(val width: Int, val height: Int) : AutoCloseable {
    private val node = RenderNode("epub-page-snapshot")
    private val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    private val renderer = HardwareRenderer()
    private var staging: Bitmap? = null
    private var closed = false
    init {
        node.setPosition(0, 0, width, height)
        renderer.setSurface(reader.surface); renderer.setContentRoot(node)
    }
    fun capture(view: View, reusable: Bitmap?, density: Int, background: (Canvas) -> Unit): Bitmap? {
        check(!closed)
        reader.acquireLatestImage()?.close()
        val recording = node.beginRecording(width, height)
        val alpha = view.alpha; val tx = view.translationX; val ty = view.translationY
        try {
            background(recording); view.alpha = 1f; view.translationX = 0f; view.translationY = 0f
            view.draw(recording)
        } finally { view.alpha = alpha; view.translationX = tx; view.translationY = ty; node.endRecording() }
        renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
        val image = reader.acquireLatestImage() ?: return null
        image.use {
            val plane = it.planes.firstOrNull() ?: return null
            require(plane.pixelStride == 4 && plane.rowStride >= width * 4)
            val paddedWidth = plane.rowStride / 4
            val output = reusable?.takeIf { b -> !b.isRecycled && b.width == width && b.height == height }
                ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                if (paddedWidth == width) output.copyPixelsFromBuffer(plane.buffer)
                else {
                    var scratch = staging
                    if (scratch == null || scratch.width != paddedWidth) {
                        scratch?.recycle(); scratch = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888); staging = scratch
                    }
                    scratch.copyPixelsFromBuffer(plane.buffer)
                    Canvas(output).drawBitmap(scratch, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) })
                }
                output.density = density
                return output
            } catch (error: Exception) {
                if (output !== reusable) output.recycle()
                throw error
            }
        }
    }
    override fun close() {
        if (closed) return
        closed = true
        renderer.setSurface(null); renderer.destroy(); reader.close(); node.discardDisplayList()
        staging?.recycle(); staging = null
    }
}
