package com.huangder.lumibooks.util.epub

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that a real tap/click on an <a> inside the transformed BOOK_LAYOUT
 * document posts a "link" message to the native WebMessageListener, and that
 * same-document footnote references still render a popover instead of being
 * silently swallowed.
 */
@RunWith(AndroidJUnit4::class)
class EpubLinkClickInstrumentedTest {
    @SuppressLint("SetJavaScriptEnabled")
    @Test
    fun clickingAnchorPostsLinkMessage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val html = EpubDocumentTransformer.transform(
            EpubResource(
                "OPS/chapter.xhtml",
                "application/xhtml+xml",
                (
                    "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head></head><body>" +
                        "<p>Before</p>" +
                        "<p><a id=\"cross\" href=\"ch2.xhtml#note\">cross chapter</a></p>" +
                        "<p><a id=\"same\" href=\"#section-two\">same chapter anchor</a></p>" +
                        "<p id=\"section-two\">Target section text</p>" +
                        "</body></html>"
                    ).toByteArray()
            ),
            EpubRenditionLayout.REFLOWABLE
        ).toString(Charsets.UTF_8)

        val loaded = CountDownLatch(1)
        val linkHrefs = java.util.Collections.synchronizedList(mutableListOf<String>())
        var firstLinkReady: CountDownLatch? = null
        lateinit var webView: WebView

