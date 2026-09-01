package com.huangder.lumibooks.util.diagnostics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultDiagnosticSessionManager @Inject constructor(
    private val logger: DiagnosticLogger,
    @ApplicationContext private val context: Context
) : DiagnosticSessionManager {
    private val builder = DiagnosticBundleBuilder(context, logger)

    override fun start(issueType: DiagnosticIssueType): DiagnosticSession = logger.startSession(issueType)

    override fun stop(sessionId: String) = logger.stopSession(sessionId)

    override suspend fun buildBundle(request: DiagnosticBundleRequest): File = withContext(Dispatchers.IO) {
        logger.flush()
        builder.build(request)
    }
}

private class DiagnosticBundleBuilder(
    private val context: Context,
    private val logger: DiagnosticLogger
) {
    companion object {
        private const val MAX_EVENTS = 2000
        private const val MAX_SUMMARY_BYTES = 12 * 1024
        private const val MAX_SCREENSHOTS = 3
        private const val MAX_SCREENSHOT_BYTES = 2L * 1024L * 1024L
    }

    fun build(request: DiagnosticBundleRequest): File {
        val now = System.currentTimeMillis()
        val session = logger.activeSession()?.takeIf { it.id == request.sessionId }
        val all = buildList {
            addAll(logger.snapshot().filter { it.timestamp >= now - 20 * 60 * 1000L })
            if (request.includePreviousCrash) logger.previousCrash()?.let(::add)
        }
        val selected = selectEvents(all, request, session).takeLast(MAX_EVENTS)
        val outputDir = File(context.cacheDir, "diagnostics").also { it.mkdirs() }
        outputDir.listFiles()?.filter { now - it.lastModified() > 24 * 60 * 60 * 1000L }?.forEach { it.delete() }
        val output = File(outputDir, "lumi-diagnostic-${now}.zip")

        val errors = selected.filter { it.level >= DiagnosticLevel.ERROR || it.exceptionType != null }
            .groupBy { "${it.exceptionType}:${it.event}:${it.exceptionMessage}" }
            .map { (signature, grouped) ->
                JSONObject().apply {
                    put("signature", signature.take(256))
                    put("count", grouped.size)
                    put("firstTimestamp", grouped.minOf { it.timestamp })
                    put("lastTimestamp", grouped.maxOf { it.timestamp })
                    put("operationIds", grouped.mapNotNull { it.operationId }.distinct())
                    put("exceptionType", grouped.firstOrNull()?.exceptionType ?: JSONObject.NULL)
                    put("stackTrace", grouped.firstOrNull()?.stackTrace ?: JSONObject.NULL)
                }
            }

        ZipOutputStream(FileOutputStream(output)).use { zip ->
            val manifest = manifest(request, selected, session, now)
            putText(zip, "manifest.json", manifest.toString(2))
            putText(zip, "device.json", deviceJson().toString(2))
            putText(zip, "errors.json", "[${errors.joinToString(",") { it.toString(2) }}]")
            putText(zip, "summary.md", summary(request, selected, errors, session).take(MAX_SUMMARY_BYTES))
            putText(zip, "README.txt", readme())

            zip.putNextEntry(ZipEntry("events.ndjson.gz"))
            val gzip = GZIPOutputStream(NonClosingOutputStream(zip))
            runCatching {
                selected.forEach { event ->
                    gzip.write(event.toJson().toString().toByteArray(Charsets.UTF_8))
                    gzip.write('\n'.code)
                }
            }.also {
                gzip.finish()
                gzip.flush()
            }
            zip.closeEntry()

            request.screenshotUris.take(MAX_SCREENSHOTS).forEachIndexed { index, uri ->
                copyScreenshot(zip, "screenshots/${index + 1}.jpg", uri)
            }
        }
        logger.log("app", "diagnostic_bundle_created", attributes = mapOf("events" to selected.size, "issueType" to request.issueType.id))
        return output
    }

    private fun selectEvents(
        events: List<DiagnosticEvent>,
        request: DiagnosticBundleRequest,
        session: DiagnosticSession?
    ): List<DiagnosticEvent> {
        val sessionEvents = request.sessionId?.let { id -> events.filter { it.sessionId == id } }.orEmpty()
        val start = session?.startedAt ?: sessionEvents.minOfOrNull { it.timestamp } ?: events.firstOrNull()?.timestamp ?: 0L
        val end = session?.stoppedAt ?: if (sessionEvents.isNotEmpty()) sessionEvents.maxOf { it.timestamp } else Long.MAX_VALUE
        val categories = request.issueType.categories
        val relevant = events.filter { event ->
            event.timestamp in (start - 90_000)..end &&
                (request.issueType == DiagnosticIssueType.OTHER ||
                    event.level >= DiagnosticLevel.WARN || event.category in categories || event.sessionId == request.sessionId)
        }
        return (relevant + events.filter { it.level >= DiagnosticLevel.ERROR && it.timestamp in (start - 90_000)..end })
            .distinctBy { "${it.timestamp}:${it.category}:${it.event}:${it.operationId}" }
            .sortedBy { it.timestamp }
    }

    private fun manifest(request: DiagnosticBundleRequest, events: List<DiagnosticEvent>, session: DiagnosticSession?, now: Long) = JSONObject().apply {
        put("schemaVersion", 1)
        put("generatedAt", now)
        put("appId", context.packageName)
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()?.let { info ->
            put("appVersion", info.versionName)
            put("buildVersion", if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode)
        }
        put("androidApi", android.os.Build.VERSION.SDK_INT)
        put("deviceModel", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".take(128))
        put("abi", android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
        put("locale", context.resources.configuration.locales[0].toLanguageTag())
        put("theme", context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
        put("screen", "${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels}")
        put("issueType", request.issueType.id)
        put("userDescription", DiagnosticRedactor(context).sanitize(request.userDescription))
        put("sessionId", session?.id ?: request.sessionId ?: JSONObject.NULL)
        put("startedAt", events.minOfOrNull { it.timestamp } ?: now)
        put("endedAt", events.maxOfOrNull { it.timestamp } ?: now)
        put("eventCount", events.size)
        put("redaction", "strict-v1")
    }

    private fun deviceJson() = JSONObject().apply {
        put("manufacturer", android.os.Build.MANUFACTURER)
        put("model", android.os.Build.MODEL)
        put("product", android.os.Build.PRODUCT)
        put("androidVersion", android.os.Build.VERSION.RELEASE)
        put("api", android.os.Build.VERSION.SDK_INT)
        put("abis", android.os.Build.SUPPORTED_ABIS.toList())
        put("density", context.resources.displayMetrics.density)
        put("screen", "${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels}")
    }

    private fun summary(
        request: DiagnosticBundleRequest,
        events: List<DiagnosticEvent>,
        errors: List<JSONObject>,
        session: DiagnosticSession?
    ): String {
        val firstError = events.firstOrNull { it.level >= DiagnosticLevel.ERROR || it.exceptionType != null }
        val counts = events.groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }
        return buildString {
            appendLine("# Lumi 诊断摘要")
            appendLine("问题类型: ${request.issueType.id}")
            appendLine("用户现象: ${DiagnosticRedactor(context).sanitize(request.userDescription)}")
            appendLine("会话: ${session?.id ?: request.sessionId ?: "recent-window"}")
            appendLine("事件数: ${events.size}")
            appendLine("建议优先查看模块: ${request.issueType.categories.ifEmpty { setOf("app") }.joinToString(", ")}")
            appendLine()
            appendLine("## 模块事件统计")
            counts.forEach { appendLine("- ${it.key}: ${it.value}") }
            appendLine()
            appendLine("## 首个错误")
            if (firstError == null) appendLine("未捕获到 ERROR/FATAL 事件。") else {
                appendLine("- ${firstError.category}/${firstError.event}")
                appendLine("- 时间: ${firstError.timestamp}")
                appendLine("- 异常: ${firstError.exceptionType ?: "无"}")
                appendLine("- 信息: ${firstError.exceptionMessage ?: firstError.result ?: "无"}")
                firstError.stackTrace?.let { appendLine("```\n${it.take(4096)}\n```") }
            }
            appendLine()
            appendLine("## AI 分析要求")
            appendLine("请先根据本摘要定位首个失败阶段，再按 operationId 检查 events.ndjson.gz；不要假设未提供的书籍正文、凭据或系统 Logcat 信息存在。")
            if (errors.isNotEmpty()) appendLine("已按异常签名去重错误：${errors.size} 组。")
        }
    }

    private fun readme() = """Lumi 诊断包 schemaVersion=1

    本包由用户主动生成，仅包含问题类型相关的结构化事件。
    已移除书籍正文、笔记正文、访问令牌、密码、Cookie、完整路径和 URL 参数。
    userDescription 与 screenshots 由用户主动提供，可能包含个人内容，请在公开分享前复核。
    events.ndjson.gz 是机器可读的完整筛选事件流，summary.md 适合优先交给 AI。
    """.trimIndent()

    private fun putText(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun copyScreenshot(zip: ZipOutputStream, name: String, uri: Uri) {
        runCatching {
            val bitmap = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) ?: return
            val scaled = if (bitmap.width > 2400 || bitmap.height > 2400) {
                val ratio = minOf(2400f / bitmap.width, 2400f / bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
            } else bitmap
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, output)
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            val bytes = output.toByteArray().take(MAX_SCREENSHOT_BYTES.toInt()).toByteArray()
            if (bytes.isNotEmpty()) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}

private class NonClosingOutputStream(private val delegate: java.io.OutputStream) : java.io.FilterOutputStream(delegate) {
    override fun close() = flush()
}
