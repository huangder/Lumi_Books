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
        // Require an authored illustration hint and substantial relative width.
        // A large source bitmap or an ordinary note's width=100% alone is insufficient.
        return blockArtwork && percent != null && percent >= 25f
    }
}
