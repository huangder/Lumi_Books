package com.huangder.lumibooks.util.parser

import android.content.Context
import android.net.Uri
import com.huangder.lumibooks.util.BookFileAccess
import com.huangder.lumibooks.util.SeekableBookSource
import com.huangder.lumibooks.util.cache.BookFingerprint
import com.huangder.lumibooks.util.cache.MirrorLifetime
import com.huangder.lumibooks.util.cache.ReaderCacheStore
import com.huangder.lumibooks.util.zip.ZipCompatRepair
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * An opened comic archive: the (possibly mirrored) seekable source plus the page index derived from
 * its central directory. Pages are decoded straight out of the ZIP, so nothing is extracted to disk.
 */
internal class OpenedCbzArchive(
    private val lease: SeekableBookSource?,
    val readablePath: String,
    val zipFile: ZipFile,
    val index: CbzIndex,
    val comicInfo: CbzComicInfo?
) : Closeable {
    constructor(
        lease: SeekableBookSource?,
        zipFile: ZipFile,
        index: CbzIndex,
        comicInfo: CbzComicInfo?
    ) : this(lease, zipFile.name, zipFile, index, comicInfo)

    fun openEntry(entryName: String): InputStream? {
        val entry = zipFile.getEntry(entryName) ?: return null
        return runCatching { zipFile.getInputStream(entry) }.getOrNull()
    }

    /** URI contract used by the vendored SSIV ZIP decoders (ImageSource.zipEntry). */
    fun entryUri(entryName: String): Uri = Uri.fromParts("file+zip", readablePath, entryName)

    override fun close() {
        runCatching { zipFile.close() }
        runCatching { lease?.close() }
    }
}

internal object CbzArchiveOpener {
    private const val EMPTY_ARCHIVE_MESSAGE = "CBZ archive is empty"
    private const val COMIC_INFO_FILE_NAME = "comicinfo.xml"
    private const val MAX_COMIC_INFO_BYTES = 512 * 1024
    private const val INDEX_NAMESPACE = "cbz_index"
    private const val INDEX_VERSION = 1

    fun open(context: Context, location: String): OpenedCbzArchive {
        val lease = BookFileAccess.openSeekable(
            context = context,
            location = location,
            writable = false,
            mirrorLifetime = MirrorLifetime.SESSION
        )
        var zipFile: ZipFile? = null
        return try {
            val readablePath = ZipCompatRepair.prepare(
                context = context,
                sourcePath = lease.path,
                cacheDirectoryName = "cbz_compat",
                cachedExtension = "cbz",
                emptyArchiveMessage = EMPTY_ARCHIVE_MESSAGE
            )
            val opened = ZipFile(readablePath)
            zipFile = opened
            val cacheStore = ReaderCacheStore.get(context)
            val fingerprint = BookFingerprint.resolve(context, location)
            val cached = cacheStore.readMetadata(INDEX_NAMESPACE, fingerprint)?.decodeIndex()
            val index: CbzIndex
            val comicInfo: CbzComicInfo?
            if (cached != null) {
                index = cached.index
                comicInfo = cached.comicInfo
            } else {
                val entryNames = opened.entries().asSequence()
                    .filterNot { it.isDirectory }
                    .map { it.name }
                    .toList()
                index = CbzPageIndex.build(entryNames)
                comicInfo = readComicInfo(opened, entryNames)
                if (index.pages.isNotEmpty()) {
                    cacheStore.writeMetadata(
                        INDEX_NAMESPACE,
                        fingerprint,
                        encodeIndex(index, comicInfo)
                    )
                }
            }
            OpenedCbzArchive(
                lease = lease,
                readablePath = readablePath,
                zipFile = opened,
                index = index,
                comicInfo = comicInfo
            )
        } catch (error: Throwable) {
            runCatching { zipFile?.close() }
            runCatching { lease.close() }
            throw error
        }
    }

    /** The shallowest `ComicInfo.xml` wins, so a root-level sidecar always takes precedence. */
    private fun readComicInfo(zipFile: ZipFile, entryNames: List<String>): CbzComicInfo? {
        val candidate = entryNames
            .filter { it.substringAfterLast('/').equals(COMIC_INFO_FILE_NAME, ignoreCase = true) }
            .minByOrNull { it.count { character -> character == '/' } }
            ?: return null
        val entry = zipFile.getEntry(candidate) ?: return null
        if (entry.size > MAX_COMIC_INFO_BYTES) return null
        val xml = runCatching {
            zipFile.getInputStream(entry).use { input ->
                input.readUpTo(MAX_COMIC_INFO_BYTES).toString(Charsets.UTF_8)
            }
        }.getOrNull() ?: return null
        return CbzComicInfo.parse(xml)
    }

