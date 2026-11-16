package com.huangder.lumibooks.data.sync

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class WebdavLogLevel { INFO, WARN, ERROR }

data class WebdavLogEntry(val time: Long, val level: WebdavLogLevel, val message: String)

data class WebdavSyncRun(
    val startedAt: Long,
    val trigger: WebdavSyncTrigger,
    val finishedAt: Long?,
    val success: Boolean?,
    val entries: List<WebdavLogEntry>
)

/**
 * Keeps a human-readable log of the most recent full WebDAV syncs so users can see why a
 * sync failed. Each run is its own file; only [MAX_RUNS] files are kept. Credentials never
 * reach this log: messages pass through [sanitizeWebdavLogMessage].
 */
@Singleton
class WebdavSyncLogStore internal constructor(private val directory: File) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, "webdav_logs"))

    private val lock = Any()
    private var currentFile: File? = null

    fun beginRun(trigger: WebdavSyncTrigger, now: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            runCatching {
                directory.mkdirs()
                val file = File(directory, "run-$now.log")
                file.writeText(JSONObject().put("type", "begin").put("time", now)
                    .put("trigger", trigger.name).toString() + "\n")
                currentFile = file
                prune()
            }.onFailure { Log.w(TAG, "Unable to start sync log: ${it.message}") }
        }
    }

    fun log(level: WebdavLogLevel, message: String, now: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val file = currentFile ?: return
            runCatching {
                file.appendText(JSONObject().put("type", "entry").put("time", now)
                    .put("level", level.name).put("message", sanitizeWebdavLogMessage(message))
                    .toString() + "\n")
            }
        }
    }

    fun info(message: String) = log(WebdavLogLevel.INFO, message)
    fun warn(message: String) = log(WebdavLogLevel.WARN, message)
    fun error(message: String) = log(WebdavLogLevel.ERROR, message)

    fun endRun(success: Boolean, now: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val file = currentFile ?: return
            runCatching {
                file.appendText(JSONObject().put("type", "end").put("time", now)
                    .put("success", success).toString() + "\n")
            }
            currentFile = null
        }
    }

    /** Most recent first. */
    suspend fun recentRuns(): List<WebdavSyncRun> = withContext(Dispatchers.IO) {
        synchronized(lock) { runFiles().take(MAX_RUNS).mapNotNull(::parse) }
    }

    fun clear() {
        synchronized(lock) {
            currentFile = null
            directory.listFiles()?.forEach { it.delete() }
        }
    }

    private fun runFiles(): List<File> = directory.listFiles { file ->
        file.isFile && file.name.startsWith("run-") && file.name.endsWith(".log")
    }.orEmpty().sortedByDescending { it.name.removePrefix("run-").removeSuffix(".log").toLongOrNull() ?: 0L }

    private fun prune() {
        runFiles().drop(MAX_RUNS).forEach { it.delete() }
    }

    private fun parse(file: File): WebdavSyncRun? = runCatching {
        var startedAt = 0L
        var trigger = WebdavSyncTrigger.MANUAL
        var finishedAt: Long? = null
        var success: Boolean? = null
        val entries = mutableListOf<WebdavLogEntry>()
        file.forEachLine { line ->
            val json = runCatching { JSONObject(line) }.getOrNull() ?: return@forEachLine
            when (json.optString("type")) {
                "begin" -> {
                    startedAt = json.optLong("time")
                    trigger = runCatching { WebdavSyncTrigger.valueOf(json.optString("trigger")) }
                        .getOrDefault(WebdavSyncTrigger.MANUAL)
                }
                "entry" -> entries += WebdavLogEntry(
                    time = json.optLong("time"),
                    level = runCatching { WebdavLogLevel.valueOf(json.optString("level")) }
                        .getOrDefault(WebdavLogLevel.INFO),
                    message = json.optString("message")
                )
                "end" -> {
                    finishedAt = json.optLong("time")
                    success = json.optBoolean("success")
                }
            }
        }
        WebdavSyncRun(startedAt, trigger, finishedAt, success, entries)
    }.getOrNull()

    companion object {
        const val MAX_RUNS = 3
        private const val TAG = "WebdavSyncLog"
    }
}

/** Strips credentials from URLs and key/value pairs but keeps hosts, which users need to debug. */
internal fun sanitizeWebdavLogMessage(value: String): String = value
    .replace(Regex("(?i)(https?://)[^/\\s@]+@"), "$1")
    // Credential tokens contain a digit or base64 padding, so prose like "basic auth" survives.
    .replace(Regex("(?i)\\b(basic|bearer|digest)\\s+(?=[A-Za-z0-9._~+/=-]*[0-9=+/])[A-Za-z0-9._~+/=-]{8,}"), "$1 [REDACTED]")
    .replace(Regex("(?i)(token|password|passwd|secret|api[-_]?key|cookie)\\s*[:=]\\s*[^,;\\s]+"), "$1=[REDACTED]")
    .take(1000)
