package com.huangder.lumibooks.util

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.huangder.lumibooks.data.local.DataStoreManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 远程字体按需下载（配置驱动，见 docs/app-config.json 的 fonts 段）。
 * 配置复用 UpdateChecker 的 app-config.json 拉取（含小时级缓存击穿），
 * 下载实现参照 MineruApiClient.download() 的流式写法。
 */
@Singleton
class FontDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStoreManager: DataStoreManager
) {
    private val allowedHosts = setOf(
        "raw.githubusercontent.com",
        "cdn.jsdelivr.net"
    )

    /** 确保 key 对应字体已下载且版本最新；成功返回字体文件，失败返回 null。 */
    suspend fun ensure(key: String): File? = withContext(Dispatchers.IO) {
        val config = UpdateChecker.fetchUpdateConfig()
            ?.remoteFonts
            ?.firstOrNull { it.key == key }
            ?: return@withContext null
        val target = fontFile(key)
        val versions = dataStoreManager.remoteFontVersions.first()
        if (versions[key] == config.version && target.isFile && target.length() == config.sizeBytes) {
            return@withContext target
        }

        val temporary = File(target.parentFile, "remote_$key.ttf.tmp")
        for (url in config.urls) {
            val ok = runCatching { download(url, temporary, config.sizeBytes) }.isSuccess &&
                temporary.length() == config.sizeBytes
            if (ok) {
                target.parentFile?.mkdirs()
                val moved = temporary.renameTo(target) ||
                    (temporary.copyTo(target, overwrite = true)?.let { temporary.delete(); true } ?: false)
                if (moved) {
                    dataStoreManager.saveRemoteFontVersion(key, config.version)
                    return@withContext target
                }
            }
            temporary.delete()
        }
        null
    }

    private fun fontFile(key: String): File =
        File(context.filesDir, "fonts/remote_$key.ttf").apply { parentFile?.mkdirs() }

    private fun download(url: String, destination: File, maxBytes: Long) {
        val uri = URI(url)
        val host = uri.host?.lowercase() ?: throw IllegalStateException("下载地址无主机")
        if (uri.scheme != "https" || host !in allowedHosts) throw IllegalStateException("不信任的下载源")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", "LumiBooks-Android")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            val declaredLength = connection.contentLengthLong
            if (declaredLength > maxBytes) throw IllegalStateException("超出下载上限")
            var total = 0L
            connection.inputStream.buffered().use { input ->
                FileOutputStream(destination).buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) throw IllegalStateException("超出下载上限")
                        output.write(buffer, 0, read)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}

/** 已下载字体的纯函数加载入口（无需注入，供各渲染路径调用）。 */
object DownloadedFonts {
    fun file(context: Context, key: String): File? =
        File(context.filesDir, "fonts/remote_$key.ttf").takeIf { it.isFile && it.length() > 0L }

    fun typeface(context: Context, key: String): Typeface? =
        file(context, key)?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }

    fun family(context: Context, key: String): FontFamily? =
        typeface(context, key)?.let(::FontFamily)

    /** Compose 侧加载：未下载时回退系统字体。 */
    @Composable
    fun familyOrDefault(key: String): FontFamily {
        val context = LocalContext.current
        return remember(context, key) { family(context, key) ?: FontFamily.Default }
    }
}
