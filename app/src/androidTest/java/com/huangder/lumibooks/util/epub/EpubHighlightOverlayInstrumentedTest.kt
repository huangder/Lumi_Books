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
class EpubHighlightOverlayInstrumentedTest {
    @SuppressLint("SetJavaScriptEnabled")
    @Test
    fun roundedOverlaySurvivesReaderReconfiguration() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val html = EpubDocumentTransformer.transform(
            EpubResource(
                "OPS/chapter.xhtml",
                "application/xhtml+xml",
                ("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head></head><body>" +
                    "<p style=\"width:120px;font-size:24px;line-height:1\">" +
                    "before repeated highlight text across several wrapped lines after</p>" +
                    "<p>another line for pagination</p></body></html>").toByteArray()
            ),
            EpubRenditionLayout.REFLOWABLE
        ).toString(Charsets.UTF_8)
        val loaded = CountDownLatch(1)
        val resultReady = CountDownLatch(1)
        val expiredResultReady = CountDownLatch(1)
        var result: JSONObject? = null
        var expiredResult: JSONObject? = null
        var encodedResult: String? = null
        lateinit var webView: WebView
        EpubHighlightTestActivity.current = null
        instrumentation.targetContext.startActivity(
            Intent().setClassName(
                instrumentation.targetContext.packageName,
                EpubHighlightTestActivity::class.java.name
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        val activityDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var activity: EpubHighlightTestActivity? = null
        while (activity == null && System.nanoTime() < activityDeadline) {
            activity = EpubHighlightTestActivity.current
            if (activity == null) Thread.sleep(25)
        }
        val hostActivity = requireNotNull(activity) { "debug WebView host activity did not start" }

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

        instrumentation.runOnMainSync {
            webView.evaluateJavascript(
                "window.LumiReader.configure({flow:'paginated',theme:'day',insets:{top:0,right:0,bottom:0,left:0}});" +
                    "window.LumiReader.setHighlights([{exact:'repeated highlight text across several wrapped lines'," +
                    "color:'#ffff5f64',start:{version:2,exact:'repeated highlight text across several wrapped lines'," +
                    "prefix:'before ',suffix:' after',progression:0.2}}]);" +
                    "window.LumiReader.findText({version:2,exact:'repeated highlight text across several wrapped lines'," +
                    "prefix:'before ',suffix:' after',progression:0.2},101);" +
                    "window.LumiReader.findText({version:2,exact:'another line for pagination'," +
                    "prefix:'',suffix:'',progression:0.8},102);"
            ) {
                webView.postDelayed({
                    webView.evaluateJavascript(
                        "window.LumiReader.configure({flow:'paginated',theme:'sepia',insets:{top:0,right:0,bottom:0,left:0}});" +
                            "(function(){var p=document.querySelector('p'),r=document.createRange();" +
                            "if(p&&p.firstChild)r.selectNodeContents(p.firstChild);" +
                            "var blocks=Array.from(document.querySelectorAll('.lumi-highlight-block'))," +
                            "search=blocks.filter(function(e){return e.style.backgroundColor.indexOf('255, 193, 7')>=0})," +
                            "target=document.querySelectorAll('p')[1],layer=document.getElementById('lumi-highlight-layer')," +
                            "targetRect=target?target.getBoundingClientRect():null,layerRect=layer?layer.getBoundingClientRect():null;" +
                            "return JSON.stringify({count:document.querySelectorAll('.lumi-highlight-block').length," +
                            "searchCount:search.length,searchTop:search.length?parseFloat(search[0].style.top):-1," +
                            "searchAnimation:search.length?getComputedStyle(search[0]).animationName:''," +
                            "targetTop:targetRect&&layerRect?targetRect.top-layerRect.top:-1," +
                            "radius:document.querySelector('.lumi-highlight-block')?" +
                            "getComputedStyle(document.querySelector('.lumi-highlight-block')).borderTopLeftRadius:''," +
                            "gaps:(function(){var b=Array.from(document.querySelectorAll('.lumi-highlight-block'))" +
                            ".map(function(e){return {t:parseFloat(e.style.top),b:parseFloat(e.style.top)+" +
                            "parseFloat(e.style.height)}}).sort(function(a,b){return a.t-b.t}),g=[];" +
                            "for(var i=1;i<b.length;i++)g.push(b[i].t-b[i-1].b);return g})()," +
                            "layer:!!document.getElementById('lumi-highlight-layer'),body:document.body.innerText," +
                            "width:window.innerWidth,height:window.innerHeight,rangeRects:r.getClientRects().length," +
                            "bodyVisibility:getComputedStyle(document.body).visibility," +
                            "paragraphWidth:p?p.getBoundingClientRect().width:-1});})()"
                    ) { encoded ->
                        encodedResult = encoded
                        val decoded = runCatching { JSONArray("[$encoded]").optString(0) }.getOrNull()
                        result = decoded?.let { runCatching { JSONObject(it) }.getOrNull() }
                        resultReady.countDown()
                        webView.postDelayed({
                            webView.evaluateJavascript(
                                "JSON.stringify({searchCount:document.querySelectorAll('.lumi-search-highlight-block').length," +
                                    "persistentCount:document.querySelectorAll('.lumi-highlight-block:not(.lumi-search-highlight-block)').length})"
                            ) { expiredEncoded ->
                                val expiredDecoded = runCatching {
                                    JSONArray("[$expiredEncoded]").optString(0)
                                }.getOrNull()
                                expiredResult = expiredDecoded?.let {
                                    runCatching { JSONObject(it) }.getOrNull()
                                }
                                expiredResultReady.countDown()
                            }
                        }, 1800L)
                    }
                }, 500L)
                }
        }

        assertTrue("highlight geometry query must complete", resultReady.await(10, TimeUnit.SECONDS))
        assertTrue(
            "rounded overlay must be rebuilt after configuration: result=$result encoded=$encodedResult",
            result?.optInt("count", 0) ?: 0 > 0
        )
        assertEquals("6px", result?.optString("radius"))
        assertTrue("the second locator search must replace the first temporary highlight: result=$result", result?.optInt("searchCount", 0) ?: 0 > 0)
        assertEquals("lumi-search-highlight-pulse", result?.optString("searchAnimation"))
        assertTrue(
            "temporary highlight must resolve to the second paragraph: result=$result",
            kotlin.math.abs(
                (result?.optDouble("searchTop", -1000.0) ?: -1000.0) -
                    (result?.optDouble("targetTop", 1000.0) ?: 1000.0)
            ) < 3.0
        )
        assertTrue(
            "persistent note highlights must survive search and reconfiguration: result=$result",
            (result?.optInt("count", 0) ?: 0) > (result?.optInt("searchCount", 0) ?: 0)
        )
        assertTrue(
            "temporary search highlight must expire after two pulses: result=$expiredResult",
            expiredResultReady.await(5, TimeUnit.SECONDS)
        )
        assertEquals(0, expiredResult?.optInt("searchCount", -1))
        assertTrue(
            "persistent note highlights must remain after the search pulse expires: result=$expiredResult",
            expiredResult?.optInt("persistentCount", 0) ?: 0 > 0
        )
        val gaps = result?.optJSONArray("gaps") ?: JSONArray()
        assertTrue("test content must wrap into multiple highlight rows: result=$result", gaps.length() > 0)
        for (index in 0 until gaps.length()) {
            assertTrue(
                "adjacent highlight rows must retain a 3 CSS px gap: result=$result",
                gaps.optDouble(index, Double.NEGATIVE_INFINITY) >= 2.99
            )
        }
        instrumentation.runOnMainSync {
            webView.destroy()
            hostActivity.finish()
        }
    }
}
