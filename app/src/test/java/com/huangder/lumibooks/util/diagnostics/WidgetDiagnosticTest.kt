package com.huangder.lumibooks.util.diagnostics

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.widget.WidgetDiagnostics
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WidgetDiagnosticTest {
    private fun logger(): DefaultDiagnosticLogger {
        val context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "diagnostics").deleteRecursively()
        return DefaultDiagnosticLogger(context)
    }

    @Test fun widgetCategoryAndIntentKeepGeometryWithoutBookIdentity() = runBlocking {
        val logger = logger()
        DiagnosticLoggerRegistry.logger = logger
        try {
            assertEquals(DiagnosticIssueType.WIDGET, DiagnosticIssueType.fromId("widget"))
            assertTrue(DiagnosticIssueType.WIDGET.categories.containsAll(setOf("app", "widget", "reader")))
            val intent = Intent().setData(Uri.parse("lumibooks://widget/desktop-reading/17/read/private-book"))
                .putExtra(MainActivity.EXTRA_OPEN_BOOK_ID, "private-book")
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 17)
                .putExtra(MainActivity.EXTRA_RELOAD_READING_POSITION, true)
            WidgetDiagnostics.openIntent(intent, "on_create")
            WidgetDiagnostics.openIntent(Intent().setData(Uri.parse("https://example.com/private-book")), "ignored")
            val event = logger.snapshot().single()
            assertEquals("widget", event.category)
            assertEquals("17", event.attributes["widgetId"])
            assertEquals("true", event.attributes["reloadPosition"])
            assertNotNull(event.bookIdHash)
            assertFalse(event.toJson().toString().contains("private-book"))
        } finally {
            logger.flush()
            DiagnosticLoggerRegistry.logger = null
        }
    }

    @Test fun crashPersistsRedactedTailAndExportsItOutsideRecentWindow() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val logger = logger()
        logger.startSession(DiagnosticIssueType.WIDGET)
        logger.log("widget", "page_layout", attributes = mapOf("bitmapHeightPx" to 1200, "token" to "token=hidden"), bookId = "private-book")
        logger.recordCrashSynchronously(IllegalStateException("widget open failed"), "main")
        val crashFile = File(context.filesDir, "diagnostics/last-crash.json")
        val crash = JSONObject(crashFile.readText())
        // No flush before inspecting: the crash file itself must contain the tail.
        assertTrue(crash.getJSONArray("context").toString().contains("page_layout"))
        assertFalse(crash.toString().contains("hidden"))
        assertFalse(crash.toString().contains("private-book"))
        val old = System.currentTimeMillis() - 2 * 60 * 60 * 1000L
        crash.put("timestamp", old)
        val tail = crash.getJSONArray("context")
        for (index in 0 until tail.length()) tail.getJSONObject(index).put("timestamp", old - 1000 + index)
        crashFile.writeText(crash.toString())
        logger.flush()
        val reopened = DefaultDiagnosticLogger(context)
        assertTrue(reopened.previousCrashContext().any { it.event == "page_layout" })
        val crowded = object : DiagnosticLogger by reopened {
            override fun snapshot(): List<DiagnosticEvent> = List(2300) { index ->
                DiagnosticEvent(System.currentTimeMillis(), DiagnosticLevel.INFO, "widget", "recent_$index")
            }
        }
        val manager = DefaultDiagnosticSessionManager(crowded, context)
        val bundle = manager.buildBundle(DiagnosticBundleRequest(issueType = DiagnosticIssueType.WIDGET,
            userDescription = "widget", includePreviousCrash = true))
        ZipFile(bundle).use { zip ->
            val events = GZIPInputStream(zip.getInputStream(zip.getEntry("events.ndjson.gz"))).bufferedReader().use { it.readText() }
            assertTrue(events.contains("previous_uncaught_exception"))
            assertTrue(events.contains(old.toString()))
            assertTrue(events.contains("page_layout"))
        }
        val excluded = manager.buildBundle(DiagnosticBundleRequest(issueType = DiagnosticIssueType.WIDGET,
            userDescription = "widget", includePreviousCrash = false))
        ZipFile(excluded).use { zip ->
            val events = GZIPInputStream(zip.getInputStream(zip.getEntry("events.ndjson.gz"))).bufferedReader().use { it.readText() }
            assertFalse(events.contains("previous_uncaught_exception"))
            assertFalse(events.contains(old.toString()))
        }
        reopened.clearPreviousCrash()
        assertTrue(reopened.previousCrashContext().isEmpty())
        reopened.flush()
    }

    @Test fun hostLogsRawBoundsAndResponsiveSizesBeforeClamping() = runBlocking {
        val logger = logger()
        DiagnosticLoggerRegistry.logger = logger
        try {
            val context = RuntimeEnvironment.getApplication()
            val options = android.os.Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 900)
                putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    arrayListOf(android.util.SizeF(700f, 200f), android.util.SizeF(300f, 900f)))
            }
            WidgetDiagnostics.host(context, 17, options, "options_changed")
            val events = logger.snapshot()
            val bounds = events.single { it.event == "host_options" }
            assertEquals("110", bounds.attributes["minWidthDp"])
            assertEquals("900", bounds.attributes["maxHeightDp"])
            assertEquals("-1", bounds.attributes["minHeightDp"])
            assertNotNull(bounds.attributes["fontScale"])
            assertEquals(listOf("200.0", "900.0"), events.filter { it.event == "host_responsive_size" }
                .map { it.attributes["heightDp"] })
        } finally {
            logger.flush()
            DiagnosticLoggerRegistry.logger = null
        }
    }
}
