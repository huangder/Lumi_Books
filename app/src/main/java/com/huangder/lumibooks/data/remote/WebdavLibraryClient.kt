package com.huangder.lumibooks.data.remote

import com.huangder.lumibooks.data.remote.WebdavListingParser.withTrailingSlash
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Read-only WebDAV browsing for the remote library: PROPFIND listings and GET downloads. */
@Singleton
class WebdavLibraryClient @Inject constructor() {

    private fun rootUrl(source: RemoteSource): HttpUrl = RemoteHttp.parseUrl(source.url).withTrailingSlash()

    private fun clientFor(source: RemoteSource, password: String?): OkHttpClient =
        RemoteHttp.clientFor(rootUrl(source), source.username, password, preemptiveBasic = true)

    suspend fun list(source: RemoteSource, password: String?, url: String = source.url): RemotePage =
        runInterruptible(Dispatchers.IO) {
            val root = rootUrl(source)
            val collection = RemoteHttp.parseUrl(url).withTrailingSlash()
            val request = Request.Builder()
                .url(collection)
                .header("Depth", "1")
                .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_MEDIA_TYPE))
                .build()
            val call = clientFor(source, password).newCall(request)
            val response = try {
                call.execute()
            } catch (error: IOException) {
                if (call.isCanceled()) throw CancellationException("WebDAV listing cancelled")
                throw error.toRemoteException()
            }
            val body = response.use {
                if (it.code == 405 || it.code == 501) {
                    throw RemoteException(RemoteErrorKind.NOT_A_CATALOG, it.code, "Not a WebDAV collection")
                }
                if (!it.isSuccessful) throw RemoteException(remoteKindForStatus(it.code), it.code, "HTTP ${it.code}")
                try {
                    it.body?.bytes() ?: ByteArray(0)
                } catch (error: IOException) {
                    throw error.toRemoteException()
                }
            }
            if (body.size > MAX_LISTING_BYTES) throw RemoteException(RemoteErrorKind.TOO_LARGE)
            val entries = WebdavListingParser.parse(body, collection)
            val rootSegments = WebdavListingParser.normalizedSegments(root)
            val folders = entries
                .filter { it.isCollection && !it.name.startsWith('.') }
                .sortedBy { it.name.lowercase() }
                .map { RemoteFolder(title = it.name, url = it.url.toString()) }
            val books = entries
                .filter { !it.isCollection && !it.name.startsWith('.') }
                .mapNotNull { entry ->
                    val extension = RemoteBookFiles.extensionFromName(entry.name) ?: return@mapNotNull null
                    val segments = WebdavListingParser.normalizedSegments(entry.url)
                    val relative = if (segments.take(rootSegments.size) == rootSegments) {
                        segments.drop(rootSegments.size)
                    } else {
                        segments
                    }.joinToString("/")
                    RemoteBookItem(
                        sourceId = source.id,
                        key = "webdav:$relative",
                        title = entry.name.substringBeforeLast('.'),
                        formats = listOf(
                            RemoteFormat(
                                url = entry.url.toString(),
                                extension = extension,
                                mimeType = entry.contentType,
                                sizeBytes = entry.sizeBytes
                            )
                        ),
                        coverUrl = siblingCoverUrl(entry.name, entries),
                        fileName = entry.name,
                        remotePath = "/$relative",
                        lastModified = entry.lastModified
                    )
                }
                .sortedBy { it.title.lowercase() }
            val collectionSegments = WebdavListingParser.normalizedSegments(collection)
            RemotePage(
                title = if (collectionSegments.size <= rootSegments.size) {
                    source.name
                } else {
                    collectionSegments.last()
                },
                url = collection.toString(),
                folders = folders,
                books = books
            )
        }

    suspend fun download(
        source: RemoteSource,
        password: String?,
        url: String,
        destination: File,
        isCancelled: () -> Boolean,
        onProgress: (Long, Long) -> Unit
    ): RemoteHttp.Downloaded = runInterruptible(Dispatchers.IO) {
        val target = RemoteHttp.parseUrl(url)
        // Credentials are scoped to the library origin; a listing never points elsewhere.
        if (!target.host.equals(rootUrl(source).host, ignoreCase = true)) {
            throw RemoteException(RemoteErrorKind.INVALID_URL)
        }
        RemoteHttp.download(clientFor(source, password), target, destination, isCancelled, onProgress)
    }

    private companion object {
        val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        const val MAX_LISTING_BYTES = 16 * 1024 * 1024
        const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:">
  <D:prop>
    <D:resourcetype/>
    <D:displayname/>
    <D:getcontentlength/>
    <D:getcontenttype/>
    <D:getlastmodified/>
  </D:prop>
</D:propfind>
"""
    }

    private fun siblingCoverUrl(bookName: String, entries: List<WebdavListingEntry>): String? {
        val stem = bookName.substringBeforeLast('.', bookName).lowercase()
        val imageExtensions = listOf("jpg", "jpeg", "png", "webp")
        return imageExtensions.firstNotNullOfOrNull { extension ->
            entries.firstOrNull { image ->
                !image.isCollection && image.name.substringAfterLast('.', "").equals(extension, ignoreCase = true) &&
                    image.name.substringBeforeLast('.', image.name).equals(stem, ignoreCase = true)
            }?.url?.toString()
        }
    }
}
