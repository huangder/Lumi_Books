package com.huangder.lumibooks.data.remote

import java.io.File
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.OkHttpClient

/** Reads OPDS 1.x catalogs. Credentials only ever go to the catalog's own origin. */
@Singleton
class OpdsClient @Inject constructor() {

    private fun clientFor(source: RemoteSource, password: String?): OkHttpClient =
        RemoteHttp.clientFor(RemoteHttp.parseUrl(source.url), source.username, password)

    suspend fun loadPage(source: RemoteSource, password: String?, url: String = source.url): RemotePage =
        runInterruptible(Dispatchers.IO) {
            val client = clientFor(source, password)
            val fetched = RemoteHttp.fetch(client, RemoteHttp.parseUrl(url), ACCEPT_FEED, MAX_FEED_BYTES)
            if (!OpdsFeedParser.looksLikeAtom(fetched.body)) {
                val kind = if (OpdsFeedParser.looksLikeJson(fetched.body, fetched.contentType)) {
                    RemoteErrorKind.OPDS2_UNSUPPORTED
                } else {
                    RemoteErrorKind.NOT_A_CATALOG
                }
                throw RemoteException(kind)
            }
            val page = OpdsFeedParser.parse(fetched.body, fetched.finalUrl, source.id)
            val template = page.searchTemplate
            if (template != null && template.startsWith(OpdsFeedParser.OPENSEARCH_PREFIX)) {
                val descriptionUrl = template.removePrefix(OpdsFeedParser.OPENSEARCH_PREFIX)
                val resolved = runCatching {
                    val description = RemoteHttp.fetch(
                        client,
                        RemoteHttp.parseUrl(descriptionUrl),
                        ACCEPT_OPENSEARCH,
                        MAX_OPENSEARCH_BYTES
                    )
                    OpdsFeedParser.parseOpenSearchTemplate(description.body, description.finalUrl)
                }.getOrNull()
                page.copy(searchTemplate = resolved)
            } else {
                page
            }
        }

    suspend fun search(
        source: RemoteSource,
        password: String?,
        template: String,
        query: String
    ): RemotePage = loadPage(
        source = source,
        password = password,
        url = template.replace("{searchTerms}", URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20"))
    )

    /** Size of a format the catalog did not describe, via HEAD. 0 when the server does not say. */
    suspend fun contentLength(source: RemoteSource, password: String?, url: String): Long =
        runInterruptible(Dispatchers.IO) {
            RemoteHttp.contentLength(clientFor(source, password), RemoteHttp.parseUrl(url))
        }

    suspend fun download(
        source: RemoteSource,
        password: String?,
        url: String,
        destination: File,
        isCancelled: () -> Boolean,
        onProgress: (Long, Long) -> Unit
    ): RemoteHttp.Downloaded = runInterruptible(Dispatchers.IO) {
        RemoteHttp.download(clientFor(source, password), RemoteHttp.parseUrl(url), destination, isCancelled, onProgress)
    }

    private companion object {
        const val ACCEPT_FEED =
            "application/atom+xml;profile=opds-catalog, application/atom+xml;q=0.9, application/xml;q=0.8, */*;q=0.5"
        const val ACCEPT_OPENSEARCH = "application/opensearchdescription+xml, application/xml;q=0.9, */*;q=0.5"
        const val MAX_FEED_BYTES = 8L * 1024 * 1024
        const val MAX_OPENSEARCH_BYTES = 256L * 1024
    }
}
