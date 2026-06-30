package com.huangder.lumibooks.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Bookshelf : Screen("bookshelf")
    object Statistics : Screen("statistics")
    object Reader : Screen("reader/{bookId}") {
        fun createRoute(bookId: String) = "reader/$bookId"
    }
    object Settings : Screen("settings")
    object Bookmarks : Screen("bookmarks/{bookId}") {
        fun createRoute(bookId: String) = "bookmarks/$bookId"
    }
    object Notes : Screen("notes/{bookId}") {
        fun createRoute(bookId: String) = "notes/$bookId"
    }
}
