package com.huangder.lumibooks.ui.reader

import android.content.Context
import android.os.Build
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.util.DownloadedFonts
import com.huangder.lumibooks.util.cache.BookFingerprint
import com.huangder.lumibooks.util.cache.ReaderCacheStore
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Small pagination artifacts share the reader cache's atomic writes and clear generation. */
internal class ReaderPageIndexCache private constructor(
    private val store: ReaderCacheStore,
    private val fingerprint: BookFingerprint,
    private val namespace: String,
    private val cacheGeneration: Long,
    private val chapterCount: Int,
    val restored: ReaderBookPages?
) {
    // This scope outlives the UI long enough to commit the last snapshot on close.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val writeLock = Any()
    private var pending: ReaderBookPages? = null
    private var writer: Job? = null
    private var closed = false

    fun submit(pages: ReaderBookPages) = synchronized(lock) {
        if (closed || pages.chapterCounts.size != chapterCount ||
            pages.chapterCounts.all { it == null } && pages.chapterStarts.isEmpty()) return@synchronized
        pending = pages.copy(generation = 0)
        if (pages.totalPages != null) scheduleWrite(immediate = true)
        else if (writer?.isActive != true) scheduleWrite(immediate = false)
    }

    fun flush() = synchronized(lock) { if (!closed) scheduleWrite(immediate = true) }

    fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        writer?.cancel()
        writer = scope.launch { commit(); scope.cancel() }
    }

    internal suspend fun awaitWrites() { synchronized(lock) { writer }?.join() }

    private fun scheduleWrite(immediate: Boolean) {
        writer?.cancel()
        writer = scope.launch {
            if (!immediate) delay(400)
            commit()
        }
    }

    private fun commit() {
        // Keep snapshot selection and disk commit serial. An older delayed write cannot
        // overwrite a newer complete snapshot, including during reader disposal.
        synchronized(writeLock) {
            val pages = synchronized(lock) { pending } ?: return
            synchronized(store) {
                if (store.currentGeneration() != cacheGeneration) return
                val disk = decode(store.readMetadata(namespace, fingerprint), chapterCount)
                store.writeMetadata(namespace, fingerprint, encode(mergeReaderBookPages(disk, pages)))
            }
            synchronized(lock) { if (pending === pages) pending = null }
        }
    }

    companion object {
        // Increment whenever pagination, character coordinates, or the persisted schema changes.
        internal const val ENGINE_VERSION = 1

        suspend fun open(context: Context, book: Book, signature: String, chapterCount: Int): ReaderPageIndexCache =
            withContext(Dispatchers.IO) {
                val store = ReaderCacheStore.get(context)
                var fingerprint = BookFingerprint.resolve(context, book.filePath)
                if (!fingerprint.reliable) {
                    // Providers without timestamps cannot safely reuse their metadata fingerprint.
                    val digest = MessageDigest.getInstance("SHA-256")
                    val hash = runCatching {
                        context.contentResolver.openInputStream(android.net.Uri.parse(book.filePath))?.use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                digest.update(buffer, 0, read)
                            }
                            digest.digest().joinToString("") { "%02x".format(it) }
                        }
                    }.getOrNull()
                    if (hash != null) fingerprint = fingerprint.copy(identity = fingerprint.identity + ":" + hash, reliable = true)
                }
                val namespace = "page_index_v${ENGINE_VERSION}_" + paginationHash(signature)
                synchronized(store) {
                    val generation = store.currentGeneration()
                    val payload = store.readMetadata(namespace, fingerprint)
                    val restored = decode(payload, chapterCount)
                    if (payload != null && restored == null) store.deleteMetadata(namespace, fingerprint)
                    ReaderPageIndexCache(store, fingerprint, namespace, generation, chapterCount, restored)
                }
            }

        internal fun encode(pages: ReaderBookPages): JSONObject = JSONObject()
            .put("counts", JSONArray().apply { pages.chapterCounts.forEach { put(it ?: JSONObject.NULL) } })
            .put("starts", JSONObject().apply {
                pages.chapterStarts.forEach { (chapter, starts) -> put(chapter.toString(), JSONArray(starts)) }
            })

        internal fun decode(json: JSONObject?, chapterCount: Int): ReaderBookPages? = runCatching {
            if (json == null || chapterCount <= 0) return null
            val counts = json.getJSONArray("counts")
            require(counts.length() == chapterCount)
            val parsed = List(chapterCount) { index ->
                if (counts.isNull(index)) null else counts.getInt(index).also { require(it >= 0) }
            }
            require(parsed.filterNotNull().sumOf { it.toLong() } <= Int.MAX_VALUE)
            val starts = json.getJSONObject("starts")
            val boundaries = starts.keys().asSequence().associate { key ->
                val chapter = key.toInt()
                require(chapter in parsed.indices)
                val array = starts.getJSONArray(key)
                val offsets = List(array.length()) { array.getInt(it) }
                require(offsets.all { it >= 0 } && offsets.zipWithNext().all { (a, b) -> a < b })
                require(offsets.isEmpty() || offsets.first() == 0)
                parsed[chapter]?.let { require(offsets.size == it) }
                chapter to offsets
            }
            ReaderBookPages(chapterCounts = parsed, chapterStarts = boundaries)
        }.getOrNull()
    }
}

