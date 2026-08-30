package com.huangder.lumibooks.domain.model

/** Launcher icon and splash artwork pairs exposed by the app. */
enum class AppIconStyle(val storedValue: String) {
    LUMI_2("lumi2"),
    CLASSIC("classic");

    companion object {
        fun fromStoredValue(value: String?): AppIconStyle =
            entries.firstOrNull { it.storedValue == value } ?: LUMI_2

        fun normalize(value: String?): String = fromStoredValue(value).storedValue
    }
}
