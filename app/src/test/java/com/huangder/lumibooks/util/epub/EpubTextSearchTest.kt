package com.huangder.lumibooks.util.epub

import com.huangder.lumibooks.util.parser.EpubParser
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubTextSearchTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun extractsOnlyReaderVisibleDocumentTextNodes() {
        val text = EpubDocumentTransformer.extractSearchText(
            EpubResource(
                path = "OPS/chapter.xhtml",
                mediaType = "application/xhtml+xml",
                bytes = (
                    "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>" +
                        "<style>.needle{color:red}</style><script>script needle</script></head>" +
                        "<body><p>Alpha <b>Beta</b></p><noscript><span>hidden needle</span></noscript>" +
                        "<img src=\"missing.jpg\" alt=\"image needle\"/></body></html>"
                    ).toByteArray()
            )
        )

        assertTrue(text.contains("Alpha Beta"))
        assertFalse(text.contains("script needle"))
        assertFalse(text.contains("hidden needle"))
        assertFalse(text.contains("image needle"))
    }

    @Test
    fun findsCaseInsensitiveTextAcrossFormattingAndBuildsStableQuotes() = runBlocking {
        val file = temporaryFolder.newFile("search.epub")
        writeEpub(
            file,
            searchableEpubEntries(
                listOf(
                    "<p>First alpha <b>Beta</b> ending.</p>" +
                        "<p>Second alpha <i>beta</i> ending.</p>" +
                        "<img src=\"missing.jpg\"/>"
                )
            )
        )
        val epubPackage = EpubPackageReader.read(file.absolutePath)

        val results = EpubTextSearch.search(file.absolutePath, epubPackage, "ALPHA BETA")

        assertEquals(2, results.size)
        assertEquals(0, results.first().chapterIndex)
        assertTrue(results.first().locator.exact.contains("alpha", ignoreCase = true))
        assertTrue(results.first().locator.prefix.contains("First"))
        assertTrue(results.last().locator.prefix.contains("Second"))
        assertTrue(results.first().locator.textLength > results.first().locator.textPosition)
        assertTrue(results.first().locator.progression < results.last().locator.progression)
    }

    @Test
    fun scansPastLegacyTwoHundredChapterBoundary() = runBlocking {
        val file = temporaryFolder.newFile("long.epub")
        val chapters = List(205) { index ->
            if (index == 204) "<p>late boundary result</p>" else "<p>chapter $index</p>"
        }
        writeEpub(file, searchableEpubEntries(chapters))
        val epubPackage = EpubPackageReader.read(file.absolutePath)

        val result = EpubTextSearch.search(file.absolutePath, epubPackage, "late boundary result")

        assertEquals(1, result.size)
        assertEquals(204, result.single().chapterIndex)
    }

    @Test
    fun capsResultsAndCanBeCancelled() = runBlocking {
        val file = temporaryFolder.newFile("many.epub")
        writeEpub(
            file,
            searchableEpubEntries(
                listOf("<p>${List(250) { "hit" }.joinToString(" ")}</p>")
            )
        )
        val epubPackage = EpubPackageReader.read(file.absolutePath)
        assertEquals(200, EpubTextSearch.search(file.absolutePath, epubPackage, "hit", 500).size)

        val job = launch {
            EpubTextSearch.search(file.absolutePath, epubPackage, "hit")
        }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    @Test
    fun skipsMissingAndOversizedChaptersWithoutStoppingTheScan() = runBlocking {
        val missingFile = temporaryFolder.newFile("missing.epub")
        val missingEntries = searchableEpubEntries(
            listOf("<p>missing needle</p>", "<p>survivor needle</p>")
        ).toMutableMap().apply { remove("OPS/Text/c0.xhtml") }
        writeEpub(missingFile, missingEntries)
        val missingPackage = EpubPackageReader.read(missingFile.absolutePath)
        assertEquals(
            1,
            EpubTextSearch.search(missingFile.absolutePath, missingPackage, "survivor needle")
                .single().chapterIndex
        )

        val oversizedFile = temporaryFolder.newFile("oversized.epub")
        val oversizedEntries = searchableEpubEntries(
            listOf("<p>oversized needle</p>", "<p>after limit needle</p>")
        )
        writeEpubWithOversizedEntry(oversizedFile, oversizedEntries, "OPS/Text/c0.xhtml")
        val oversizedPackage = EpubPackageReader.read(oversizedFile.absolutePath)
        assertEquals(
            1,
            EpubTextSearch.search(oversizedFile.absolutePath, oversizedPackage, "after limit needle")
                .single().chapterIndex
        )
    }

    @Test
    fun parserSearchDoesNotPopulateLayoutCachesOrReadImages() = runBlocking {
        val file = temporaryFolder.newFile("cache-free.epub")
        val entries = searchableEpubEntries(listOf("<p>A &amp; B cache free needle</p>"))
            .toMutableMap()
            .apply { put("OPS/Images/unused.bin", "image needle") }
        writeEpub(file, entries)
        val parser = EpubParser()
        parser.parse(file.absolutePath)

        val matches = parser.searchEpub("a & b cache free needle")

        assertEquals(1, matches.size)
        assertTrue(privateMap(parser, "htmlCache").isEmpty())
        assertTrue(privateMap(parser, "contentCache").isEmpty())
    }

    private fun searchableEpubEntries(chapters: List<String>): Map<String, String> {
        val manifest = chapters.indices.joinToString("\n") { index ->
            "<item id=\"c$index\" href=\"Text/c$index.xhtml\" media-type=\"application/xhtml+xml\"/>"
        }
        val spine = chapters.indices.joinToString("\n") { index ->
            "<itemref idref=\"c$index\"/>"
        }
        return linkedMapOf<String, String>().apply {
            put(
                "META-INF/container.xml",
                "<container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">" +
                    "<rootfiles><rootfile full-path=\"OPS/package.opf\"/></rootfiles></container>"
            )
            put(
                "OPS/package.opf",
                "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\">" +
                    "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>Search</dc:title></metadata>" +
                    "<manifest>$manifest</manifest><spine>$spine</spine></package>"
            )
            chapters.forEachIndexed { index, body ->
                put(
                    "OPS/Text/c$index.xhtml",
                    "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body>$body</body></html>"
                )
            }
        }
    }

    private fun writeEpubWithOversizedEntry(
        file: File,
        entries: Map<String, String>,
        oversizedPath: String
    ) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                if (path == oversizedPath) {
                    val block = ByteArray(1024 * 1024) { 'x'.code.toByte() }
                    repeat(65) { zip.write(block) }
                } else {
                    zip.write(content.toByteArray(Charsets.UTF_8))
                }
                zip.closeEntry()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun privateMap(parser: EpubParser, fieldName: String): Map<Any?, Any?> {
        val field = EpubParser::class.java.getDeclaredField(fieldName).apply { isAccessible = true }
        return field.get(parser) as Map<Any?, Any?>
    }
}
