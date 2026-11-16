package com.huangder.lumibooks.ui.settings

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.EARLY_DONOR_NOTICE_ID
import com.huangder.lumibooks.domain.model.ReceivedNotice
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h760dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoticeHistoryScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyHistoryAndBundledNoticeCanBeOpenedAndClosed() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val title = context.getString(R.string.early_donor_notice_title)
        val message = context.getString(R.string.early_donor_notice_message)
        val notices = mutableStateOf(emptyList<ReceivedNotice>())
        val bundledImageRequested = AtomicBoolean(false)
        val loader = ImageLoader.Builder(context).components {
            add { chain ->
                if (chain.request.data == R.drawable.early_donor_claim_code) bundledImageRequested.set(true)
                chain.proceed(chain.request)
            }
        }.build()
        Coil.setImageLoader(loader)
        try {
            compose.setContent {
                EBookReaderTheme(appTheme = "lumi", motionPreference = MotionPreference.REDUCED) {
                    NoticeHistoryScreen(notices.value, onBack = {})
                }
            }

            compose.onNodeWithText(context.getString(R.string.notice_history_empty)).assertIsDisplayed()
            compose.runOnIdle {
                notices.value = listOf(ReceivedNotice(EARLY_DONOR_NOTICE_ID, title, message, 1L))
            }
            compose.onNodeWithText(title).performClick()
            compose.onNodeWithText(context.getString(R.string.close)).assertIsDisplayed()
            compose.waitUntil(5_000) { bundledImageRequested.get() }
            compose.onNodeWithText(context.getString(R.string.close)).performClick()
            compose.onNodeWithText(context.getString(R.string.close)).assertDoesNotExist()
            compose.onNodeWithTag("settings-toolbar").assertIsDisplayed()
            compose.onNodeWithText(title).assertIsDisplayed()
        } finally {
            Coil.reset()
            loader.shutdown()
        }
    }
}
