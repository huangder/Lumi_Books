package com.huangder.lumibooks.domain.model

/** Launcher icon and splash artwork pairs exposed by the app. */
enum class AppIconStyle(val storedValue: String) {
    LUMI_CHAN("lumi_chan"),
    LUMI_2("lumi2"),
    CLASSIC("classic");

    companion object {
        fun fromStoredValue(value: String?): AppIconStyle =
            entries.firstOrNull { it.storedValue == value } ?: LUMI_2

        fun fromStoredValue(value: String?, easterEggUnlocked: Boolean): AppIconStyle =
            fromStoredValue(value).takeUnless { it == LUMI_CHAN && !easterEggUnlocked } ?: LUMI_2

        fun normalize(value: String?, easterEggUnlocked: Boolean = true): String =
            fromStoredValue(value, easterEggUnlocked).storedValue
    }
}
