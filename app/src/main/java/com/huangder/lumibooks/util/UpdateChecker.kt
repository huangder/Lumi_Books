package com.huangder.lumibooks.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Remote notice and update checker.
 *
 * The app reads docs/app-config.json from the GitHub repository Raw URL at startup
 * or when the user manually checks for updates. This static file drives notice,
 * normal update, and forced update dialogs without a separate server.
 *
 * Config URL:
 * https://raw.githubusercontent.com/huangder/Lumi_Books/main/docs/app-config.json
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"

    /** GitHub repository Raw config URL. Do not use a custom domain for mobile reliability. */
    private const val CONFIG_URL = "https://raw.githubusercontent.com/huangder/Lumi_Books/main/docs/app-config.json"

    /** Network request timeout. */
    private const val TIMEOUT_MS = 10_000

    // Public models: keep fields compatible with the old update_config.json and the new app-config.json.

    /** Remote notice config. */
    data class NoticeConfig(
        val id: String,
        val enabled: Boolean,
        val minVersionCode: Long,
        val maxVersionCode: Long,
        val title: String,
        val message: String
    )

    /** Remote update config. */
    data class UpdateConfig(
        val latestVersion: String,
        val latestVersionCode: Long,
        val releaseUrl: String,
        val updateTitle: String,
        val updateMessage: String,
        val changelog: String,
        val forceUpdateBelowVersionCode: Long,
        val termsVersion: Int,
        val privacyVersion: Int,
        val notice: NoticeConfig?
    )

    /** Evaluated remote check result. */
    data class CheckResult(
        val hasAppUpdate: Boolean = false,
        val isForceUpdate: Boolean = false,
        val appVersion: String = "",
        val latestVersionCode: Long = 0L,
        val releaseUrl: String = "",
        val updateTitle: String = "\u53d1\u73b0\u65b0\u7248\u672c",
        val updateMessage: String = "",
        val changelog: String = "",
        val notice: NoticeConfig? = null,
        val hasTermsUpdate: Boolean = false,
        val termsVersion: Int = 0,
        val hasPrivacyUpdate: Boolean = false,
        val privacyVersion: Int = 0,
        val isNetworkError: Boolean = false
    )

    // Public models: keep fields compatible with the old update_config.json and the new app-config.json.

    /**
     * Fetch static config from GitHub Raw.
     * @return UpdateConfig on success, or null for network/JSON failures.
     */
    suspend fun fetchUpdateConfig(): UpdateConfig? {
        return withContext(Dispatchers.IO) {
            fetchConfiguredUpdate()
        }
    }

    private fun fetchConfiguredUpdate(): UpdateConfig? {
        return try {
            val cacheBustedUrl = "$CONFIG_URL?t=${System.currentTimeMillis() / (60 * 60 * 1000)}"
            val json = JSONObject(openJsonConnection(cacheBustedUrl))
            val updateJson = json.optJSONObject("update")
            val noticeJson = json.optJSONObject("notice")

            UpdateConfig(
                latestVersion = updateJson.optStringCompat(
                    keys = arrayOf("latest_version_name", "latest_version"),
                    fallback = json.optString("latest_version", "")
                ),
                latestVersionCode = updateJson.optLongCompat(
                    keys = arrayOf("latest_version_code"),
                    fallback = json.optLong("latest_version_code", 0L)
                ),
                releaseUrl = updateJson.optStringCompat(
                    keys = arrayOf("download_url", "release_url"),
                    fallback = json.optString("release_url", "")
                ),
                updateTitle = updateJson.optStringCompat(
                    keys = arrayOf("title"),
                    fallback = json.optString("update_title", "\u53d1\u73b0\u65b0\u7248\u672c")
                ).ifBlank { "\u53d1\u73b0\u65b0\u7248\u672c" },
                updateMessage = updateJson.optStringCompat(
                    keys = arrayOf("message"),
                    fallback = json.optString("update_message", "")
                ),
                changelog = updateJson.optChangelog(
                    fallback = json.optChangelog(fallback = "")
                ),
                forceUpdateBelowVersionCode = updateJson.optLongCompat(
                    keys = arrayOf("force_update_below_version_code", "min_supported_version_code"),
                    fallback = json.optLong("force_update_below_version_code", 0L)
                ),
                termsVersion = json.optInt("terms_version", 0),
                privacyVersion = json.optInt("privacy_version", 0),
                notice = parseNotice(noticeJson)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch update config: ${e.message}")
            null
        }
    }

    private fun parseNotice(json: JSONObject?): NoticeConfig? {
        if (json == null) return null
        val id = json.optString("id", "").trim()
        val title = json.optString("title", "").trim()
        val message = json.optString("message", "").trim()
        if (id.isBlank() || title.isBlank() || message.isBlank()) return null
        return NoticeConfig(
            id = id,
            enabled = json.optBoolean("enabled", false),
            minVersionCode = json.optLong("min_version_code", 0L),
            maxVersionCode = json.optLong("max_version_code", Long.MAX_VALUE),
            title = title,
            message = message
        )
    }

    private fun openJsonConnection(urlString: String): String {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "LumiBooks-Android")
        conn.setRequestProperty("Cache-Control", "no-cache")

        return try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }
            conn.inputStream.bufferedReader().use { reader -> reader.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Evaluate whether current local state should show update, notice, or policy dialogs.
     *
     * @param currentVersion     Current app versionName; fallback only when versionCode is unavailable.
     * @param currentVersionCode Current app versionCode; preferred for update comparison.
     * @param acceptedTerms      Terms version accepted by the user.
     * @param acceptedPrivacy    Privacy policy version accepted by the user.
     */
    fun evaluate(
        config: UpdateConfig,
        currentVersion: String,
        currentVersionCode: Long = 0L,
        acceptedTerms: Int,
        acceptedPrivacy: Int
    ): CheckResult {
        val hasAppUpdate = if (config.latestVersionCode > 0L && currentVersionCode > 0L) {
            config.latestVersionCode > currentVersionCode
        } else {
            config.latestVersion.isNotEmpty() && isRemoteVersionNewer(config.latestVersion, currentVersion)
        }

        val isForceUpdate = config.forceUpdateBelowVersionCode > 0L &&
                currentVersionCode > 0L &&
                currentVersionCode < config.forceUpdateBelowVersionCode

        val hasTermsUpdate = config.termsVersion > acceptedTerms
        val hasPrivacyUpdate = config.privacyVersion > acceptedPrivacy
        val eligibleNotice = config.notice?.takeIf { notice ->
            notice.enabled &&
                    currentVersionCode >= notice.minVersionCode &&
                    currentVersionCode <= notice.maxVersionCode
        }

        return CheckResult(
            hasAppUpdate = hasAppUpdate || isForceUpdate,
            isForceUpdate = isForceUpdate,
            appVersion = config.latestVersion,
            latestVersionCode = config.latestVersionCode,
            releaseUrl = config.releaseUrl,
            updateTitle = config.updateTitle,
            updateMessage = config.updateMessage,
            changelog = config.changelog,
            notice = eligibleNotice,
            hasTermsUpdate = hasTermsUpdate,
            termsVersion = config.termsVersion,
            hasPrivacyUpdate = hasPrivacyUpdate,
            privacyVersion = config.privacyVersion
        )
    }

    /**
     * Compare dotted numeric version names instead of checking whether their text differs.
     * A server configuration can temporarily lag behind a locally installed build, which
     * must never be reported as an available update.
     */
    private fun isRemoteVersionNewer(remoteVersion: String, currentVersion: String): Boolean {
        val remoteParts = parseVersionParts(remoteVersion)
        val currentParts = parseVersionParts(currentVersion)
        val partCount = maxOf(remoteParts.size, currentParts.size)

        for (index in 0 until partCount) {
            val remotePart = remoteParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (remotePart != currentPart) return remotePart > currentPart
        }
        return false
    }

    private fun parseVersionParts(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .split('.')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    private fun JSONObject?.optStringCompat(keys: Array<String>, fallback: String): String {
        if (this == null) return fallback
        for (key in keys) {
            val value = optString(key, "").trim()
            if (value.isNotBlank()) return value
        }
        return fallback
    }

    private fun JSONObject?.optLongCompat(keys: Array<String>, fallback: Long): Long {
        if (this == null) return fallback
        for (key in keys) {
            if (has(key)) return optLong(key, fallback)
        }
        return fallback
    }

    private fun JSONObject?.optChangelog(fallback: String): String {
        if (this == null) return fallback
        optString("changelog", "").trim().takeIf { it.isNotBlank() }?.let { return it }
        optString("release_notes", "").trim().takeIf { it.isNotBlank() }?.let { return it }
        val array = optJSONArray("changelog_items") ?: optJSONArray("release_note_items")
        return array?.joinLines().orEmpty().ifBlank { fallback }
    }

    private fun JSONArray.joinLines(): String {
        val lines = mutableListOf<String>()
        for (index in 0 until length()) {
            optString(index, "").trim().takeIf { it.isNotBlank() }?.let { lines += "- $it" }
        }
        return lines.joinToString("\n")
    }
}
