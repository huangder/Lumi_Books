package com.huangder.lumibooks.util.parser

import android.content.Context
import com.huangder.lumibooks.util.epub.BookSearchSource
import com.huangder.lumibooks.util.epub.EpubSearchMatch
import com.huangder.lumibooks.util.epub.EpubLocator
import com.huangder.lumibooks.util.markdown.MarkdownSnapshotStore
import com.huangder.lumibooks.util.markdown.native.*
import java.io.*
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Markdown reading never constructs an EPUB, DOM, HTML or a document-sized Spannable. */
class MarkdownParser(private val context: Context? = null) : BookParser, BookSearchSource {
    var document: MarkdownDocument? = null
        private set
    var blockSource: MarkdownBlockSource? = null
        private set
    val searchText: String get() = document?.text.orEmpty()
    // Built once on the parser worker, never allocate an outline-sized map on scroll.
    var headingOffsets: Map<String, Int> = emptyMap()
        private set
    private var readingHeadings: List<MarkdownOutline> = emptyList()

    fun headingAnchorAt(offset: Int): String? {
        var low = 0
        var high = readingHeadings.lastIndex
        while (low <= high) {
            val mid = (low + high).ushr(1)
            if (readingHeadings[mid].offset <= offset) low = mid + 1 else high = mid - 1
        }
        return readingHeadings.getOrNull(high)?.anchor
    }
    override var paragraphSpacingDp = 0f
    override var firstLineIndentChars = 0f
    override var contentWidth = 0
    override var contentHeight = 0
    override var useEpubCss = false
    override var preserveEpubBackground = false

    override fun parse(filePath: String): BookContent {
        close()
        val openedAt = System.nanoTime()
        val source = File(filePath)
        require(source.isFile && source.length() <= 64L * 1024 * 1024) { "Markdown snapshot is missing or too large" }
        val metadata = MarkdownSnapshotStore.manifest(source)
        val key = MarkdownSnapshotStore.sha256(source) + "-native-${MarkdownNativeParser.CACHE_VERSION}"
        val cacheRoot = File(context?.cacheDir ?: source.parentFile, "markdown_native").apply { mkdirs() }
        val cache = File(cacheRoot, "$key.bin")
        val cached = runCatching {
            if (!cache.isFile) null else ObjectInputStream(cache.inputStream().buffered()).use { it.readObject() as MarkdownDocument }
        }.getOrNull()
        val parsed = cached ?: MarkdownNativeParser.parse(source.readText(Charsets.UTF_8)).also { value ->
            if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
            val stage = File(cacheRoot, "$key-${UUID.randomUUID()}.tmp")
            try {
                ObjectOutputStream(stage.outputStream().buffered()).use { it.writeObject(value) }
                java.nio.file.Files.move(stage.toPath(), cache.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } finally { stage.delete() }
        }
        if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
        android.util.Log.i("MarkdownNative", "parse warm=${cached != null} bytes=${source.length()} blocks=${parsed.blocks.size} ms=${(System.nanoTime() - openedAt) / 1_000_000}")
        document = parsed
        headingOffsets = parsed.outline.associate { it.anchor to it.offset }
        readingHeadings = parsed.outline.filter { it.level in 1..6 }
        blockSource = context?.let { MarkwonBlockSource(it.applicationContext, parsed, MarkdownSnapshotStore.assets(source)) }
        val title = parsed.title ?: metadata?.optString("title")?.takeIf(String::isNotBlank) ?: source.nameWithoutExtension
        val author = parsed.author ?: metadata?.optString("author").orEmpty()
        return BookContent(title, author, listOf(Chapter(0, title, "")), tocEntries = parsed.toc.filter { it.level in 1..6 })
    }
    // Plain text is for TTS and compatibility consumers only. The reader uses blockSource.
    override fun getChapterContent(chapterIndex: Int): CharSequence = if (chapterIndex == 0) searchText else ""
    override fun getChapterHtml(chapterIndex: Int, optimizeLayout: Boolean): String = ""
    override fun getChapterCount() = if (document != null) 1 else 0
    override fun resolveLink(sourceChapterIndex: Int, href: String): BookLinkTarget? =
        if (href.startsWith('#')) headingOffsets[href.removePrefix("#")]?.let { BookLinkTarget(0, it) } else null
    override fun isFootnoteHref(chapterIndex: Int, href: String) = href.startsWith("#footnote-")
    override fun resolveFootnoteText(sourceChapterIndex: Int, href: String): String? =
        resolveLink(0, href)?.let { target -> document?.let { it.blocks[it.index.blockAt(target.characterOffset)].text } }
    override suspend fun searchBook(query: String, maxResults: Int): List<EpubSearchMatch> {
        if (query.isBlank() || maxResults <= 0) return emptyList()
        val text = searchText
        val results = mutableListOf<EpubSearchMatch>()
        var from = 0
        while (results.size < maxResults) {
            currentCoroutineContext().ensureActive()
            val at = text.indexOf(query, from, true)
            if (at < 0) break
            val end = at + query.length
            results.add(EpubSearchMatch(0, at, query.length,
                text.substring(maxOf(0, at - 24), minOf(text.length, end + 40)),
                EpubLocator(version = 3, href = "document.md", textPosition = at, textLength = text.length,
                    exact = text.substring(at, end), prefix = text.substring(maxOf(0, at - 32), at),
                    suffix = text.substring(end, minOf(text.length, end + 32)), progression = at.toFloat() / text.length.coerceAtLeast(1))))
            from = end
        }
        return results
    }
    override fun close() {
        blockSource?.close(); blockSource = null; document = null
        headingOffsets = emptyMap(); readingHeadings = emptyList()
    }
}