    private fun InputStream.readUpTo(limit: Int): ByteArray {
        val buffer = ByteArray(8192)
        val collected = ByteArrayOutputStream()
        while (collected.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - collected.size()))
            if (read <= 0) break
            collected.write(buffer, 0, read)
        }
        return collected.toByteArray()
    }

    private class CachedIndex(val index: CbzIndex, val comicInfo: CbzComicInfo?)

    private fun encodeIndex(index: CbzIndex, comicInfo: CbzComicInfo?): JSONObject {
        val pages = JSONArray()
        index.pages.forEach { page ->
            pages.put(
                JSONArray()
                    .put(page.entryName)
                    .put(page.chapterIndex)
                    .put(page.pageInChapter)
            )
        }
        val chapterDirectory = JSONArray()
        val chapterPageCounts = JSONArray()
        index.chapters.forEach { chapter ->
            chapterDirectory.put(chapter.directory ?: JSONObject.NULL)
            chapterPageCounts.put(chapter.pageCount)
        }
        return JSONObject()
            .put("version", INDEX_VERSION)
            .put("pages", pages)
            .put("chapterDirectories", chapterDirectory)
            .put("chapterPageCounts", chapterPageCounts)
            .put("comicInfo", comicInfo?.let(::encodeComicInfo) ?: JSONObject.NULL)
    }

    private fun encodeComicInfo(info: CbzComicInfo): JSONObject = JSONObject()
        .putNullable("title", info.title)
        .putNullable("series", info.series)
        .putNullable("number", info.number)
        .putNullable("volume", info.volume)
        .putNullable("writer", info.writer)
        .putNullable("penciller", info.penciller)
        .putNullable("author", info.author)
        .putNullable("summary", info.summary)
        .putNullable("manga", info.manga)
        .putNullable("pageCount", info.pageCount)
        .putNullable("language", info.language)

    private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
        put(name, value ?: JSONObject.NULL)

    private fun JSONObject.decodeIndex(): CachedIndex? {
        if (optInt("version") != INDEX_VERSION) return null
        val pagesJson = optJSONArray("pages") ?: return null
        val directoriesJson = optJSONArray("chapterDirectories") ?: return null
        val pageCountsJson = optJSONArray("chapterPageCounts") ?: return null
        if (directoriesJson.length() != pageCountsJson.length()) return null

        val pages = ArrayList<CbzPage>(pagesJson.length())
        for (index in 0 until pagesJson.length()) {
            val page = pagesJson.optJSONArray(index) ?: return null
            val entryName = page.optString(0, "")
            if (entryName.isEmpty()) return null
            pages += CbzPage(
                entryName = entryName,
                chapterIndex = page.optInt(1, -1),
                pageInChapter = page.optInt(2, 0)
            )
        }
        if (pages.isEmpty()) return null

        val chapters = ArrayList<CbzChapter>(directoriesJson.length())
        var firstPageIndex = 0
        for (index in 0 until directoriesJson.length()) {
            val pageCount = pageCountsJson.optInt(index, 0)
            chapters += CbzChapter(
                directory = if (directoriesJson.isNull(index)) null
                else directoriesJson.optString(index).takeIf(String::isNotEmpty),
                firstPageIndex = firstPageIndex,
                pageCount = pageCount
            )
            firstPageIndex += pageCount
        }
        if (firstPageIndex != pages.size) return null
        return CachedIndex(
            index = CbzIndex(pages = pages, chapters = chapters),
            comicInfo = decodeComicInfo(optJSONObject("comicInfo"))
        )
    }

    private fun decodeComicInfo(json: JSONObject?): CbzComicInfo? {
        if (json == null) return null
        fun text(name: String): String? =
            if (json.isNull(name)) null else json.optString(name).takeIf(String::isNotEmpty)
        val info = CbzComicInfo(
            title = text("title"),
            series = text("series"),
            number = text("number"),
            volume = text("volume"),
            writer = text("writer"),
            penciller = text("penciller"),
            author = text("author"),
            summary = text("summary"),
            manga = text("manga"),
            pageCount = if (json.isNull("pageCount")) null else json.optInt("pageCount"),
            language = text("language")
        )
        return info.takeIf { it != CbzComicInfo() }
    }
}
