package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.ui.layout.isLandscapeOrNearSquare

internal fun originalEpubSpreadEligible(
    enabled: Boolean, isEpub: Boolean, widthDp: Int, heightDp: Int,
    eInk: Boolean, transition: String
): Boolean = enabled && isEpub && !eInk && widthDp >= 600 &&
    isLandscapeOrNearSquare(widthDp, heightDp) && transition !in setOf("continuous", "scroll")
