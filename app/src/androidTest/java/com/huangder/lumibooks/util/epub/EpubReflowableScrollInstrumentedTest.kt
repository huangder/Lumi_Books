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
class EpubReflowableScrollInstrumentedTest {
    @SuppressLint("SetJavaScriptEnabled")
    @Test
    fun shortPublisherPageUsesFullViewportAndFitsDecoratedContent() {
        val webView = openPage(
            """
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><meta name="viewport" content="width=320,initial-scale=1"/></head>
                  <body style="margin:0;padding:0;background-image:linear-gradient(#234,#567)">
                    <div id="intro" style="width:300px;margin:1em auto;border:60px solid transparent;border-image:linear-gradient(#fff,#ccc) 60 stretch">
                      <p>Introduction</p>
                    </div>
                  </body>
                </html>
            """.trimIndent()
        )
        val result = configureAndMeasure(webView)

        assertEquals(28.0, result.getDouble("paddingTop"), 1.0)
        assertEquals(32.0, result.getDouble("paddingBottom"), 1.0)
        assertEquals(0.0, result.getDouble("backgroundLeft"), 1.0)
        assertEquals(0.0, result.getDouble("backgroundTop"), 1.0)
        assertEquals(result.getDouble("viewportWidth"), result.getDouble("backgroundWidth"), 1.0)
        assertTrue(result.getDouble("backgroundHeight") >= result.getDouble("viewportHeight") - 1.0)
        assertEquals(1, result.getInt("pageCount"))
        assertEquals("hidden", result.getString("overflowY"))
        assertTrue(result.getBoolean("fitted"))
        assertTrue(result.getDouble("introLeft") >= 23.0)
        assertTrue(result.getDouble("introRight") <= result.getDouble("viewportWidth") - 27.0)
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Test
    fun longChapterKeepsItsRealScrollRange() {
        val webView = openPage(
            """
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><meta name="viewport" content="width=320,initial-scale=1"/></head>
                  <body style="margin:0;padding:0"><div style="height:2400px">Long chapter</div></body>
                </html>
            """.trimIndent()
        )
        val result = configureAndEvaluate(
            webView,
            """
                (function(){
                  var root=document.scrollingElement||document.documentElement;
                  var position=window.LumiReader.currentPosition();
                  return JSON.stringify({
                    maxScroll:Math.max(0,root.scrollHeight-root.clientHeight),
                    viewportHeight:root.clientHeight,
                    pageCount:position.pageCount,
                    overflowY:getComputedStyle(document.documentElement).overflowY
                  });
                })()
            """.trimIndent()
        )

        assertTrue(result.getDouble("maxScroll") > result.getDouble("viewportHeight"))
        assertTrue(result.getInt("pageCount") > 1)
        assertEquals("auto", result.getString("overflowY"))
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Test
    fun shortChapterUpwardSwipeRequestsNextChapter() {
        val webView = openPage(
            """
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><meta name="viewport" content="width=320,initial-scale=1"/></head>
                  <body style="margin:0;padding:0"><p>Short chapter</p></body>
                </html>
            """.trimIndent()
        )
        val result = configureAndEvaluate(
            webView,
            """
                (function(){
                  var messages=[];
                  window.lumiNative={postMessage:function(value){messages.push(JSON.parse(value));}};
                  function touch(y){return new Touch({identifier:1,target:document.body,clientX:160,clientY:y});}
                  function send(type,y,active){
                    var item=touch(y);
                    document.body.dispatchEvent(new TouchEvent(type,{
                      bubbles:true,cancelable:true,
                      touches:active?[item]:[],targetTouches:active?[item]:[],changedTouches:[item]
                    }));
                  }
                  send('touchstart',700,true);
                  send('touchmove',500,true);
                  send('touchend',450,false);
                  var turn=messages.filter(function(message){return message.type==='chapterTurn';})[0]||null;
                  return JSON.stringify({supported:typeof Touch==='function',turn:turn});
                })()
            """.trimIndent()
        )

        assertTrue(result.getBoolean("supported"))
        assertEquals(1, result.getJSONObject("turn").getJSONObject("payload").getInt("direction"))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openPage(source: String): WebView {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val html = EpubDocumentTransformer.transform(
            EpubResource("OPS/intro.xhtml", "application/xhtml+xml", source.toByteArray()),
            EpubRenditionLayout.REFLOWABLE
        ).toString(Charsets.UTF_8)

        EpubHighlightTestActivity.current = null
        instrumentation.targetContext.startActivity(
            Intent().setClassName(
                instrumentation.targetContext.packageName,
                EpubHighlightTestActivity::class.java.name
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var activity: EpubHighlightTestActivity? = null
        while (activity == null && System.nanoTime() < deadline) {
            activity = EpubHighlightTestActivity.current
            if (activity == null) Thread.sleep(25L)
        }
        val host = requireNotNull(activity) { "debug WebView host activity did not start" }
        val loaded = CountDownLatch(1)
        lateinit var webView: WebView
        instrumentation.runOnMainSync {
            webView = WebView(host).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        loaded.countDown()
                    }
                }
                loadDataWithBaseURL(
                    "https://reader.invalid/intro.xhtml",
                    html,
                    "application/xhtml+xml",
                    "utf-8",
                    null
                )
            }
            host.setContentView(webView)
        }
        assertTrue("transformed reflowable page must load", loaded.await(10, TimeUnit.SECONDS))
        return webView
    }

    private fun configureAndMeasure(webView: WebView): JSONObject {
        return configureAndEvaluate(
            webView,
            """
                (function(){
                  var root=document.scrollingElement||document.documentElement;
                  var vv=window.visualViewport;var intro=document.getElementById('intro');
                  var ir=intro.getBoundingClientRect();
                  var bg=document.getElementById('lumi-publisher-background').getBoundingClientRect();
                  var bodyStyle=getComputedStyle(document.body);
                  return JSON.stringify({
                    viewportWidth:vv?vv.width:innerWidth,viewportHeight:vv?vv.height:innerHeight,
                    paddingTop:parseFloat(bodyStyle.paddingTop),paddingBottom:parseFloat(bodyStyle.paddingBottom),
                    backgroundLeft:bg.left,backgroundTop:bg.top,backgroundWidth:bg.width,backgroundHeight:bg.height,
                    pageCount:window.LumiReader.currentPosition().pageCount,
                    overflowY:getComputedStyle(document.documentElement).overflowY,
                    fitted:intro.getAttribute('data-lumi-overflow-fit')==='true',
                    introLeft:ir.left,introRight:ir.right
                  });
                })()
            """.trimIndent()
        )
    }

    private fun configureAndEvaluate(webView: WebView, expression: String): JSONObject {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val completed = CountDownLatch(1)
        var result: JSONObject? = null
        instrumentation.runOnMainSync {
            webView.evaluateJavascript(
                "window.LumiReader.configure({flow:'scrolled',theme:'day',preservePublisherBackground:true," +
                    "insets:{top:28,right:27,bottom:32,left:23}});"
            ) {
                webView.postDelayed({
                    webView.evaluateJavascript(expression) { encoded ->
                        val decoded = runCatching { JSONArray("[$encoded]").optString(0) }.getOrNull()
                        result = decoded?.let { runCatching { JSONObject(it) }.getOrNull() }
                        completed.countDown()
                    }
                }, 350L)
            }
        }
        assertTrue("reflowable evaluation must complete", completed.await(10, TimeUnit.SECONDS))
        return requireNotNull(result) { "reflowable measurement payload was invalid" }
    }
}
