package com.huangder.lumibooks.util.epub

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpubComicIndexTest {
    private fun fixture(first: String, second: String = "<html><body/></html>", nav: String? = null): Pair<EpubPackage, Map<String, String>> {
        val entries = epub3Entries().toMutableMap()
        entries["OPS/Text/chapter 1.xhtml"] = first
        entries["OPS/Text/chapter2.xhtml"] = second
        if (nav != null) entries["OPS/nav.xhtml"] = nav
        val file = File.createTempFile("epub-comic-index", ".epub")
        return try {
            writeEpub(file, entries)
            EpubPackageReader.read(file.path) to entries
        } finally { file.delete() }
    }

    private fun index(fixture: Pair<EpubPackage, Map<String, String>>) =
        EpubComicIndexer.build(fixture.first, { fixture.second[it]?.toByteArray() })

    @Test fun followsSpineAndDomOrderAndRetainsRepeatedAndMissingImages() {
        val result = index(fixture("""<html><body><img src="../Images/10.jpg"/><img src="../Images/2.jpg"/>
            <img src="../Images/10.jpg"/><img src="../Images/missing.jpg"/></body></html>""",
            """<html><body><img src="../Images/1.jpg"/></body></html>"""))
        assertEquals(listOf("10.jpg", "2.jpg", "10.jpg", "missing.jpg", "1.jpg"), result.pages.map { it.imagePath.substringAfterLast('/') })
        assertEquals(listOf(0, 1, 2, 3, 0), result.pages.map { it.occurrence })
        assertEquals(listOf(0, 0, 0, 0, 1), result.pages.map { it.chapterIndex })
        assertTrue(result.rightToLeft)
        assertFalse(result.hasOmittedContent)
    }

    @Test fun unwrapsSvgImageAndResolvesEncodedRelativePaths() {
        val result = index(fixture("""<html><body><svg><image xlink:href="../Images/hello%20world.png"/></svg>
            <img src="../Images/../Images/a.webp"/><img src="https://example.test/remote.jpg"/>
            <img src="../../../../escape.jpg"/></body></html>"""))
        assertEquals(listOf("OPS/Images/hello world.png", "OPS/Images/a.webp"), result.pages.map { it.imagePath })
        assertTrue(result.hasOmittedContent)
    }

    @Test fun mapsAnchoredLogicalChaptersAndKeepsImagesBeforeFirstAnchor() {
        val result = index(fixture("""<html><body><img src="../Images/intro.jpg"/>
            <div id="a"><img src="../Images/a.jpg"/></div><div id="b"><img src="../Images/b.jpg"/></div></body></html>""",
            nav = """<html xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol>
            <li><a href="Text/chapter%201.xhtml#a">A</a></li><li><a href="Text/chapter%201.xhtml#b">B</a></li>
            </ol></nav></body></html>"""))
        assertEquals(listOf(0, 0, 1), result.pages.map { it.chapterIndex })
        assertEquals(listOf("A", "B", ""), result.chapterTitles)
        assertEquals("b", result.pages.last().anchor)
        assertEquals(2, result.pageForChapter(1))
        assertEquals(2, result.pageForChapter(2))
    }

    @Test fun warnsAboutTextBackgroundsAndPureVectors() {
        val fixture = fixture("""<html><head><link href="../Styles/book.css"/></head><body><p>Text</p>
            <svg><path d="M0 0"/></svg><img src="../Images/page.jpg"/></body></html>""")
        assertTrue(index(fixture).hasOmittedContent)
        val background = fixture("""<html><head><link href="../Styles/book.css"/></head><body><img src="../Images/page.jpg"/></body></html>""")
        assertTrue(index(background.first to (background.second + ("OPS/Styles/book.css" to "body { background-image: url(paper.png) }"))).hasOmittedContent)
    }

    @Test fun imageSpineDoesNotScanOrDecodeOtherArchiveImages() {
        val (book, entries) = fixture("<html><body/></html>")
        val image = EpubManifestItem("image", "Images/page.jpg", "OPS/Images/page.jpg", "image/jpeg")
        val reads = mutableListOf<String>()
        val result = EpubComicIndexer.build(book.copy(spine = listOf(EpubSpineItem("image", image,
            renditionLayout = EpubRenditionLayout.PRE_PAGINATED))), { reads += it; entries[it]?.toByteArray() })
        assertEquals("OPS/Images/page.jpg", result.pages.single().imagePath)
        assertTrue(reads.isEmpty())
    }

    @Test fun positionsAndCacheRoundTripAndRejectUnknownVersions() {
        val result = index(fixture("""<html><body><img src="../Images/a.jpg"/><img src="../Images/a.jpg"/></body></html>"""))
        val position = EpubComicPosition.fromPage(result.pages[1], 0.625f)
        assertEquals(position, EpubComicPosition.decode(position.encode()))
        assertEquals(1, result.restore(position))
        assertEquals(result, EpubComicIndex.decode(result.encode()))
        assertNull(EpubComicIndex.decode(result.encode().replace("\"version\":1", "\"version\":999")))
        assertEquals(0, result.restore(position.copy(imagePath = "deleted.jpg")))
        assertEquals(1, result.pageForChapter(9))
    }

    @Test fun annotatedImagesRetainPhysicalOccurrenceAfterLogicalSlicing() {
        val fixture = fixture("""<html><body><div id="a"><img src="../Images/a.jpg"/></div>
            <div id="b"><img src="../Images/a.jpg"/></div></body></html>""")
        val annotated = EpubComicIndexer.annotate(fixture.first, "OPS/Text/chapter 1.xhtml", 1,
            fixture.second.getValue("OPS/Text/chapter 1.xhtml").toByteArray())
        val sliced = sliceEpubLogicalChapter(annotated, "b", null)
        val image = org.jsoup.Jsoup.parse(sliced).selectFirst("img")!!
        val position = EpubComicPosition.decode(image.attr("data-lumi-comic-image"))!!
        assertEquals(1, position.occurrence)
        assertEquals(1, position.chapterIndex)
    }

    @Test fun cancellationStopsIndexing() {
        val fixture = fixture("<html><body/></html>")
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            EpubComicIndexer.build(fixture.first, { fixture.second[it]?.toByteArray() },
                { throw java.util.concurrent.CancellationException() })
        }
    }
}
