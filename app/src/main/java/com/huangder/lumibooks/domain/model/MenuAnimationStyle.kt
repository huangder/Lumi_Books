package com.huangder.lumibooks.domain.model

enum class MenuAnimationStyle(val storedValue: String) {
    NORMAL("normal"),
    LIQUID("liquid");

    companion object {
        fun fromStoredValue(value: String?): MenuAnimationStyle =
            entries.firstOrNull { it.storedValue == value } ?: LIQUID

        fun normalize(value: String?): String = fromStoredValue(value).storedValue
    }
}
