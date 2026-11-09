package com.huangder.lumibooks.util.epub

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class EpubFootnoteImagePolicyTest {
    private fun illustration(attributes: String, css: String = ""): Boolean =
        EpubFootnoteImagePolicy.isPublisherIllustration(
            Jsoup.parseBodyFragment("<img $attributes>").selectFirst("img")!!,
            EpubCssIndex.parse(listOf("book.css" to css)))

    @Test fun authoredArtworkCanActAsANoteReference() {
        assertTrue(illustration("class='duokan-image-static' width='100%'"))
        assertTrue(illustration("class='duokan-image-single' style='width:50%'"))
        assertTrue(illustration("style='display:block;width:100%'"))
        assertTrue(illustration("class='art'", ".art{display:block;width:80%}"))
    }

    @Test fun smallIconsAndLargeIntrinsicBitmapsDoNotBecomeIllustrations() {
        assertFalse(illustration("class='duokan-footnote' width='100%'"))
        assertFalse(illustration("width='1200' height='1800'"))
        assertFalse(illustration("class='duokan-image-static' width='100%' style='width:1em'"))
        assertFalse(illustration("class='duokan-image-static' width='12%'"))
        assertFalse(illustration("class='duokan-image-static' width='100%'", "img{width:11px}"))
    }
}
