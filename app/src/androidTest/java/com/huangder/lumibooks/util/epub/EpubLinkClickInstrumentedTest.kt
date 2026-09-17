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
    fun mismatchedFootnoteBodiesLeaveTheFlowAndStayReadableInThePopover() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // 《一生之敌》序章真实结构：第 3 条注释正文没有 id，且第 3 个引用重复指向第 1 条。
        val html = EpubDocumentTransformer.transform(
            EpubResource(
                "OPS/chapter.xhtml",
                "application/xhtml+xml",
                (
                    "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">" +
                        "<head></head><body>" +
                        "<p>正文一<a epub:type=\"noteref\" id=\"r1\" href=\"#footnote-1\"> <img src=\"n.png\"/></a>继续</p>" +
                        "<aside epub:type=\"footnote\" id=\"footnote-1\">第一条注释正文。</aside>" +
                        "<aside epub:type=\"footnote\" id=\"footnote-2\">第二条注释正文。</aside>" +
                        "<aside epub:type=\"footnote\">第三条注释正文。</aside>" +
                        "<p>正文二<a epub:type=\"noteref\" id=\"r2\" href=\"#footnote-2\"> <img src=\"n.png\"/></a>继续</p>" +
                        "<p>正文三<a epub:type=\"noteref\" id=\"r3\" href=\"#footnote-1\"> <img src=\"n.png\"/></a>继续</p>" +
                        "</body></html>"
                    ).toByteArray()
            ),
            EpubRenditionLayout.REFLOWABLE
        ).toString(Charsets.UTF_8)

        val loaded = CountDownLatch(1)
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
                    "insets:{top:0,right:0,bottom:0,left:0}});"
            ) {}
        }
        val flowState = pollJson(instrumentation, webView) {
            "(function(){var asides=document.querySelectorAll('aside');var refs=[];" +
                "var all=document.querySelectorAll('a[href]');" +
                "for(var i=0;i<all.length;i++){if(all[i].getAttribute('epub:type')==='noteref')refs.push(all[i]);}" +
                "return JSON.stringify({hidden:document.querySelectorAll('[data-lumi-footnote-body=\"true\"]').length," +
                "bodies:asides.length,thirdId:asides.length>2?asides[2].id:''," +
                "thirdHref:refs.length>2?refs[2].getAttribute('href'):''," +
                "thirdDisplay:asides.length>2?getComputedStyle(asides[2]).display:''});})()"
        }
        assertEquals("注释正文必须全部移出正文流", 3, flowState?.optInt("hidden") ?: -1)
        assertEquals("缺 id 的注释正文应补上合成 id", "lumi-footnote-auto-3", flowState?.optString("thirdId"))
        assertTrue(
            "第 3 个引用应改写到配对的注释正文",
            flowState?.optString("thirdHref")?.endsWith("#lumi-footnote-auto-3") == true
        )
        assertEquals("注释正文不应再占排版空间", "none", flowState?.optString("thirdDisplay"))

        instrumentation.runOnMainSync {
            webView.evaluateJavascript(
                "(function(){var a=document.getElementById('r3');" +
                    "a.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,view:window}));})()"
            ) {}
        }
        val popoverState = pollJson(instrumentation, webView) {
            "(function(){var p=document.getElementById('lumi-footnote-popover');" +
                "return JSON.stringify({popover:!!p," +
                "content:(document.getElementById('lumi-footnote-content')||{}).textContent||''});})()"
        }
        assertTrue("改写的引用也要能弹出气泡", popoverState?.optBoolean("popover") == true)
        assertTrue(
            "气泡里必须是对应的第 3 条注释",
            popoverState?.optString("content")?.contains("第三条注释正文") == true
        )

        instrumentation.runOnMainSync {
            webView.destroy()
        }
    }

    private fun pollJson(
        instrumentation: android.app.Instrumentation,
        webView: WebView,
        timeoutSeconds: Long = 8,
        script: () -> String
    ): JSONObject? {
        var state: JSONObject? = null
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (state == null && System.nanoTime() < deadline) {
            val ready = CountDownLatch(1)
            instrumentation.runOnMainSync {
                webView.evaluateJavascript(script()) { encoded ->
                    val decoded = runCatching { JSONArray("[$encoded]").optString(0) }.getOrNull()
                    state = decoded?.let { runCatching { JSONObject(it) }.getOrNull() }
                    ready.countDown()
                }
            }
            assertTrue("evaluateJavascript must complete", ready.await(2, TimeUnit.SECONDS))
            if (state == null) Thread.sleep(200)
        }
        return state
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Test
    fun bracketedMarkerLinkShowsPopover() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val html = EpubDocumentTransformer.transform(
            EpubResource(
                "OPS/chapter.xhtml",
                "application/xhtml+xml",
                (
                    "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head></head><body>" +
                        "<p>正文内容<a href=\"#note-01\">[01]</a>继续正文。</p>" +
                        "<p id=\"note-01\">注：这是一条正文注释。</p>" +
                        "</body></html>"
                    ).toByteArray()
            ),
            EpubRenditionLayout.REFLOWABLE
        ).toString(Charsets.UTF_8)

        val loaded = CountDownLatch(1)
        val linkPosted = CountDownLatch(1)
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
                                val root = runCatching { JSONObject(message.data ?: return) }.getOrNull() ?: return
                                if (root.optString("type") == "link") linkPosted.countDown()
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
            "bracketed marker link must show a popover: state=$footnoteState",
            footnoteState?.optBoolean("popover", false) == true
        )
        assertTrue(
            "popover must contain the note body: state=$footnoteState",
            footnoteState?.optString("content")?.contains("这是一条正文注释") == true
        )
        assertEquals(
            "popover must not fall back to a link jump",
            1,
            linkPosted.count
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
