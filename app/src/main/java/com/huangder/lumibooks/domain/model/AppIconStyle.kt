package com.huangder.lumibooks.domain.model

/** Launcher icon and splash artwork pairs exposed by the app. */
enum class AppIconStyle(val storedValue: String) {
    LUMI_CHAN("lumi_chan"),
    LUMI_2("lumi2"),

    /** Lumi 2 artwork on a dark background, regardless of the system theme. */
    LUMI_2_DARK("lumi2_dark"),

    /** Lumi 2 artwork that follows the system theme through Android's `-night` resources. */
    LUMI_2_AUTO("lumi2_auto"),
    CLASSIC("classic");

    /** Whether this style is one of the Lumi 2 appearance variants (light / dark / auto). */
    val isLumi2Family: Boolean
        get() = this == LUMI_2 || this == LUMI_2_DARK || this == LUMI_2_AUTO

    companion object {
        fun fromStoredValue(value: String?): AppIconStyle =
            entries.firstOrNull { it.storedValue == value } ?: LUMI_2

        fun fromStoredValue(value: String?, easterEggUnlocked: Boolean): AppIconStyle =
            fromStoredValue(value).takeUnless { it == LUMI_CHAN && !easterEggUnlocked } ?: LUMI_2

        fun normalize(value: String?, easterEggUnlocked: Boolean = true): String =
            fromStoredValue(value, easterEggUnlocked).storedValue
    }
}
