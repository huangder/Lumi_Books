package com.huangder.lumibooks.ui.reader

import android.content.Context
import android.widget.TextView
import com.huangder.lumibooks.ui.reader.engine.PageLayoutEngine
import com.huangder.lumibooks.ui.reader.engine.resolveReaderTypeface
import com.huangder.lumibooks.ui.reader.engine.resolveReaderVerticalMargins
import com.huangder.lumibooks.ui.layout.isLandscapeOrNearSquare

/** Configure the numbering index with the same single-page metrics as ReadView. */
internal fun configureScrollPagination(
    context: Context,
    engine: PageLayoutEngine,
    state: ReaderUiState,
    width: Int,
    height: Int,
    contentIdentity: String,
    bionicReading: Boolean,
    twoPageSpread: Boolean = false
) {
    val density = context.resources.displayMetrics.density
    val fontSize = state.fontSize * density
    val face = resolveReaderTypeface(context, state.fontType, state.customFontPath, state.bodyFontWeight)
    val fontKey = readerPaginationFontKey(context, state.fontType, state.customFontPath, state.bodyFontWeight)
    val margins = resolveReaderVerticalMargins(height, state.marginTopDp * density,
        state.marginBottomDp * density, fontSize, state.lineHeight, 2.5f * density, face.typeface)
    val spread = twoPageSpread && isLandscapeOrNearSquare(width, height)
    val pageWidth = if (spread) ((width - (16f * density).toInt()) / 2).coerceAtLeast(1) else width
    val rightMargin = if (spread) (state.marginLeftDp.coerceAtMost(state.marginRightDp) / 2f)
        .coerceAtLeast(12f) else state.marginRightDp
    engine.sharedTextPaint = TextView(context).paint.apply {
        textSize = fontSize
        typeface = face.typeface
        isFakeBoldText = face.fakeBold
        letterSpacing = state.letterSpacing * density / fontSize
    }
    engine.setPaginationIdentity(contentIdentity)
    engine.setPaginationLayoutIdentity(listOf(fontKey, state.paragraphSpacing, state.firstLineIndent,
        bionicReading, twoPageSpread, state.chineseMode).joinToString("|"))
    engine.configure(width = pageWidth, height = height, fontSizePx = fontSize,
        lineSpacingPx = 2.5f * density, lineSpacingMult = state.lineHeight,
        letterSpacingPx = state.letterSpacing * density, fontType = state.fontType,
        typeface = face.typeface, fontWeight = state.bodyFontWeight,
        marginLeftPx = state.marginLeftDp * density, marginRightPx = rightMargin * density,
        marginTopPx = margins.topPx.toFloat(), marginBottomPx = margins.bottomPx.toFloat(),
        chapterCount = state.chapterCount, useDisplayDensityForSpans = state.book?.format?.name == "TXT",
        textAlignment = state.textAlignment, fontIdentity = fontKey)
}

internal fun readerScrollPageIndex(
    pages: ReaderBookPages,
    chapter: Int,
    character: Int?,
    fraction: Float
): Int? {
    val count = pages.chapterCounts.getOrNull(chapter)?.takeIf { it > 0 } ?: return null
    return (character?.let { pages.pageAtCharacter(chapter, it) }
        ?: ((if (fraction.isFinite()) fraction else 0f).coerceIn(0f, 1f) * count).toInt())
        .coerceIn(0, count - 1)
}
