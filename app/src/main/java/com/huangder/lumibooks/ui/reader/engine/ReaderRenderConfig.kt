package com.huangder.lumibooks.ui.reader.engine

import com.huangder.lumibooks.domain.model.ReaderEdgeTapMode
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.domain.model.ReaderWritingMode

data class ReaderLayoutConfig(
    val fontSizePx: Float,
    val theme: String,
    val chapterCount: Int,
    val startChapter: Int,
    val startPage: Int,
    val lineHeightMult: Float,
    val letterSpacingDp: Float,
    val textAlignment: ReaderTextAlignment,
    val fontType: String,
    val customFontPath: String?,
    val marginLeftDp: Float,
    val marginRightDp: Float,
    val marginTopDp: Float,
    val marginBottomDp: Float,
    val paragraphSpacingDp: Float,
    val firstLineIndent: Float,
    val bodyFontWeight: Int,
    val bionicReadingEnabled: Boolean,
    val useDisplayDensityForSpans: Boolean,
    val writingMode: ReaderWritingMode,
    val twoPageSpread: Boolean
)

data class ReaderBackgroundConfig(
    val color: Int,
    val textColor: Int,
    val imagePath: String?,
    val imageOpacity: Float,
    val imageBlurDp: Float
)

/** Immutable bridge from Compose state to the paged Android renderer. */
data class ReaderRenderConfig(
    val layout: ReaderLayoutConfig,
    val background: ReaderBackgroundConfig,
    val chineseMode: String,
    val pageTransition: String,
    val pageTransitionDurationMs: Int,
    val edgeTapMode: ReaderEdgeTapMode
)
