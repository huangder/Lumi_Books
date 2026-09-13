package com.huangder.lumibooks.util.parser

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan

/**
 * 段首缩进要跳过标题，否则章节标题也会跟着首行缩进。
 *
 * [android.text.Html.fromHtml] 会把 `<h1>`–`<h6>` 转成"放大 + 加粗"的 span，
 * 因此把"首字符同时带放大和加粗 span"视为标题；单纯 `<b>`（只加粗）或 `<big>`
 * （只放大）不会命中，避免把正文里的强调文字误判成标题。
 */
internal fun isHeadingParagraph(text: Spanned, start: Int, end: Int): Boolean {
    if (start < 0 || start >= end || end > text.length) return false
    val enlarged = text.getSpans(start, start + 1, RelativeSizeSpan::class.java)
        .any { it.sizeChange > 1.0f }
    if (!enlarged) return false
    return text.getSpans(start, start + 1, StyleSpan::class.java).any {
        it.style == Typeface.BOLD || it.style == Typeface.BOLD_ITALIC
    }
}

/** 去掉空白后比较，避免标题里的空格/换行造成误判。 */
internal fun normalizeHeadingText(value: CharSequence): String =
    buildString(value.length) {
        value.forEach { character ->
            if (!character.isWhitespace()) append(character)
        }
    }

/** 段落文本是否就是本章标题（应付用 CSS 类而不是 h1/h2 排版的标题）。 */
internal fun isChapterTitleParagraph(
    text: CharSequence,
    normalizedChapterTitle: String
): Boolean {
    if (normalizedChapterTitle.isEmpty()) return false
    return normalizeHeadingText(text) == normalizedChapterTitle
}