/** Foreground results take precedence; restore never drops newly confirmed chapters. */
internal fun mergeReaderBookPages(cached: ReaderBookPages?, current: ReaderBookPages): ReaderBookPages {
    if (cached == null || cached.chapterCounts.size != current.chapterCounts.size) return current
    if (current.chapterCounts.indices.any { chapter ->
            val fresh = current.chapterCounts[chapter]
            val saved = cached.chapterCounts[chapter]
            fresh != null && saved != null && (fresh != saved ||
                current.chapterStarts[chapter]?.let { it != cached.chapterStarts[chapter] && cached.chapterStarts[chapter] != null } == true)
        }) return current
    return current.copy(
        chapterCounts = current.chapterCounts.mapIndexed { index, count -> count ?: cached.chapterCounts[index] },
        chapterStarts = (cached.chapterStarts.keys + current.chapterStarts.keys).associateWith { chapter ->
            val fresh = current.chapterStarts[chapter]
            val saved = cached.chapterStarts[chapter]
            when {
                current.chapterCounts[chapter] != null -> fresh ?: saved.orEmpty()
                cached.chapterCounts[chapter] != null -> saved.orEmpty()
                fresh == null -> saved.orEmpty()
                saved == null -> fresh
                saved.size > fresh.size && saved.take(fresh.size) == fresh -> saved
                else -> fresh
            }
        }.filterValues { it.isNotEmpty() }
    )
}

internal fun paginationHash(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** File-backed fonts use stable file metadata, never a process-local Typeface identity. */
internal fun readerPaginationFontKey(context: Context, fontType: String, path: String?, weight: Int): String {
    val file = when {
        fontType.startsWith("custom") -> path?.let(::File)
        fontType == "fangsong" -> DownloadedFonts.file(context, "fangsong")
        fontType == "serif" -> null // Bundled resources are covered by the pagination engine version.
        else -> null
    }
    return listOf(fontType, weight.coerceIn(100, 900), file?.absolutePath,
        file?.isFile, file?.length(), file?.lastModified(), Build.FINGERPRINT).joinToString("|")
}

/** Display-only changes deliberately do not enter the pagination identity. */
internal fun ReaderUiState.paginationContentKey(): String = listOf(
    book?.format, chapterCount, chapterTitles, txtActiveCharsetName, txtTocRuleId,
    chineseMode, optimizeLayout, useEpubCss, renderMode, fontSize, firstLineIndent,
    paragraphSpacing, bionicReadingEnabled, readerWritingMode, textAlignment
).joinToString("|")
