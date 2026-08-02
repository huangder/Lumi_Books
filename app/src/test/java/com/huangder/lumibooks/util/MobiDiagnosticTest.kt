package com.huangder.lumibooks.util

import com.huangder.lumibooks.util.epub.EpubDocumentTransformer
import com.huangder.lumibooks.util.epub.EpubRenditionLayout
import com.huangder.lumibooks.util.epub.MobiRawml
import com.huangder.lumibooks.util.parser.MobiParser
import org.junit.Test
import java.io.File

class MobiDiagnosticTest {

    @Test
    fun diagnoseRealFile() {
        val path = "E:/Desktop/哈利.波特(珍藏版)(全七册).mobi"
        if (!File(path).exists()) {
            println("SKIP: file not present")
            return
        }
        val parser = MobiParser(null)
        val content = parser.parse(path)
        println("TITLE=${content.title}")
        println("AUTHOR=${content.author}")
        println("CHAPTERS=${content.chapters.size}")
        content.chapters.take(8).forEach { println("  ch${it.index}: ${it.title}") }

        val session = parser.openRenderSession()
        try {
            println("SESSION_CHAPTERS=${session.chapterCount}")
            val rawml = parser.sessionRawml
            val charset = parser.sessionCharset
            var failures = 0
            var emptyDocuments = 0
            var firstFailure = -1
            for (index in 0 until minOf(3, session.chapterCount)) {
                val range = parser.sessionChapterRanges[index]
                val fragment = MobiRawml.chapterFragment(
                    rawml, range, index, charset,
                    resolveImage = { "recindex:$it" },
                    resolveLink = { "filepos:$it" }
                )
                val full =
                    "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/></head><body>$fragment</body></html>"
                val document = EpubDocumentTransformer.parseAndSanitize(
                    full.toByteArray(Charsets.UTF_8),
                    "chapter-${index.toString().padStart(3, '0')}.html",
                    useXmlParser = false
                )
                val html = String(
                    EpubDocumentTransformer.transform(document, EpubRenditionLayout.REFLOWABLE),
                    Charsets.UTF_8
                )
                if (index == 1) {
                    java.io.File("E:/Desktop/diag_ch1_xhtml.xml").writeText(html, Charsets.UTF_8)
                }
                val namedEntities = Regex("&(?!amp;|lt;|gt;|quot;|apos;|#)[a-zA-Z]+;")
                    .findAll(html).map { it.value }.distinct().toList()
                println("CH$index fragmentLen=${fragment.length} htmlLen=${html.length} namedEntities=$namedEntities")
                println("CH$index htmlHead=" + html.take(600).replace("\n", " "))
                if (index == 1) {
                    println("CH1_FRAGMENT=" + fragment.take(1200))
                    // Write the raw chapter bytes so we can inspect them with a real editor.
                    val rawSlice = rawml.copyOfRange(range.first, minOf(range.second, range.first + 2048))
                    java.io.File("E:/Desktop/diag_ch1_raw.bin").writeBytes(rawSlice)
                    java.io.File("E:/Desktop/diag_ch1_decoded.txt").writeText(
                        String(rawSlice, Charsets.UTF_8),
                        Charsets.UTF_8
                    )
                    java.io.File("E:/Desktop/diag_ch1_fragment.txt").writeText(fragment.take(2000), Charsets.UTF_8)
                }
            }
            // Full sweep: every chapter must produce a non-empty transformed document.
            for (index in 0 until session.chapterCount) {
                try {
                    val range = parser.sessionChapterRanges[index]
                    val fragment = MobiRawml.chapterFragment(
                        rawml, range, index, charset,
                        resolveImage = { "recindex:$it" },
                        resolveLink = { "filepos:$it" }
                    )
                    val full =
                        "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/></head><body>$fragment</body></html>"
                    val document = EpubDocumentTransformer.parseAndSanitize(
                        full.toByteArray(Charsets.UTF_8),
                        "chapter-${index.toString().padStart(3, '0')}.html",
                        useXmlParser = false
                    )
                    val html = String(
                        EpubDocumentTransformer.transform(document, EpubRenditionLayout.REFLOWABLE),
                        Charsets.UTF_8
                    )
                    if (html.length < 200) {
                        emptyDocuments++
                        if (firstFailure < 0) firstFailure = index
                    }
                } catch (error: Throwable) {
                    failures++
                    if (firstFailure < 0) firstFailure = index
                    println("SWEEP_FAIL ch$index: ${error.javaClass.simpleName}: ${error.message}")
                }
            }
            println("SWEEP failures=$failures emptyDocs=$emptyDocuments firstProblem=$firstFailure total=${session.chapterCount}")
        } finally {
            session.close()
        }
        parser.close()
    }

