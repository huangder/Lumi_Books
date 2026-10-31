package com.huangder.lumibooks.ui.reader

import android.graphics.*
import android.media.ImageReader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer

/** Copies viewport pixels only; the final row's alignment padding need not be mapped. */
internal fun copyEpubSnapshotRows(source: ByteBuffer, target: ByteBuffer, width: Int, height: Int, rowStride: Int) {
    val rowBytes = width * 4
    require(width > 0 && height > 0 && rowStride >= rowBytes)
    val rows = source.duplicate()
    val start = rows.position()
    require(rows.remaining().toLong() >= (height - 1L) * rowStride + rowBytes)
    target.clear()
    for (row in 0 until height) {
        val offset = start + row * rowStride
        rows.limit(offset + rowBytes)
        rows.position(offset)
        target.put(rows)
    }
    target.flip()
}

/** One viewport-sized capture pipeline; exported frames remain owned by the host's lease pool. */
@RequiresApi(Build.VERSION_CODES.Q)
internal class EpubHardwareSnapshot(val width: Int, val height: Int) : AutoCloseable {
    private val node = RenderNode("epub-page-snapshot")
    private val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    private val renderer = HardwareRenderer()
    private var staging: ByteBuffer? = null
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
            val output = reusable?.takeIf { b -> !b.isRecycled && b.width == width && b.height == height }
                ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                if (plane.rowStride == width * 4) output.copyPixelsFromBuffer(plane.buffer)
                else {
                    val packed = staging ?: ByteBuffer.allocateDirect(width * height * 4).also { staging = it }
                    copyEpubSnapshotRows(plane.buffer, packed, width, height, plane.rowStride)
                    // Copy pixels without another density-dependent bitmap draw.
                    output.copyPixelsFromBuffer(packed)
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
        staging = null
    }
}
