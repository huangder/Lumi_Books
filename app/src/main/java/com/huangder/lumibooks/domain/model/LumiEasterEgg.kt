package com.huangder.lumibooks.domain.model

object LumiEasterEgg {
    const val THEME = "lumi_chan"
    const val REQUIRED_CLICKS = 5

    fun nextClickCount(clicks: Int, unlocked: Boolean): Int =
        if (unlocked) clicks else (clicks + 1).coerceIn(0, REQUIRED_CLICKS)

    fun canSelect(value: String, unlocked: Boolean): Boolean = value != THEME || unlocked

    fun normalizeTheme(value: String?, unlocked: Boolean): String =
        (value ?: "lumi").takeIf { canSelect(it, unlocked) } ?: "lumi"
}
