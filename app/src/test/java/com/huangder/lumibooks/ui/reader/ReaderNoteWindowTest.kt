package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.app.Application
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.huangder.lumibooks.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderNoteWindowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun originalLayoutNoteUsesItsOwnWindowAcrossCancelConfirmAndReopen() {
        val visible = mutableStateOf(false)
        lateinit var activity: Activity
        lateinit var readerView: View
        var confirms = 0
        compose.setContent {
            val view = LocalView.current
            activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>().first()
            Box(Modifier.fillMaxSize()) {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                    View(context).also { readerView = it }
                })
                Text("Current reading page")
                NoteInputSheet(
                    visible = visible.value,
                    keepReaderViewport = true,
                    initialText = "",
                    onTextChange = {},
                    onConfirm = { confirms++ },
                    onDismiss = { visible.value = false }
                )
            }
        }
        var originalMode = 0
        var readerSize = 0 to 0
        compose.runOnIdle {
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            originalMode = activity.window.attributes.softInputMode
            readerSize = readerView.width to readerView.height
            assertTrue(readerSize.first > 0 && readerSize.second > 0)
        }
        repeat(2) { iteration ->
            compose.runOnIdle { visible.value = true }
            compose.waitForIdle()
            val dialog = ShadowDialog.getLatestDialog()
            compose.runOnIdle {
                assertTrue(dialog.isShowing)
                assertNotSame(activity.window, dialog.window)
                assertEquals(originalMode, activity.window.attributes.softInputMode)
                assertEquals(0, dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                // Simulate the available height shrinking in the IME's target window.
                dialog.window!!.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, readerSize.second / 2)
            }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(readerSize, readerView.width to readerView.height)
            }
            val action = if (iteration == 0) R.string.cancel else R.string.confirm
            compose.onNodeWithContentDescription(activity.getString(action)).performClick()
            compose.waitForIdle()
            compose.runOnIdle {
                assertFalse(dialog.isShowing)
                assertFalse(visible.value)
                assertEquals(originalMode, activity.window.attributes.softInputMode)
                assertEquals(readerSize, readerView.width to readerView.height)
            }
            compose.onNodeWithText("Current reading page").assertIsDisplayed()
        }
        assertEquals(1, confirms)
    }
}
