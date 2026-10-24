package com.huangder.lumibooks.ui.home.remote

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.huangder.lumibooks.data.remote.RemoteHttp
import com.huangder.lumibooks.data.remote.RemoteSource
import java.io.File

/** Display name of an authorized folder from its tree URI, mirroring the import sheet label. */
internal fun authorizedFolderDisplayName(uri: String, fallback: String): String = runCatching {
    val treeId = DocumentsContract.getTreeDocumentId(Uri.parse(uri))
    Uri.decode(treeId).substringAfter(':', treeId).trim('/').substringAfterLast('/')
}.getOrNull()?.takeIf(String::isNotBlank) ?: fallback

/**
 * Cover loaders that authenticate against one remote library. Password-protected catalogs
 * (Calibre with auth, NAS shares) reject anonymous cover requests, so the default loader would
 * only ever show placeholders. Caches are shared because Coil allows one DiskCache per folder.
 */
internal object RemoteImageLoaders {
    @Volatile private var memoryCache: MemoryCache? = null
    @Volatile private var diskCache: DiskCache? = null

    fun create(context: Context, source: RemoteSource, password: String?): ImageLoader {
        val appContext = context.applicationContext
        val memory = memoryCache ?: synchronized(this) {
            memoryCache ?: MemoryCache.Builder(appContext).maxSizePercent(0.10).build().also { memoryCache = it }
        }
        val disk = diskCache ?: synchronized(this) {
            diskCache ?: DiskCache.Builder()
                .directory(File(appContext.cacheDir, "remote_covers"))
                .maxSizeBytes(64L * 1024 * 1024)
                .build()
                .also { diskCache = it }
        }
        return ImageLoader.Builder(appContext)
            .memoryCache(memory)
            .diskCache(disk)
            .okHttpClient {
                runCatching {
                    RemoteHttp.clientFor(RemoteHttp.parseUrl(source.url), source.username, password)
                }.getOrElse { okhttp3.OkHttpClient() }
            }
            .build()
    }
}
