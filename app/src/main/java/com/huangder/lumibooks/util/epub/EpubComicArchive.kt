package com.huangder.lumibooks.util.epub

import android.content.Context
import com.huangder.lumibooks.util.cache.BookFingerprint
import com.huangder.lumibooks.util.cache.ReaderCacheStore
import com.huangder.lumibooks.util.parser.CbzChapter
import com.huangder.lumibooks.util.parser.CbzIndex
import com.huangder.lumibooks.util.parser.CbzPage
import com.huangder.lumibooks.util.parser.OpenedCbzArchive
import com.huangder.lumibooks.util.zip.ZipCompatRepair
import com.huangder.lumibooks.util.kindle.ReadableEpubSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.zip.ZipFile

internal object EpubComicArchive {
    suspend fun index(context: Context, location: String): EpubComicIndex {
        val coroutine = currentCoroutineContext()
        val store = ReaderCacheStore.get(context)
        val namespace = "epub_comic_index_v${EpubComicIndex.VERSION}"
        return withArchive(context, location) { path, zip ->
            // A reconstructed publication's path contains its content hash and converter version.
            val fingerprint = BookFingerprint.resolve(context, path)
            store.readMetadata(namespace, fingerprint)?.toString()?.let(EpubComicIndex::decode)
                ?.let { return@withArchive it }
            val entries = zip.entries().asSequence().associateBy { it.name.lowercase() }
            val index = EpubComicIndexer.build(EpubPackageReader.read(path), { resource ->
                val entry = zip.getEntry(resource) ?: entries[resource.lowercase()]
                entry?.takeUnless { it.isDirectory }?.let {
                    require(it.size <= 64L * 1024 * 1024) { "EPUB resource too large" }
                    zip.getInputStream(it).use { stream ->
                        val bytes = stream.takeBoundedBytes(64 * 1024 * 1024)
                        require(bytes.size <= 64 * 1024 * 1024)
                        bytes
                    }
                }
            }, { coroutine.ensureActive() })
            coroutine.ensureActive()
            store.writeMetadata(namespace, fingerprint, org.json.JSONObject(index.encode()))
            index
        }
    }

    fun open(context: Context, location: String, index: EpubComicIndex): OpenedCbzArchive {
        val lease = ReadableEpubSource.open(context, location)
        var zip: ZipFile? = null
        try {
            val path = compatible(context, lease.path)
            val opened = ZipFile(path).also { zip = it }
            val entries = opened.entries().asSequence().associateBy { it.name.lowercase() }
            val groups = index.pages.groupBy { it.chapterIndex }
            val pageInChapter = mutableMapOf<Int, Int>()
            val comicPages = index.pages.map { page ->
                val ordinal = pageInChapter[page.chapterIndex] ?: 0
                pageInChapter[page.chapterIndex] = ordinal + 1
                CbzPage(entries[page.imagePath.lowercase()]?.name ?: page.imagePath,
                    page.chapterIndex, ordinal)
            }
            val chapters = groups.map { (chapter, pages) ->
                CbzChapter(index.chapterTitles.getOrNull(chapter), index.pages.indexOf(pages.first()), pages.size)
            }
            return OpenedCbzArchive(lease, path, opened, CbzIndex(comicPages, chapters), null)
        } catch (error: Throwable) {
            zip?.close()
            lease.close()
            throw error
        }
    }

    private fun compatible(context: Context, path: String): String = ZipCompatRepair.prepare(
        context, path, cacheDirectoryName = "epub_comic_compat", cachedExtension = "epub",
        emptyArchiveMessage = "EPUB archive is empty")

    private fun <T> withArchive(context: Context, location: String, read: (String, ZipFile) -> T): T =
        ReadableEpubSource.open(context, location).use { lease ->
            val path = compatible(context, lease.path)
            ZipFile(path).use { read(path, it) }
        }
}

private fun java.io.InputStream.takeBoundedBytes(limit: Int): ByteArray {
    val result = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(result.size().toLong() + count <= limit) { "EPUB resource too large" }
        result.write(buffer, 0, count)
    }
    return result.toByteArray()
}
