package com.huangder.lumibooks.util.epub

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubPageTextInstrumentedTest {
    @SuppressLint("SetJavaScriptEnabled")
    @Test
    fun longParagraphHasExactNonOverlappingPageTextInEveryReadingAxis() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val paragraph = ("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".repeat(180)) + " END"
        val html = EpubDocumentTransformer.transform(
            EpubResource(
                "OPS/chapter.xhtml",
                "application/xhtml+xml",
                ("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head></head>" +
                    "<body><p style=\"font-size:24px;line-height:1.4;margin:0\">" +
                    paragraph + "</p></body></html>").toByteArray()
            ),
            EpubRenditionLayout.REFLOWABLE
        ).toString(Charsets.UTF_8)

        EpubHighlightTestActivity.current = null
        instrumentation.targetContext.startActivity(
            Intent().setClassName(
                instrumentation.targetContext.packageName,
                EpubHighlightTestActivity::class.java.name
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        val activityDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var activity: EpubHighlightTestActivity? = null
        while (activity == null && System.nanoTime() < activityDeadline) {
            activity = EpubHighlightTestActivity.current
            if (activity == null) Thread.sleep(25L)
        }
        val hostActivity = requireNotNull(activity) { "debug WebView host activity did not start" }
        val loaded = CountDownLatch(1)
        lateinit var webView: WebView
        instrumentation.runOnMainSync {
            webView = WebView(hostActivity).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        loaded.countDown()
                    }
                }
                loadDataWithBaseURL(
                    "https://reader.invalid/chapter.xhtml",
                    html,
                    "application/xhtml+xml",
                    "utf-8",
                    null
                )
            }
            hostActivity.setContentView(webView)
        }
        assertTrue("transformed EPUB document must load", loaded.await(10, TimeUnit.SECONDS))

        val modes = listOf(
            Triple("horizontal", "horizontal-tb", "ltr"),
            Triple("vertical", "vertical-rl", "ltr"),
            Triple("rtl", "horizontal-tb", "rtl")
        )
        modes.forEach { (name, writingMode, direction) ->
            val ready = CountDownLatch(1)
            var result: JSONObject? = null
            instrumentation.runOnMainSync {
                webView.evaluateJavascript(
                    "document.body.style.writingMode='$writingMode';" +
                        "document.body.style.direction='$direction';" +
                        "window.LumiReader.configure({flow:'paginated',progression:'$direction'," +
                        "theme:'day',insets:{top:0,right:0,bottom:0,left:0}});"
                ) {
                    webView.postDelayed({
                        webView.evaluateJavascript(
                            "(function(){var first=window.LumiReader.pageText(0),pages=[];" +
                                "for(var i=0;i<first.pageCount;i++)pages.push(window.LumiReader.pageText(i));" +
                                "return JSON.stringify({chapterText:first.chapterText,pages:pages});})()"
                        ) { encoded ->
                            val decoded = runCatching { JSONArray("[$encoded]").optString(0) }
                                .getOrNull()
                            result = decoded?.let { runCatching { JSONObject(it) }.getOrNull() }
                            ready.countDown()
                        }
                    }, 300L)
                }
            }
            assertTrue("$name page text query must complete", ready.await(10, TimeUnit.SECONDS))
            val payload = requireNotNull(result) { "$name page text payload was invalid" }
            val pages = payload.getJSONArray("pages")
            assertTrue("$name fixture must span multiple pages", pages.length() > 1)
            val merged = StringBuilder()
            var expectedStart = 0
            for (index in 0 until pages.length()) {
                val page = pages.getJSONObject(index)
                assertEquals("$name page $index must start after its predecessor", expectedStart, page.getInt("startCharacterOffset"))
                val end = page.getInt("endCharacterOffset")
                assertTrue("$name page $index must have a valid range", end >= expectedStart)
                merged.append(page.getString("text"))
                expectedStart = end
            }
            assertEquals(payload.getString("chapterText").length, expectedStart)
            assertEquals(payload.getString("chapterText"), merged.toString())
            assertEquals(paragraph, merged.toString())
        }

        instrumentation.runOnMainSync {
            webView.destroy()
            hostActivity.finish()
        }
    }
}
