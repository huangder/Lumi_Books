package com.huangder.lumibooks.util.epub

import org.jsoup.nodes.Element

/** A noteref is an interaction, not proof that every image inside it is a tiny icon. */
internal object EpubFootnoteImagePolicy {
    private val illustrationClasses = setOf("duokan-image-static", "duokan-image-single")

    fun isPublisherIllustration(image: Element, cssIndex: EpubCssIndex = EpubCssIndex.EMPTY): Boolean {
        fun declaration(name: String): String? = Regex(
            """(?:^|;)\s*${Regex.escape(name)}\s*:\s*([^;]+)""", RegexOption.IGNORE_CASE
        ).find(image.attr("style"))?.groupValues?.get(1)?.substringBefore('!')?.trim()
            ?: cssIndex.declaration(image, name)?.substringBefore('!')?.trim()

        val width = declaration("width") ?: image.attr("width")
        val percent = width.trim().removeSuffix("%").toFloatOrNull()
            ?.takeIf { width.trim().endsWith('%') }
        val blockArtwork = image.classNames().any { it in illustrationClasses } ||
            declaration("display").equals("block", ignoreCase = true)
        // Some Duokan books put the illustration directly inside the answer link,
        // without an image class. Require an image-only Duokan noteref as well as
        // the authored relative width; ordinary noterefs and sized icons stay inline.
        val anchor = image.closest("a")
        val illustratedAnswer = anchor != null && anchor.hasClass("duokan-footnote") &&
            anchor.attr("epub:type").split(Regex("\\s+")).contains("noteref") &&
            anchor.text().isBlank() && anchor.select("img").size == 1 &&
            !image.hasClass("duokan-footnote") && image.closest("sup, sub") == null
        // Require an authored illustration hint and substantial relative width.
        // A large source bitmap or an ordinary note's width=100% alone is insufficient.
        return (blockArtwork || illustratedAnswer) && percent != null && percent >= 25f
    }
}
