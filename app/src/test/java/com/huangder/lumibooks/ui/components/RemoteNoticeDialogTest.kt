package com.huangder.lumibooks.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.ErrorResult
import coil.request.SuccessResult
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h760dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemoteNoticeDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `photo adds scrollable content and a failed image leaves the text layout intact`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val photoUrl = "https://example.com/photo.jpg"
        val imageUrl = mutableStateOf<String?>(null)
        val imageResId = mutableStateOf<Int?>(null)
        val failed = AtomicBoolean(false)
        var acknowledged = false
        val photo = BitmapDrawable(context.resources, Bitmap.createBitmap(240, 150, Bitmap.Config.ARGB_8888))
        val loader = ImageLoader.Builder(context).components {
            add { chain ->
                when (chain.request.data) {
                    photoUrl -> SuccessResult(photo, chain.request, DataSource.MEMORY)
                    R.drawable.early_donor_claim_code -> chain.proceed(chain.request)
                    else -> {
                        failed.set(true)
                        ErrorResult(null, chain.request, IOException("Image unavailable"))
                    }
                }
            }
        }.build()
        Coil.setImageLoader(loader)

        try {
            val longMessage = List(40) { "通知正文第 ${it + 1} 行" }.joinToString("\n")
            val message = mutableStateOf(longMessage)
            compose.setContent {
                EBookReaderTheme {
                    CompositionLocalProvider(LocalAppTheme provides "ios") {
                        RemoteNoticeDialog(
                            title = "通知",
                            message = message.value,
                            imageUrl = imageUrl.value,
                            imageResId = imageResId.value,
                            onConfirm = { acknowledged = true }
                        )
                    }
                }
            }

            fun scrollExtent(): Float = compose.onNode(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
            ).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()

            val textExtent = scrollExtent()
            assertTrue(textExtent > 0f)
            compose.runOnIdle { imageUrl.value = photoUrl }
            compose.waitUntil(timeoutMillis = 5_000) { scrollExtent() > textExtent }

            compose.runOnIdle { imageUrl.value = "https://example.com/missing.jpg" }
            compose.waitUntil(timeoutMillis = 5_000) {
                scrollExtent() == textExtent && failed.get()
            }
            assertEquals(textExtent, scrollExtent(), 0.5f)
            compose.onNodeWithText(longMessage).assertIsDisplayed()

            // Keep the failed URL to verify the bundled image takes precedence offline.
            compose.runOnIdle { imageResId.value = R.drawable.early_donor_claim_code }
            compose.waitUntil(timeoutMillis = 5_000) { scrollExtent() > textExtent }

            val donorMessage = context.getString(R.string.early_donor_notice_message)
            compose.runOnIdle { message.value = donorMessage }
            compose.onNodeWithText(donorMessage).assertIsDisplayed()
            assertEquals(0f, scrollExtent(), 0.5f)

            compose.onNodeWithText(context.getString(R.string.remote_notice_acknowledge)).performClick()
            compose.runOnIdle { assertTrue(acknowledged) }
        } finally {
            Coil.reset()
            loader.shutdown()
        }
    }
}
