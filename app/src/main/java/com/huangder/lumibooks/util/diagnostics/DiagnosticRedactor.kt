package com.huangder.lumibooks.util.diagnostics

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class DiagnosticRedactor private constructor(private val salt: String) {
    constructor(context: Context) : this(loadSalt(context))

    companion object {
        internal fun forTesting(salt: String): DiagnosticRedactor = DiagnosticRedactor(salt)

        private fun loadSalt(context: Context): String {
            val saltFile = File(context.filesDir, "diagnostics/redaction.salt")
            return runCatching {
                saltFile.parentFile?.mkdirs()
                if (!saltFile.exists()) saltFile.writeText(UUID.randomUUID().toString())
                saltFile.readText()
            }.getOrDefault("lumi-diagnostic")
        }
    }

    fun hashIdentifier(value: String): String = sha256("$salt:$value").take(16)

    fun sanitize(value: String): String {
        return sanitizeInternal(value, 256)
    }

    fun sanitizeStackTrace(value: String): String = sanitizeInternal(value, 8192)

    private fun sanitizeInternal(value: String, maxLength: Int): String {
        var result = value
            .replace(Regex("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+"), "Bearer [REDACTED]")
            .replace(Regex("(?i)(token|password|passwd|secret|api[-_]?key|cookie)\\s*[:=]\\s*[^,;\\s]+"), "$1=[REDACTED]")
            .replace(Regex("https?://([^/\\s]+)([^\\s]*)"), "https://$1/[REDACTED]")
            .replace(Regex("(?i)([A-Z]:\\\\|/)([^\\s\\\"']+)[\\\\/]([^\\s\\\"']+)"), "[PATH]")
            .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[EMAIL]")
            .replace(Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"), "[IP]")
        return result.take(maxLength)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
