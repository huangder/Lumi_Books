package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.Disposable
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import com.huangder.lumibooks.domain.model.ReaderEdgeTapMode
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.domain.model.ReaderWritingMode
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderBackgroundLoadingTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private lateinit var loader: PendingImageLoader
    private lateinit var page: PageContentView
    private lateinit var image: ImageView
    private lateinit var source: File

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        loader = PendingImageLoader(ImageLoader.Builder(context).diskCache(null).build())
        Coil.setImageLoader(loader)
        page = PageContentView(context)
        image = page.getChildAt(0) as ImageView
        source = temporaryFolder.newFile("background.jpg")
    }

    @After
    fun tearDown() {
        Coil.reset()
        loader.shutdown()
    }

    @Test
    fun opacityDragKeepsTheLoadedBackgroundWithoutNewRequests() {
        page.setReaderBackground(Color.WHITE, source.path)
        val loaded = ColorDrawable(Color.BLUE)
        loader.succeed(loader.requests.single(), loaded)

        for (opacity in listOf(0.8f, 0.5f, 0f, 0.7f)) {
            page.setReaderBackground(Color.WHITE, source.path, opacity)
            assertSame(loaded, image.drawable)
            assertEquals(opacity, image.alpha, 0.001f)
        }
        assertEquals(1, loader.requests.size)
    }

    @Test
    fun opacityDragDoesNotCancelAPendingBlurRequest() {
        page.setReaderBackground(Color.WHITE, source.path, imageBlurDp = 12f)
        repeat(20) {
            page.setReaderBackground(Color.WHITE, source.path, it / 20f, 12f)
        }
        assertEquals(1, loader.requests.size)
        val blurred = ColorDrawable(Color.BLUE)
        loader.succeed(loader.requests.single(), blurred)
        assertSame(blurred, image.drawable)
        assertEquals(0.95f, image.alpha, 0.001f)
    }

    @Test
    fun blurChangesKeepThePreviousImageWhileLoadingAndAfterFailure() {
        page.setReaderBackground(Color.WHITE, source.path)
        val original = ColorDrawable(Color.BLUE)
        loader.succeed(loader.requests.single(), original)

        page.setReaderBackground(Color.WHITE, source.path, imageBlurDp = 12f)
        assertSame(original, image.drawable)
        loader.fail(loader.requests.last())
        assertSame(original, image.drawable)

        page.setReaderBackground(Color.WHITE, source.path, imageBlurDp = 12f)
        assertEquals(3, loader.requests.size)
        val blurred = ColorDrawable(Color.GREEN)
        loader.succeed(loader.requests.last(), blurred)
        assertSame(blurred, image.drawable)
        page.setReaderBackground(Color.WHITE, source.path, imageBlurDp = 12f)
        assertEquals(3, loader.requests.size)
    }

    @Test
    fun changingToABakedImageKeepsThePreviousImageUntilSuccess() {
        page.setReaderBackground(Color.WHITE, source.path, imageBlurDp = 12f)
        val original = ColorDrawable(Color.BLUE)
        loader.succeed(loader.requests.single(), original)

        val baked = temporaryFolder.newFile("background-blurred.jpg")
        page.setReaderBackground(Color.WHITE, baked.path)
        assertSame(original, image.drawable)
        val replacement = ColorDrawable(Color.GREEN)
        loader.succeed(loader.requests.last(), replacement)
        assertSame(replacement, image.drawable)
    }

    @Test
    fun unchangedReadViewSettingsRetryAFailedBackgroundWithoutRecreatingThePage() {
        val reader = ReadView(RuntimeEnvironment.getApplication())
        reader.setReaderBackground(Color.WHITE, Color.BLACK, source.path)
        assertEquals(6, loader.requests.size)
        loader.requests.toList().drop(1).forEach { loader.succeed(it, ColorDrawable(Color.BLUE)) }
        // Only one page loses its image request; the other five must keep theirs.
        loader.fail(loader.requests.first())
        reader.setReaderBackground(Color.WHITE, Color.BLACK, source.path)
        assertEquals(7, loader.requests.size)
    }

    @Test
    fun temporarilyMissingFileCanLoadOnTheNextReadViewUpdate() {
        val reader = ReadView(RuntimeEnvironment.getApplication())
        val delayed = File(temporaryFolder.root, "delayed.jpg")
        reader.setReaderBackground(Color.WHITE, Color.BLACK, delayed.path)
        assertEquals(0, loader.requests.size)

        delayed.writeText("available")
        reader.setReaderBackground(Color.WHITE, Color.BLACK, delayed.path)
        assertEquals(6, loader.requests.size)
    }

    @Test
    fun composeRenderConfigRetriesAFailedImageOnAPortraitTablet() {
        val reader = ReadView(RuntimeEnvironment.getApplication())
        reader.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY))
        reader.layout(0, 0, 800, 1200)
        val config = renderConfig(source.path)
        reader.applyRenderConfig(config)
        assertEquals(false, reader.isTwoPageSpreadActive)
        val currentImage = reader.curPageView.getChildAt(0) as ImageView
        val currentRequest = loader.requests.single { it.target is coil.target.ImageViewTarget &&
            (it.target as coil.target.ImageViewTarget).view === currentImage }
        loader.requests.filter { it !== currentRequest }.forEach {
            loader.succeed(it, ColorDrawable(Color.BLUE))
        }
        loader.fail(currentRequest)

        reader.applyRenderConfig(config)
        assertEquals(7, loader.requests.size)
        val replacement = ColorDrawable(Color.GREEN)
        loader.succeed(loader.requests.last(), replacement)
        assertSame(replacement, currentImage.drawable)
        reader.applyRenderConfig(config)
        assertEquals(7, loader.requests.size)
    }

    @Test
    fun composeRenderConfigRetriesAnImageWhenOnlyControlsChange() {
        val reader = ReadView(RuntimeEnvironment.getApplication())
        val config = renderConfig(source.path)
        reader.applyRenderConfig(config)
        loader.requests.toList().drop(1).forEach { loader.succeed(it, ColorDrawable(Color.BLUE)) }
        loader.fail(loader.requests.first())

        reader.applyRenderConfig(config.copy(pageTransitionDurationMs = 300))
        assertEquals(7, loader.requests.size)
    }

    private fun renderConfig(imagePath: String) = ReaderRenderConfig(
        layout = ReaderLayoutConfig(
            fontSizePx = 24f, theme = "day", chapterCount = 0, startChapter = 0, startPage = 0,
            lineHeightMult = 1.5f, letterSpacingDp = 0f, textAlignment = ReaderTextAlignment.NATURAL,
            fontType = "system", customFontPath = null, marginLeftDp = 20f, marginRightDp = 20f,
            marginTopDp = 20f, marginBottomDp = 20f, paragraphSpacingDp = 2f, firstLineIndent = 2f,
            bodyFontWeight = 400, bionicReadingEnabled = false, useDisplayDensityForSpans = false,
            writingMode = ReaderWritingMode.HORIZONTAL, twoPageSpread = true
        ),
        background = ReaderBackgroundConfig(Color.WHITE, Color.BLACK, imagePath, 1f, 0f),
        chineseMode = "original", pageTransition = "slide", pageTransitionDurationMs = 260,
        edgeTapMode = ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT
    )

    @Test
    fun unchangedReadViewSettingsDoNotRestoreTheBackgroundDuringAFade() {
        val reader = ReadView(RuntimeEnvironment.getApplication())
        reader.setReaderBackground(Color.WHITE, Color.BLACK, source.path)
        loader.requests.toList().forEach { loader.succeed(it, ColorDrawable(Color.BLUE)) }
        val currentPage = reader.slotManager.getCurSlot().contentView
        currentPage.stripBackgroundForFade()

        reader.setReaderBackground(Color.WHITE, Color.BLACK, source.path)
        assertEquals(Color.TRANSPARENT, (currentPage.background as ColorDrawable).color)
        assertEquals(6, loader.requests.size)
    }

    @Test
    fun anOlderRequestFinishingDoesNotDisableRetryForANewerMissingFile() {
        page.setReaderBackground(Color.WHITE, source.path)
        val delayed = File(temporaryFolder.root, "delayed.jpg")
        page.setReaderBackground(Color.WHITE, delayed.path)
        loader.succeed(loader.requests.single(), ColorDrawable(Color.BLUE))

        delayed.writeText("available")
        page.setReaderBackground(Color.WHITE, delayed.path)
        assertEquals(2, loader.requests.size)
    }

    @Test
    fun removingThePhotoHidesTheImageEvenAfterALoadFailure() {
        page.setReaderBackground(Color.WHITE, source.path)
        loader.succeed(loader.requests.single(), ColorDrawable(Color.BLUE))
        page.setReaderBackground(Color.WHITE, source.path, imageBlurDp = 12f)
        loader.fail(loader.requests.last())

        page.setReaderBackground(Color.WHITE, null)
        assertEquals(View.GONE, image.visibility)
        assertEquals(null, image.drawable)
    }

    /** Controls request completion while exercising Coil's actual ImageView target. */
    private class PendingImageLoader(private val delegate: ImageLoader) : ImageLoader by delegate {
        val requests = mutableListOf<ImageRequest>()
        private val jobs = mutableListOf<CompletableDeferred<ImageResult>>()

        override fun enqueue(request: ImageRequest): Disposable {
            requests += request
            request.target?.onStart(request.placeholder)
            val pending = CompletableDeferred<ImageResult>()
            jobs += pending
            return object : Disposable {
                override val job = pending
                override val isDisposed get() = !pending.isActive
                override fun dispose() { pending.cancel() }
            }
        }

        fun succeed(request: ImageRequest, drawable: Drawable) {
            request.target?.onSuccess(drawable)
            request.listener?.onSuccess(request, SuccessResult(drawable, request, DataSource.MEMORY))
        }

        fun fail(request: ImageRequest) {
            request.target?.onError(request.error)
            request.listener?.onError(request, ErrorResult(request.error, request, IllegalStateException("Image unavailable")))
        }

        override fun shutdown() {
            jobs.forEach { it.cancel() }
            delegate.shutdown()
        }
    }
}
