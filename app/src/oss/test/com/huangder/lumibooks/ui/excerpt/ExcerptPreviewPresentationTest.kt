package com.huangder.lumibooks.ui.excerpt

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ExcerptPreviewPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun fullscreenKeepsBackPreviewAndExportActions() {
        show(ExcerptPresentation.FULLSCREEN)
        compose.onNodeWithContentDescription(context.getString(R.string.back)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.close)).assertDoesNotExist()
        verifyPreviewAndActions()
    }

    @Test fun readerSheetRetainsItsCloseHeader() {
        show(ExcerptPresentation.SHEET)
        compose.onNodeWithContentDescription(context.getString(R.string.close)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).assertDoesNotExist()
        verifyPreviewAndActions()
    }

    @Test fun fullscreenSmallViewportAtLargeFontReservesVisiblePreview() {
        show(ExcerptPresentation.FULLSCREEN, fontScale = 2f, small = true)
        verifyPreviewAndActions()
        assertTrue(compose.onNodeWithTag("excerpt_preview").fetchSemanticsNode().boundsInRoot.height > 80f)
    }

    private fun show(presentation: ExcerptPresentation, fontScale: Float = 1f, small: Boolean = false) {
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", motionPreference = MotionPreference.REDUCED) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Box(if (small) Modifier.size(320.dp, 480.dp) else Modifier.size(400.dp, 700.dp)) {
                        ExcerptPreviewContent(
                            ExcerptRequest("Book", "Author", "Chapter", "An excerpt.", createdAt = 0),
                            ExcerptSelection(), {}, {}, presentation = presentation,
                            render = { _, _ -> ExcerptDocument(800, android.graphics.Color.WHITE) {} },
                            write = { _, _ -> Uri.parse("content://excerpt/test") }
                        )
                    }
                }
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasContentDescription(context.getString(R.string.excerpt_save)) and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun verifyPreviewAndActions() {
        val preview = compose.onNodeWithTag("excerpt_preview").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val save = compose.onNodeWithContentDescription(context.getString(R.string.excerpt_save))
            .assertIsDisplayed().assertIsEnabled().fetchSemanticsNode().boundsInRoot
        val share = compose.onNodeWithContentDescription(context.getString(R.string.excerpt_share))
            .assertIsDisplayed().assertIsEnabled().fetchSemanticsNode().boundsInRoot
        assertTrue(preview.bottom <= save.top)
        assertTrue(preview.bottom <= share.top)
    }
}
