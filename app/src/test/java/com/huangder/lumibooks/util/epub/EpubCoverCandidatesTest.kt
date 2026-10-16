package com.huangder.lumibooks.util.epub

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubCoverCandidatesTest {
    private fun book(vararg items: EpubManifestItem, firstId: String = items.first().id) = EpubPackage(
        filePath = "book.epub", opfPath = "OPS/content.opf", basePath = "OPS",
        title = "", author = "", manifest = items.associateBy { it.id },
        spine = listOf(EpubSpineItem(firstId, items.first { it.id == firstId },
            renditionLayout = EpubRenditionLayout.REFLOWABLE)),
        navigation = emptyList(), pageProgressionDirection = EpubPageProgressionDirection.DEFAULT,
        renditionLayout = EpubRenditionLayout.REFLOWABLE, renditionOrientation = null,
        renditionSpread = null, renditionFlow = null
    )

    @Test fun htmlCoverResolvesImageRelativeToChapter() {
        val cover = EpubManifestItem("front", "Text/cover.xhtml", "OPS/Text/cover.xhtml", "application/xhtml+xml")
        val epub = book(cover)
        val opf = "<package><metadata><meta content='front' name='cover'/></metadata></package>"
        val paths = EpubCoverCandidates.paths(epub, opf) {
            "<html><body><svg><image xlink:href='../Images/front%20art.jpg'/></svg></body></html>"
        }
        assertEquals(listOf("OPS/Images/front art.jpg"), paths)
    }

    @Test fun metadataImagePrecedesNamedAndSpineCandidates() {
        val first = EpubManifestItem("first", "Text/start.xhtml", "OPS/Text/start.xhtml", "application/xhtml+xml")
        val named = EpubManifestItem("coverNamed", "Images/cover.jpg", "OPS/Images/cover.jpg", "image/jpeg")
        val declared = EpubManifestItem("declared", "Images/art.png", "OPS/Images/art.png", "image/png")
        val epub = book(first, named, declared)
        val paths = EpubCoverCandidates.paths(epub,
            "<package><metadata><meta name='cover' content='declared'/></metadata></package>") {
            "<html><body><img src='../Images/start.jpg'/></body></html>"
        }
        assertEquals(listOf("OPS/Images/art.png", "OPS/Images/cover.jpg", "OPS/Images/start.jpg"), paths)
    }

    @Test fun multiImageFirstChapterIsNotAFrontCover() {
        val first = EpubManifestItem("first", "Text/start.xhtml", "OPS/Text/start.xhtml", "application/xhtml+xml")
        val paths = EpubCoverCandidates.paths(book(first), "<package/>") {
            "<html><body><img src='a.png'/><img src='b.png'/></body></html>"
        }
        assertEquals(emptyList<String>(), paths)
    }

    @Test fun guideCoverChapterPrecedesNamedImage() {
        val first = EpubManifestItem("start", "Text/start.xhtml", "OPS/Text/start.xhtml", "application/xhtml+xml")
        val cover = EpubManifestItem("front", "Text/front.xhtml", "OPS/Text/front.xhtml", "application/xhtml+xml")
        val named = EpubManifestItem("coverArt", "Images/cover.jpg", "OPS/Images/cover.jpg", "image/jpeg")
        val paths = EpubCoverCandidates.paths(book(first, cover, named),
            "<package><guide><reference href='Text/front.xhtml' type='cover'/></guide></package>") { path ->
            if (path == cover.fullPath) "<html><body><img src='../Images/front.jpg'/></body></html>"
            else "<html><body><p>Chapter one</p><img src='../Images/logo.png'/></body></html>"
        }
        assertEquals(listOf("OPS/Images/front.jpg", "OPS/Images/cover.jpg"), paths)
    }

    @Test fun firstChapterWithTextAndImageIsNotCover() {
        val first = EpubManifestItem("start", "Text/start.xhtml", "OPS/Text/start.xhtml", "application/xhtml+xml")
        val paths = EpubCoverCandidates.paths(book(first), "<package/>") {
            "<html><body><h1>Chapter one</h1><img src='../Images/logo.png'/></body></html>"
        }
        assertEquals(emptyList<String>(), paths)
    }

    @Test fun epub3CoverImagePropertyPrecedesNamedChapter() {
        val first = EpubManifestItem("start", "Text/start.xhtml", "OPS/Text/start.xhtml", "application/xhtml+xml")
        val named = EpubManifestItem("coverPage", "Text/cover.xhtml", "OPS/Text/cover.xhtml", "application/xhtml+xml")
        val declared = EpubManifestItem("art", "Images/front.png", "OPS/Images/front.png",
            "image/png", setOf("cover-image"))
        val paths = EpubCoverCandidates.paths(book(first, named, declared), "<package/>") { path ->
            if (path == named.fullPath) "<html><body><img src='../Images/alternate.jpg'/></body></html>"
            else "<html><body><p>Chapter</p></body></html>"
        }
        assertEquals(listOf("OPS/Images/front.png", "OPS/Images/alternate.jpg"), paths)
    }
}