        instrumentation.runOnMainSync {
            webView = WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        loaded.countDown()
                    }
                }
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    WebViewCompat.addWebMessageListener(
                        this,
                        "lumiNative",
                        setOf("https://" + EpubRenderSession.ASSET_DOMAIN),
                        object : WebViewCompat.WebMessageListener {
                            override fun onPostMessage(
                                sourceView: WebView,
                                message: WebMessageCompat,
                                sourceOrigin: Uri,
                                isMainFrame: Boolean,
                                replyProxy: JavaScriptReplyProxy
                            ) {
                                if (message.data == null) return
                                val root = runCatching {
                                    JSONObject(message.data)
                                }.getOrNull() ?: return
                                if (root.optString("type") == "link") {
                                    root.optJSONObject("payload")?.optString("href")?.let {
                                        linkHrefs.add(it)
                                        firstLinkReady?.countDown()
                                    }
                                }
                            }
                        }
                    )
                }
                loadDataWithBaseURL(
                    "https://" + EpubRenderSession.ASSET_DOMAIN + "/chapter.xhtml",
                    html,
                    "application/xhtml+xml",
                    "utf-8",
                    null
                )
            }
        }
        assertTrue("transformed document must load", loaded.await(10, TimeUnit.SECONDS))

        firstLinkReady = CountDownLatch(1)
        instrumentation.runOnMainSync {
            webView.evaluateJavascript(
                "window.LumiReader.configure({flow:'paginated',theme:'day',nativePaging:true," +
                    "insets:{top:0,right:0,bottom:0,left:0}});" +
                    "(function(){var a=document.getElementById('cross');" +
                    "a.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,view:window}));})()"
            ) {}
        }
        assertTrue("link message must be posted", firstLinkReady!!.await(5, TimeUnit.SECONDS))
        assertEquals(
            "https://" + EpubRenderSession.ASSET_DOMAIN + "/ch2.xhtml#note",
            linkHrefs.firstOrNull()
        )
        // The cross-chapter footnote attempt leaves a closing footnote popover in the DOM
        // for ~220ms; wait it out so the next click is not swallowed by the popover guard.
        Thread.sleep(400)
        val secondLinkReady = CountDownLatch(1)
        firstLinkReady = secondLinkReady
        instrumentation.runOnMainSync {
            webView.evaluateJavascript(
                "(function(){var s=document.getElementById('same');" +
                    "s.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,view:window}));})()"
            ) {}
        }
        assertTrue("same-chapter link message must be posted", secondLinkReady.await(5, TimeUnit.SECONDS))
        assertTrue(
            "same-chapter anchor must post its absolute href",
            linkHrefs.contains("https://" + EpubRenderSession.ASSET_DOMAIN + "/chapter.xhtml#section-two")
        )

        instrumentation.runOnMainSync {
            webView.destroy()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Test
    fun sameDocumentFootnoteShowsPopoverInsteadOfSilentNoop() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val html = EpubDocumentTransformer.transform(
            EpubResource(
                "OPS/chapter.xhtml",
                "application/xhtml+xml",
                (
                    "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head></head><body>" +
                        "<p><sup><a class=\"duokan-footnote\" href=\"#ref_end_1\">1</a></sup></p>" +
                        "<p id=\"ref_end_1\">Footnote body</p>" +
                        "</body></html>"
                    ).toByteArray()
            ),
            EpubRenditionLayout.REFLOWABLE
        ).toString(Charsets.UTF_8)

        val loaded = CountDownLatch(1)
        var footnoteState: JSONObject? = null
        lateinit var webView: WebView

        instrumentation.runOnMainSync {
            webView = WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        loaded.countDown()
                    }
                }
                loadDataWithBaseURL(
                    "https://" + EpubRenderSession.ASSET_DOMAIN + "/chapter.xhtml",
                    html,
                    "application/xhtml+xml",
                    "utf-8",
                    null
                )
            }
        }
        assertTrue("transformed document must load", loaded.await(10, TimeUnit.SECONDS))

        instrumentation.runOnMainSync {
            webView.evaluateJavascript(
                "window.LumiReader.configure({flow:'paginated',theme:'day',nativePaging:true," +
                    "insets:{top:0,right:0,bottom:0,left:0}});" +
                    "(function(){var a=document.querySelector('a[href]');" +
                    "a.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,view:window}));})()"
            ) {}
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        while (footnoteState == null && System.nanoTime() < deadline) {
            val pollReady = CountDownLatch(1)
            instrumentation.runOnMainSync {
                webView.evaluateJavascript(
                    "JSON.stringify({popover:!!document.getElementById('lumi-footnote-popover')," +
                        "content:(document.getElementById('lumi-footnote-content')||{}).textContent||''})"
                ) { encoded ->
                    val decoded = runCatching { JSONArray("[$encoded]").optString(0) }.getOrNull()
                    footnoteState = decoded?.let { runCatching { JSONObject(it) }.getOrNull() }
                    pollReady.countDown()
                }
            }
            assertTrue("footnote state poll must complete", pollReady.await(2, TimeUnit.SECONDS))
            if (footnoteState == null) Thread.sleep(200)
        }
        assertTrue(
            "same-document footnote must show a popover: state=$footnoteState",
            footnoteState?.optBoolean("popover", false) == true
        )
        assertTrue(
            "popover must contain the footnote body: state=$footnoteState",
            footnoteState?.optString("content")?.contains("Footnote body") == true
        )

        instrumentation.runOnMainSync {
            webView.destroy()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Test
    fun goToFragmentReportsMissingTarget() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val html = EpubDocumentTransformer.transform(
            EpubResource(
                "OPS/chapter.xhtml",
                "application/xhtml+xml",
                (
                    "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head></head><body>" +
                        "<p id=\"note-1\">Target note text</p>" +
                        "</body></html>"
                    ).toByteArray()
            ),
            EpubRenditionLayout.REFLOWABLE
        ).toString(Charsets.UTF_8)

        val loaded = CountDownLatch(1)
        val resultReady = CountDownLatch(1)
        var result: JSONObject? = null
        lateinit var webView: WebView

        instrumentation.runOnMainSync {
            webView = WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        loaded.countDown()
                    }
                }
                loadDataWithBaseURL(
                    "https://" + EpubRenderSession.ASSET_DOMAIN + "/chapter.xhtml",
                    html,
                    "application/xhtml+xml",
                    "utf-8",
                    null
                )
            }
        }
        assertTrue("transformed document must load", loaded.await(10, TimeUnit.SECONDS))

        instrumentation.runOnMainSync {
            webView.evaluateJavascript(
                "window.LumiReader.configure({flow:'paginated',theme:'day',nativePaging:true," +
                    "insets:{top:0,right:0,bottom:0,left:0}});" +
                    "JSON.stringify({found:window.LumiReader.goToFragment('note-1')," +
                    "missing:window.LumiReader.goToFragment('does-not-exist')})"
            ) { encoded ->
                val decoded = runCatching { JSONArray("[$encoded]").optString(0) }.getOrNull()
                result = decoded?.let { runCatching { JSONObject(it) }.getOrNull() }
                resultReady.countDown()
            }
        }
        assertTrue("goToFragment result must be reported", resultReady.await(5, TimeUnit.SECONDS))
        assertTrue("existing fragment must resolve: result=$result", result?.optBoolean("found", false) == true)
        assertTrue("missing fragment must report false: result=$result", result?.optBoolean("missing", true) == false)

        instrumentation.runOnMainSync {
            webView.destroy()
        }
    }
}
