package com.huangder.lumibooks.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 统一检查更新工具：通过 GitHub Raw 上的 update_config.json
 * 一次请求覆盖 App 版本、用户协议版本、隐私政策版本三项检查。
 *
 * 配置文件地址：
 * https://raw.githubusercontent.com/huangder/android_books/main/update_config.json
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"

    /** GitHub Raw CDN 上的更新配置 URL */
    private const val CONFIG_URL =
        "https://raw.githubusercontent.com/huangder/android_books/main/update_config.json"

    /** 网络请求超时（毫秒） */
    private const val TIMEOUT_MS = 10_000

    // ─── 数据模型 ────────────────────────────────────────────

    /** 远程更新配置 */
    data class UpdateConfig(
        val latestVersion: String,
        val latestVersionCode: Int,
        val releaseUrl: String,
        val termsVersion: Int,
        val privacyVersion: Int
    )

    /** 各项检查结果汇总 */
    data class CheckResult(
        val hasAppUpdate: Boolean = false,
        val appVersion: String = "",
        val releaseUrl: String = "",
        val hasTermsUpdate: Boolean = false,
        val termsVersion: Int = 0,
        val hasPrivacyUpdate: Boolean = false,
        val privacyVersion: Int = 0,
        val isNetworkError: Boolean = false
    )

    // ─── 公开方法 ────────────────────────────────────────────

    /**
     * 从 GitHub Raw 拉取更新配置。
     * @return UpdateConfig 成功时返回，网络/解析失败返回 null
     */
    suspend fun fetchUpdateConfig(): UpdateConfig? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(CONFIG_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("User-Agent", "LumiBooks-Android")

                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "HTTP $code")
                    return@withContext null
                }

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)

                UpdateConfig(
                    latestVersion = json.optString("latest_version", ""),
                    latestVersionCode = json.optInt("latest_version_code", 0),
                    releaseUrl = json.optString("release_url", ""),
                    termsVersion = json.optInt("terms_version", 0),
                    privacyVersion = json.optInt("privacy_version", 0)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch update config: ${e.message}")
                null
            }
        }
    }

    /**
     * 执行完整的更新检查，对比远程配置与本地状态。
     *
     * @param currentVersion  本地 App 版本号（BuildConfig.VERSION_NAME）
     * @param acceptedTerms   本地已接受的用户协议版本
     * @param acceptedPrivacy 本地已接受的隐私政策版本
     */
    fun evaluate(
        config: UpdateConfig,
        currentVersion: String,
        acceptedTerms: Int,
        acceptedPrivacy: Int
    ): CheckResult {
        val hasAppUpdate = config.latestVersion.isNotEmpty() &&
                config.latestVersion != currentVersion

        val hasTermsUpdate = config.termsVersion > acceptedTerms
        val hasPrivacyUpdate = config.privacyVersion > acceptedPrivacy

        return CheckResult(
            hasAppUpdate = hasAppUpdate,
            appVersion = config.latestVersion,
            releaseUrl = config.releaseUrl,
            hasTermsUpdate = hasTermsUpdate,
            termsVersion = config.termsVersion,
            hasPrivacyUpdate = hasPrivacyUpdate,
            privacyVersion = config.privacyVersion
        )
    }
}
