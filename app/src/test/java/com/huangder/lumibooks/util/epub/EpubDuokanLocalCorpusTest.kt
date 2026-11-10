package com.huangder.lumibooks.util.epub

import android.app.Application
import android.net.Uri
import java.io.File
import java.util.zip.ZipFile
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Opt-in private corpus; book text and assets stay outside version control. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EpubDuokanLocalCorpusTest {
    @Test fun localBooksUseTheRealSessionAndKeepSourceCoordinates() {
        val inputs = System.getenv("LUMI_DUOKAN_TEST_BOOKS").orEmpty()
            .split(File.pathSeparator).filter(String::isNotBlank)
        assumeTrue("Set LUMI_DUOKAN_TEST_BOOKS to a local EPUB corpus", inputs.isNotEmpty())
        val export = System.getenv("LUMI_DUOKAN_EXPORT_DIR")?.let(::File)
        inputs.forEachIndexed { bookIndex, path ->
            EpubRenderSession.open(path).use { session ->
                ZipFile(path).use { zip ->
                    val selected = linkedSetOf(0)
                    var avatarChapter: Int? = null
                    var floatChapter: Int? = null
                    var repairChapter: Int? = null
                    var repairedReferences = 0
                    var noteReferences = 0
                    var transformedNotes = 0
                    for (index in 0 until session.chapterCount) {
                        val href = session.chapterHref(index).substringBefore('#')
                        val entry = zip.getEntry(href) ?: continue
                        val raw = zip.getInputStream(entry).use { it.readBytes() }
                        val source = Jsoup.parse(raw.toString(Charsets.UTF_8), "", Parser.xmlParser())
                        if (avatarChapter == null && source.selectFirst("img.photo") != null) avatarChapter = index
                        if (floatChapter == null && source.selectFirst(".DKimg-right") != null) floatChapter = index
                        source.select("img[src]").forEach { image ->
                            val target = EpubPathResolver.resolve(href, image.attr("src"))
                            if (target != null && zip.getEntry(target) == null) {
                                val resource = session.read(target)
                                if (resource != null && resource.path != target && resource.mediaType.startsWith("image/")) {
                                    repairedReferences++
                                    if (repairChapter == null) repairChapter = index
                                }
                            }
                        }
                        val notes = source.select("a.duokan-footnote[href]").size
                        if (notes > 0) { selected += index; noteReferences += notes }
                    }
                    avatarChapter?.let(selected::add)
                    floatChapter?.let(selected::add)
                    repairChapter?.let(selected::add)
                    val directory = export?.resolve("book-$bookIndex")
                    if (directory != null) {
                        val content = directory.resolve("content").toPath().toAbsolutePath().normalize()
                        zip.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                            val target = content.resolve(entry.name).normalize()
                            require(target.startsWith(content)) { "Unsafe corpus entry" }
                            target.parent.toFile().mkdirs()
                            zip.getInputStream(entry).use { input -> target.toFile().outputStream().use(input::copyTo) }
                        }
                    }
                    val manifest = mutableListOf<String>()
                    selected.forEach { index ->
                        val response = requireNotNull(session.assetLoader.shouldInterceptRequest(Uri.parse(session.chapterUrl(index))))
                        val bytes = response.data.use { it.readBytes() }
                        val document = Jsoup.parse(bytes.toString(Charsets.UTF_8), "", Parser.xmlParser())
                        assertEquals(session.searchText(index), EpubDocumentTransformer.extractSearchText(
                            EpubResource("chapter.xhtml", "application/xhtml+xml", bytes)))
                        assertEquals(session.imageTextOffsets(index), EpubDocumentTransformer.imageTextOffsets(
                            EpubResource("chapter.xhtml", "application/xhtml+xml", bytes)))
                        if (index == avatarChapter) {
                            assertTrue(document.select("img.photo").isNotEmpty())
                            assertTrue(document.select("img.photo").all { it.attr("data-lumi-inline-media") == "true" })
                        }
                        if (index == floatChapter) {
                            assertTrue(document.select(".DKimg-right[data-lumi-floated-media=true]").isNotEmpty())
                            assertTrue(document.select(".DKimg-right img[data-lumi-bleed]").isEmpty())
                        }
                        document.select("a.duokan-footnote[epub:type=noteref] img[width=100%]:not(.duokan-footnote)").forEach { image ->
                            assertEquals("true", image.attr("data-lumi-footnote-illustration"))
                            assertFalse(image.hasAttr("data-lumi-footnote-marker"))
                            assertFalse(image.hasAttr("data-lumi-inline-media"))
                        }
                        transformedNotes += document.select("a.duokan-footnote[href]").size
                        val hint = session.pagePresentation(index).duokanPage
                        if (hint != null) assertEquals(hint.property, document.body().attr("data-lumi-duokan-page"))
                        if (directory != null) {
                            val href = session.chapterHref(index).substringBefore('#')
                            document.select("img[src]").forEach imageLoop@ { image ->
                                val targetPath = EpubPathResolver.resolve(href, image.attr("src")) ?: return@imageLoop
                                val resource = session.read(targetPath) ?: return@imageLoop
                                val target = directory.resolve("content/$targetPath")
                                if (!target.exists() && resource.path != targetPath && resource.mediaType.startsWith("image/")) {
                                    requireNotNull(target.parentFile).mkdirs()
                                    target.writeBytes(resource.bytes)
                                }
                            }
                            document.select("base").remove()
                            document.head().prependElement("base").attr("href", directory.resolve("content/$href").toURI().toString())
                            val target = directory.resolve("chapter-$index.html")
                            target.writeText(document.outerHtml())
                            manifest += target.absolutePath
                        }
                    }
                    assertEquals(noteReferences, transformedNotes)
                    directory?.resolve("chapters.txt")?.writeText(manifest.joinToString("\n"))
                    println("Duokan corpus book=$bookIndex chapters=${session.chapterCount} checked=${selected.size} linkedNotes=$transformedNotes repairedImages=$repairedReferences avatar=${avatarChapter != null} float=${floatChapter != null}")
                }
            }
        }
    }
}
