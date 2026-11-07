package com.huangder.lumibooks.ui.icons

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ControlIconRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fingerprintButtonGainsWeightWhileTheHeaderKeepsItsOriginalAsset() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        for (dp in listOf(13, 18, 24)) {
            val pixels = dp * 3
            val renders = listOf(
                com.huangder.lumibooks.R.drawable.ic_hidden_library_fingerprint,
                com.huangder.lumibooks.R.drawable.ic_hidden_library_fingerprint_control
            ).map { id ->
                Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888).also { bitmap ->
                    context.getDrawable(id)!!.apply {
                        setBounds(0, 0, pixels, pixels)
                        draw(android.graphics.Canvas(bitmap))
                    }
                }
            }
            val coverage = renders.map { bitmap ->
                var total = 0L
                for (y in 0 until pixels) for (x in 0 until pixels) total += android.graphics.Color.alpha(bitmap.getPixel(x, y))
                total
            }
            assertTrue(coverage[1] > coverage[0] && coverage[1] < coverage[0] * 1.5)
            renders.forEach { it.recycle() }
        }
    }

    @Test fun smallControlOutlinesGainWeightWithoutClippingInLightDarkAndDisabledStates() {
        val icons = listOf(AppIcons.ArrowLeft, AppIcons.Plus, AppIcons.MagnifyingGlass,
            AppIcons.SortAscending, AppIcons.CaretDown, AppIcons.Bookmark.regular, AppIcons.Tag)
        val names = listOf("Back", "Add", "Search", "Sort", "Chevron", "Bookmark", "Tag")
        lateinit var painters: List<Painter>
        compose.setContent {
            painters = icons.flatMap { listOf(it, controlIcon(it)) }.map { rememberVectorPainter(it) }
        }
        compose.runOnIdle {
            val sheet = Bitmap.createBitmap(1440, 1420, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(sheet)
            canvas.drawColor(android.graphics.Color.WHITE)
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 17f; color = android.graphics.Color.BLACK }
            names.forEachIndexed { i, name -> canvas.drawText("$name: before / after", i * 180f + 180f, 27f, text) }
            var row = 0
            for (dark in listOf(false, true)) for (alpha in listOf(1f, .38f)) for (dp in listOf(13, 18, 24)) {
                val top = row * 110 + 50
                val background = if (dark) 0xFF202124.toInt() else android.graphics.Color.WHITE
                text.color = if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                val bg = Paint().apply { color = background }
                canvas.drawRect(0f, top.toFloat(), 1440f, top + 110f, bg)
                canvas.drawText("${dp}dp", 15f, top + 35f, text)
                canvas.drawText(if (alpha < 1f) "disabled" else "enabled", 15f, top + 60f, text)
                icons.indices.forEach { index ->
                    val pixels = dp * 3
                    val renders = (0..1).map { variant ->
                        Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888).also { bitmap ->
                            CanvasDrawScope().draw(Density(3f), LayoutDirection.Ltr,
                                Canvas(bitmap.asImageBitmap()), Size(pixels.toFloat(), pixels.toFloat())) {
                                with(painters[index * 2 + variant]) {
                                    draw(size, alpha = alpha, colorFilter = ColorFilter.tint(if (dark) Color.White else Color.Black))
                                }
                            }
                        }
                    }
                    fun coverage(bitmap: Bitmap): Long {
                        var total = 0L
                        for (y in 0 until pixels) for (x in 0 until pixels) total += android.graphics.Color.alpha(bitmap.getPixel(x, y))
                        return total
                    }
                    val before = coverage(renders[0]); val after = coverage(renders[1])
                    assertTrue("${names[index]} at ${dp}dp must gain a small amount of weight", after > before && after < before * 1.5)
                    for (edge in 0 until pixels) {
                        assertTrue(android.graphics.Color.alpha(renders[1].getPixel(edge, 0)) == 0)
                        assertTrue(android.graphics.Color.alpha(renders[1].getPixel(edge, pixels - 1)) == 0)
                        assertTrue(android.graphics.Color.alpha(renders[1].getPixel(0, edge)) == 0)
                        assertTrue(android.graphics.Color.alpha(renders[1].getPixel(pixels - 1, edge)) == 0)
                    }
                    renders.forEachIndexed { variant, bitmap ->
                        canvas.drawBitmap(bitmap, 180f + index * 180f + variant * 84f, top + 20f, null)
                        bitmap.recycle()
                    }
                }
                row++
            }
            val output = File("build/control-icons-comparison.png")
            output.parentFile.mkdirs()
            output.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            sheet.recycle()
        }
    }
}
