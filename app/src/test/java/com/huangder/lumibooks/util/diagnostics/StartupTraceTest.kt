package com.huangder.lumibooks.util.diagnostics

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import com.huangder.lumibooks.BuildConfig
import com.huangder.lumibooks.util.LaunchThemeController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import android.app.Application
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.GZIPInputStream
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class StartupTraceTest {
    @Test fun activityRecreationAndBuildGateAreRecordedWithoutIntentData() = runBlocking {
        val logger = DefaultDiagnosticLogger(RuntimeEnvironment.getApplication())
        val before = logger.snapshot().size
        DiagnosticLoggerRegistry.logger = logger
        try {
            val controller = Robolectric.buildActivity(android.app.Activity::class.java).setup()
            StartupTrace.activity(controller.get(), "activity_created", false)
            controller.recreate()
            StartupTrace.activity(controller.get(), "activity_created", true)
            val added = logger.snapshot().drop(before)
            if (BuildConfig.STARTUP_TRACE_ENABLED) {
                assertEquals(2, added.size)
                assertEquals(listOf("false", "true"), added.map { it.attributes["restored"] })
                assertNotEquals(added[0].attributes["instance"], added[1].attributes["instance"])
            } else assertTrue(added.isEmpty())
            controller.pause().stop().destroy()
        } finally {
            DiagnosticLoggerRegistry.logger = null
            logger.flush()
        }
        Unit
    }

    @Test fun disabledRecorderDoesNotReadClockOrWrite() {
        StartupTraceRecorder(false, { _, _ -> error("sink") }, { error("clock") }).record("test")
    }

    @Test fun releaseTracingDoesNotEnableDiagnosticOrDebugBuild() {
        if (BuildConfig.BUILD_TYPE == "release") {
            assertFalse(BuildConfig.DIAGNOSTIC_BUILD)
            assertFalse(BuildConfig.DEBUG)
        }
    }

    @Test fun sequenceAndProcessIdentitySurviveSameTimestampAndSinkFailure() {
        val fields = mutableListOf<Map<String, Any?>>()
        val recorder = StartupTraceRecorder(true, { _, values -> fields += values }, { 10L }, "process-a")
        recorder.record("entry")
        recorder.record("draw")
        assertEquals(listOf(1L, 2L), fields.map { it["sequence"] })
        assertTrue(fields.all { it["processId"] == "process-a" && it["elapsedMs"] == 10L })
        StartupTraceRecorder(true, { _, _ -> error("disk failure") }, { 10L }).record("safe")
    }

    @Test fun intentAllowlistExcludesSensitiveFieldsButPreservesStaleAliasAndSplash() {
        for (enabled in listOf(false, true)) {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName("com.huangder.lumibooks", "com.huangder.lumibooks.ui.splash.SplashLauncherActivity")
                addCategory(Intent.CATEGORY_LAUNCHER)
                data = Uri.parse("content://private/secret-book.epub")
                clipData = ClipData.newPlainText("secret", "secret-clip")
                putExtra("bookId", "secret-id")
                putExtra(LaunchThemeController.EXTRA_SPLASH_ENABLED, enabled)
            }
            val fields = StartupTrace.intentFields(intent)
            assertFalse(fields.toString().contains("secret"))
            assertEquals(enabled, fields["splashExtra"])
            assertEquals(true, fields["launcherCategory"])
            assertTrue(fields["component"].toString().endsWith("SplashLauncherActivity"))
        }
        assertNull(StartupTrace.intentFields(Intent("secret-action"))["action"])
    }

    private fun event(sequence: Int, time: Long = 5_000_000, process: String = "p1", session: String? = null) =
        DiagnosticEvent(time, DiagnosticLevel.INFO, "startup", "frame-$sequence", sessionId = session,
            attributes = mapOf("processId" to process, "sequence" to "$sequence", "elapsedMs" to "42"))

    @Test fun exportIgnoresManualSessionAndOrdersSequencesWithinProcesses() {
        val events = listOf(event(2, session = "manual"), event(1), event(3, process = "p2"),
            event(4, time = 5_000_000 - 40 * 60 * 1000), event(9, time = 1))
        val selected = selectStartupEvents(events, 5_000_001, 8000)
        assertEquals(4, selected.size)
        val summary = startupSummary(selected.reversed())
        assertTrue(summary.indexOf("frame-1") < summary.indexOf("frame-2"))
        assertTrue(summary.contains("进程启动 p2"))
        assertFalse(summary.contains("frame-9"))
        assertTrue(summary.contains("录屏"))
    }

    @Test fun persistedRecordsReloadAcrossLoggerInstancesAndRotate() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val directory = File(context.filesDir, "diagnostics").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val logger = DefaultDiagnosticLogger(context)
        logger.log("startup", "first_process", attributes = mapOf("processId" to "one", "sequence" to 1))
        logger.flush()
        val reloaded = DefaultDiagnosticLogger(context)
        assertTrue(reloaded.snapshot().any { it.event == "first_process" })
        // Reach the production rotation boundary without generating thousands of events.
        java.io.RandomAccessFile(File(directory, "events.ndjson"), "rw").use {
            it.setLength(if (BuildConfig.DIAGNOSTIC_BUILD) 8L * 1024 * 1024 else 2L * 1024 * 1024)
        }
        reloaded.log("startup", "second_process", attributes = mapOf("processId" to "two", "sequence" to 1))
        reloaded.flush()
        assertTrue(File(directory, "events.1.ndjson").exists())
        assertTrue(File(directory, "events.ndjson").readText().contains("second_process"))
        assertTrue(File(directory, "events.ndjson").length() < 4096)
        // Remove padding used solely to exercise the byte threshold, preserving real NDJSON.
        File(directory, "events.1.ndjson").writeText(logger.snapshot().first().toJson().toString() + "\n")
        val nextProcess = DefaultDiagnosticLogger(context)
        assertTrue(nextProcess.snapshot().any { it.event == "first_process" })
        assertTrue(nextProcess.snapshot().any { it.event == "second_process" })
        val manager = DefaultDiagnosticSessionManager(nextProcess, context)
        manager.start(DiagnosticIssueType.SYNC)
        val zip = manager.buildBundle(DiagnosticBundleRequest(issueType = DiagnosticIssueType.OTHER,
            userDescription = "startup", startupOnly = true))
        ZipFile(zip).use { archive ->
            val manifest = JSONObject(archive.getInputStream(archive.getEntry("manifest.json")).bufferedReader().use { it.readText() })
            assertEquals(BuildConfig.STARTUP_TRACE_ENABLED, manifest.getBoolean("startupTraceEnabled"))
            assertEquals(BuildConfig.DIAGNOSTIC_BUILD, manifest.getBoolean("diagnosticBuild"))
            assertEquals(BuildConfig.BUILD_TYPE, manifest.getString("buildType"))
            val text = GZIPInputStream(archive.getInputStream(archive.getEntry("events.ndjson.gz"))).bufferedReader().use { it.readText() }
            assertTrue(text.contains("first_process"))
            assertTrue(text.contains("second_process"))
            if (BuildConfig.STARTUP_TRACE_ENABLED) {
                assertNotNull(archive.getEntry("startup-summary.md"))
                assertFalse(text.contains("diagnostic_session_started"))
            } else assertNull(archive.getEntry("startup-summary.md"))
        }
    }
}
