package com.huangder.lumibooks.ui.bookshare

import android.app.Application
import android.graphics.*
import com.huangder.lumibooks.domain.model.*
import com.huangder.lumibooks.ui.excerpt.writePng
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookShareRendererTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val output = File("build/reading-insights-qa").apply { mkdirs() }
    private fun snapshot(review:String="阅读让平凡的日子有了新的光。\nA quiet moment, a new perspective.", coverPath:String?=null) = BookShareSnapshot(
        Book(id="book",title="山间的一日 · A Day in the Mountains",author="Lumi",filePath="",coverPath=coverPath,
            format=BookFormat.EPUB,lastReadTime=0,readingProgress=.42f,createdAt=0),
        review,4.5f,LocalDate.of(2026,11,4),ActivityDuration(1_200_000,600_000),.42f)
    @Test fun allEditionStylesRenderBothContentKindsAndKeepTileBoundariesConsistent() {
        val cover = File(output,"sample-cover.png")
        val bitmap = Bitmap.createBitmap(240,340,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        canvas.drawColor(Color.rgb(72,109,95))
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(238,231,201);textSize=30f;typeface=Typeface.SERIF }
        canvas.drawText("A DAY",32f,100f,paint);canvas.drawText("IN THE",32f,145f,paint);canvas.drawText("MOUNTAINS",20f,190f,paint)
        cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        for (style in BookShareEdition.styles) for (kind in BookShareKind.entries) {
            val document=BookShareRenderer.prepare(context,snapshot(coverPath=cover.absolutePath),kind,style)
            assertEquals(920,document.width)
            assertTrue(document.height>=1100)
            val bytes=ByteArrayOutputStream().also(document::writePng).toByteArray()
            File(output,"book-${kind.name.lowercase()}-${style.name.lowercase()}.png").writeBytes(bytes)
            val decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
            try {
                assertEquals(document.height,decoded.height)
                val tile=document.render(510,4)
                try {
                    for(y in 0..3) for(x in 0 until 920 step 11)
                        assertEquals("PNG seam",tile.getPixel(x,y),decoded.getPixel(x,510+y))
                } finally { tile.recycle() }
            } finally { decoded.recycle() }
        }
    }
    @Test fun longMixedLanguageReviewIsNotTruncatedAndMissingCoverStillRenders() {
        val short=BookShareRenderer.prepare(context,snapshot("短评"),BookShareKind.REVIEW,BookShareStyle.SIMPLE)
        val long=BookShareRenderer.prepare(context,snapshot("中文の読書 한국어 English. ".repeat(300)),BookShareKind.REVIEW,BookShareStyle.SIMPLE)
        assertTrue(long.height>short.height*2)
        val tail=long.render(long.height-200,200)
        try { assertEquals(920,tail.width);assertEquals(200,tail.height) } finally { tail.recycle() }
    }
    @Test fun checkInRequiresActivityButRecommendationDoesNotRequireRatingOrReview() {
        val blank=snapshot("").copy(rating=0f,activity=ActivityDuration(),currentProgress=null)
        assertTrue(shareHasContent(blank,BookShareKind.REVIEW))
        assertFalse(shareHasContent(blank,BookShareKind.CHECK_IN))
        assertTrue(shareHasContent(blank.copy(activity=ActivityDuration(0,1)),BookShareKind.CHECK_IN))
        assertFalse(BookShareStyle.SIMPLE.premium)
        assertTrue(BookShareStyle.COVER.premium)
        assertTrue(BookShareStyle.TICKET.premium)
    }
}
