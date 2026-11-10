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

    @Test fun imageOnlyDuokanAnswerLinksCanSupplyTheIllustrationHint() {
        fun answer(attributes: String, text: String = "", anchorClass: String = "duokan-footnote"): Boolean =
            EpubFootnoteImagePolicy.isPublisherIllustration(Jsoup.parseBodyFragment(
                """<a class="$anchorClass" epub:type="noteref" href="#footnote_1">$text<img $attributes/></a>"""
            ).selectFirst("img")!!)
        assertTrue(answer("width='100%'"))
        assertTrue(answer("width='100%' style='duokan-bleed:leftright;'"))
        assertFalse(answer("width='100%' style='width:11px'"))
        assertFalse(answer("width='100%' class='duokan-footnote'"))
        assertFalse(answer("width='12%'"))
        assertFalse(answer("width='100%'", text = "Note"))
        assertFalse(answer("width='100%'", anchorClass = "ordinary-note"))
    }
}