    @Test
    fun findGarbledChapter() {
        val path = "E:/Desktop/哈利.波特(珍藏版)(全七册).mobi"
        if (!File(path).exists()) {
            println("SKIP: file not present")
            return
        }
        val parser = MobiParser(null)
        try {
            parser.parse(path)
            val rawml = parser.sessionRawml
            val charset = parser.sessionCharset
            val strict = java.nio.charset.Charset.forName("UTF-8")
            val decoder = strict.newDecoder()
            try {
                decoder.decode(java.nio.ByteBuffer.wrap(rawml))
                println("RAWML_UTF8=strict-valid")
            } catch (error: Throwable) {
                println("RAWML_UTF8=INVALID: ${error.javaClass.simpleName} ${error.message}")
            }
            parser.sessionChapterRanges.forEachIndexed { index, range ->
                val fragment = MobiRawml.chapterFragment(
                    rawml, range, index, charset,
                    resolveImage = { "recindex:$it" },
                    resolveLink = { "filepos:$it" }
                )
                if (fragment.contains("蜘蛛") || fragment.contains("那里")) {
                    val marker = fragment.indexOf("y\"")
                    println("CH$index len=${fragment.length} hasTagFrag=${fragment.contains("\">")} yQuote=$marker")
                    if (index == 6) {
                        val needle = "而他就睡".toByteArray(Charsets.UTF_8)
                        var pos = -1
                        outer@ for (i in range.first..range.second - needle.size) {
                            for (j in needle.indices) {
                                if (rawml[i + j] != needle[j]) continue@outer
                            }
                            pos = i
                            break
                        }
                        if (pos >= 0) {
                            val slice = rawml.copyOfRange(pos, minOf(pos + 160, range.second))
                            println("CH6_RAWHEX=" + slice.joinToString(" ") { "%02X".format(it) })
                            println("CH6_RAWSTR=" + String(slice, Charsets.UTF_8))
                        } else {
                            println("CH6 needle not found in raw bytes")
                        }
                    }
                    if (index in 5..8) {
                        java.io.File("E:/Desktop/diag_ch$index.txt").writeText(fragment, Charsets.UTF_8)
                    }
                }
            }
        } finally {
            parser.close()
        }
    }

    @Test
    fun analyzeParagraphStructure() {
        val path = "E:/Desktop/哈利.波特(珍藏版)(全七册).mobi"
        if (!File(path).exists()) {
            println("SKIP: file not present")
            return
        }
        val parser = MobiParser(null)
        try {
            parser.parse(path)
            for (chapter in intArrayOf(0, 1, 6, 7)) {
                val range = parser.sessionChapterRanges[chapter]
                val fragment = MobiRawml.chapterFragment(
                    parser.sessionRawml, range, chapter, parser.sessionCharset,
                    resolveImage = { "recindex:$it" },
                    resolveLink = { "filepos:$it" }
                )
                val pTags = Regex("<p\\b", RegexOption.IGNORE_CASE).findAll(fragment).count()
                val brTags = Regex("<br\\b", RegexOption.IGNORE_CASE).findAll(fragment).count()
                val brAfterP = Regex("</p>\\s*<br\\b", RegexOption.IGNORE_CASE).findAll(fragment).count()
                println(
                    "CH$chapter len=${fragment.length} pTags=$pTags brTags=$brTags brAfterP=$brAfterP"
                )
            }
        } finally {
            parser.close()
        }
    }
}
