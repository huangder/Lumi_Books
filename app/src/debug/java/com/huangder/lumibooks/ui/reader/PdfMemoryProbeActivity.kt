package com.huangder.lumibooks.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.PageRenderMode
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Real-device PDF rendering probe inside the debug APK; leaves books/preferences untouched. */
class PdfMemoryProbeActivity : ComponentActivity() {
    private var source: BitmapPageSource? = null
    private val results = StringBuilder()
    private lateinit var status: TextView
    private lateinit var image: ProbeImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        status = TextView(this)
        image = ProbeImageView().apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(image, LinearLayout.LayoutParams(-1, 0, 1f))
        })
        lifecycleScope.launch {
            try {
                val path = intent.getStringExtra("path")
                    ?: File(getExternalFilesDir(null), "pdf-original-probe.pdf").path
                val pageSource = requireNotNull(BitmapPageSourceFactory.create(this@PdfMemoryProbeActivity, path, BookFormat.PDF))
                source = pageSource
                val width = resources.displayMetrics.widthPixels
                record("OPEN pages=${pageSource.pageCount} width=$width heapMax=${Runtime.getRuntime().maxMemory()}")
                val pages = intent.getStringExtra("pages")?.split(',')?.map { it.trim().toInt() - 1 }
                    ?: listOf(0, 1, 2, 199, 405, 406, 407, 590, 999, 1499, pageSource.pageCount - 1)
                var renders = 0
                for (page in pages) {
                    require(page in 0 until pageSource.pageCount)
                    pageSource.updateViewport(RasterViewport(setOf(page), anchor = page, width = width,
                        scrolling = true, backgroundPreparationEnabled = true))
                    val sizes = listOf(PageRenderMode.NORMAL to width, PageRenderMode.HIGH to width,
                        PageRenderMode.NATIVE to width, PageRenderMode.NATIVE to width * 5)
                    for ((mode, target) in sizes) {
                        val started = SystemClock.elapsedRealtime()
                        val bitmap = requireNotNull(pageSource.renderPage(page, target, mode))
                        if (intent.getBooleanExtra("sharpen", true) && mode == PageRenderMode.NATIVE) {
                            val sharp = withContext(Dispatchers.Default) { sharpenReaderBitmap(bitmap, 0.5f) }
                            display(sharp)
                            image.setImageDrawable(null)
                            if (sharp !== bitmap) sharp.recycle()
                        } else display(bitmap)
                        val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                        record("PASS page=${page + 1} mode=$mode target=$target bitmap=${bitmap.width}x${bitmap.height} bytes=${bitmap.allocationByteCount} ms=${SystemClock.elapsedRealtime() - started} pssKiB=${memory.totalPss}")
                        renders++
                    }
                }
                record("DONE renders=$renders pages=${pages.size}")
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Log.e(TAG, "FAIL", error)
                record("FAIL ${error.javaClass.simpleName}: ${error.message}")
            } finally {
                image.setImageDrawable(null)
                source?.close()
                source = null
                withContext(Dispatchers.IO) {
                    File(getExternalFilesDir(null), "pdf-memory-probe.txt").writeText(results.toString())
                }
            }
        }
    }

    private suspend fun display(bitmap: Bitmap) {
        val before = image.draws
        image.setImageBitmap(bitmap)
        // Exercise actual hardware drawing, including texture upload, not just native decoding.
        repeat(20) {
            delay(50)
            if (image.draws > before) return
        }
        error("Bitmap did not reach a drawn frame")
    }

    private fun record(line: String) {
        results.appendLine(line)
        status.text = line
        Log.i(TAG, line)
    }

    override fun onDestroy() {
        image.setImageDrawable(null)
        source?.close()
        source = null
        super.onDestroy()
    }

    private inner class ProbeImageView : ImageView(this@PdfMemoryProbeActivity) {
        var draws = 0
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (drawable != null) draws++
        }
    }

    private companion object { const val TAG = "PdfMemoryProbe" }
}
