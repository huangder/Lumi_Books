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

/**
 * 固定排版（pre-paginated）书籍的页边距规则：
 * 整页只有图片的页面（封面、插图页）必须满屏显示，不能被页边距缩进去一圈；
 * 含正文的页面仍然先塞进“视口减去 insets”的盒子再按 insets 偏移。
 */
@RunWith(AndroidJUnit4::class)
class EpubFixedLayoutInsetsInstrumentedTest {
    @SuppressLint("SetJavaScriptEnabled")
    @Test
    fun fixedLayoutTextPageIsScaledIntoInsetBox() {
        val webView = openFixedLayoutPage(
            body = "<p>正文</p><div id=\"page\" style=\"width:1200px;height:1600px;background:#2f855a\"></div>"
        )
        val insetRect = measure(webView, left = 40, right = 40, top = 40, bottom = 40)
        val fullRect = measure(webView, left = 0, right = 0, top = 0, bottom = 0)

        val viewportWidth = insetRect.getDouble("viewportWidth")
        val viewportHeight = insetRect.getDouble("viewportHeight")
        val availableWidth = viewportWidth - 80.0
        val availableHeight = viewportHeight - 80.0
        val scale = minOf(availableWidth / 1200.0, availableHeight / 1600.0)

        assertEquals(
            "inset box mismatch, measured=$insetRect",
            availableWidth,
            insetRect.getDouble("width"),
            3.0
        )
        assertEquals(40.0, insetRect.getDouble("left"), 3.0)
        assertEquals(1600.0 * scale, insetRect.getDouble("height"), 3.0)
        assertEquals(40.0 + (availableHeight - 1600.0 * scale) / 2.0, insetRect.getDouble("top"), 3.0)

        // 去掉边距后页面必须铺得更满，证明边距真的参与了固定排版的缩放。
        val fullViewportWidth = fullRect.getDouble("viewportWidth")
        val fullViewportHeight = fullRect.getDouble("viewportHeight")
        val fullScale = minOf(fullViewportWidth / 1200.0, fullViewportHeight / 1600.0)
        assertEquals(0.0, fullRect.getDouble("left"), 3.0)
        assertEquals(
            (fullViewportHeight - 1600.0 * fullScale) / 2.0,
            fullRect.getDouble("top"),
            3.0
        )
        assertEquals(
            "full box mismatch, measured=$fullRect",
            fullViewportWidth,
            fullRect.getDouble("width"),
            3.0
        )
        assertTrue(fullRect.getDouble("width") > insetRect.getDouble("width"))
    }

    /** 整页只有图片的固定版式页面：页边距不参与，整页按完整视口居中。 */
    @SuppressLint("SetJavaScriptEnabled")
    @Test
    fun fixedLayoutImagePageIgnoresMargins() {
        val webView = openFixedLayoutPage(
            body = "<div id=\"page\" style=\"width:1200px;height:1600px;background:#2f855a\">" +
                "<img src=\"page.jpg\" style=\"width:1200px;height:1600px\"/></div>"
        )
        val insetRect = measure(webView, left = 40, right = 40, top = 40, bottom = 40)
        val fullRect = measure(webView, left = 0, right = 0, top = 0, bottom = 0)

        val viewportWidth = insetRect.getDouble("viewportWidth")
        val viewportHeight = insetRect.getDouble("viewportHeight")
        val scale = minOf(viewportWidth / 1200.0, viewportHeight / 1600.0)

        // 加了页边距也必须和没有页边距一样：满视口居中，而不是缩进 40px。
        assertEquals(0.0, insetRect.getDouble("left") - fullRect.getDouble("left"), 0.5)
        assertEquals(0.0, insetRect.getDouble("top") - fullRect.getDouble("top"), 0.5)
        assertEquals(1200.0 * scale, insetRect.getDouble("width"), 3.0)
        assertEquals((viewportWidth - 1200.0 * scale) / 2.0, insetRect.getDouble("left"), 3.0)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openFixedLayoutPage(body: String): WebView {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val html = EpubDocumentTransformer.transform(
            EpubResource(
                "OPS/fixed.xhtml",
                "application/xhtml+xml",
                (
                    "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>" +
                        // 去掉浏览器默认的 body 8px margin，便于精确断言“视口 − insets”的盒子。
                        "</head><body style=\"margin:0;padding:0\">" + body + "</body></html>"
                    ).toByteArray()
            ),
            EpubRenditionLayout.PRE_PAGINATED
        ).toString(Charsets.UTF_8)

        EpubHighlightTestActivity.current = null
        instrumentation.targetContext.startActivity(
            Intent().setClassName(
                instrumentation.targetContext.packageName,
                EpubHighlightTestActivity::class.java.name
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        val activityDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var hostActivity: EpubHighlightTestActivity? = null
        while (hostActivity == null && System.nanoTime() < activityDeadline) {
            hostActivity = EpubHighlightTestActivity.current
            if (hostActivity == null) Thread.sleep(25L)
        }
        val host = requireNotNull(hostActivity) { "debug WebView host activity did not start" }

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
                    "https://reader.invalid/fixed.xhtml",
                    html,
                    "application/xhtml+xml",
                    "utf-8",
                    null
                )
            }
            host.setContentView(webView)
        }
        assertTrue("transformed fixed layout must load", loaded.await(10, TimeUnit.SECONDS))
        return webView
    }

    private fun measure(
        webView: WebView,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int
    ): JSONObject {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val completed = CountDownLatch(1)
        var result: JSONObject? = null
        instrumentation.runOnMainSync {
            webView.evaluateJavascript(
                "window.LumiReader.configure({flow:'paginated',theme:'day'," +
                    "insets:{top:$top,right:$right,bottom:$bottom,left:$left}});"
            ) {
                webView.postDelayed({
                    webView.evaluateJavascript(
                        "(function(){var r=document.getElementById('page').getBoundingClientRect();" +
                            "var vv=window.visualViewport;" +
                            "return JSON.stringify({" +
                            "viewportWidth:Math.round(vv?vv.width:window.innerWidth)," +
                            "viewportHeight:Math.round(vv?vv.height:window.innerHeight)," +
                            "left:r.left,top:r.top," +
                            "width:r.width,height:r.height});})()"
                    ) { encoded ->
                        val decoded = runCatching { JSONArray("[$encoded]").optString(0) }.getOrNull()
                        result = decoded?.let { runCatching { JSONObject(it) }.getOrNull() }
                        completed.countDown()
                    }
                }, 350L)
            }
        }
        assertTrue("fixed layout measurement must complete", completed.await(10, TimeUnit.SECONDS))
        return requireNotNull(result) { "fixed layout measurement payload was invalid" }
    }
}
