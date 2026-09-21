package com.huangder.lumibooks.domain.model

/**
 * How the app presents the transition into the reader when a book is opened.
 *
 * [HERO] morphs the tapped cover into the reading window, [LOADING_PAGE] keeps the
 * established loading sheet that only appears when a book needs a while to open.
 */
enum class BookOpenTransition(val storedValue: String) {
    HERO("hero"),
    LOADING_PAGE("loading_page");

    companion object {
        fun fromStoredValue(value: String?): BookOpenTransition =
            entries.firstOrNull { it.storedValue == value } ?: HERO

        fun normalize(value: String?): String = fromStoredValue(value).storedValue
    }
}
