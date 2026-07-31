package com.huangder.lumibooks.domain.model

/**
 * WebDAV sync configuration.
 * Password is stored separately in [com.huangder.lumibooks.data.local.WebdavTokenStore]
 * for encrypted on-device protection.
 */
data class WebdavConfig(
    val enabled: Boolean = false,
    val serverUrl: String = "",       // e.g. https://dav.example.com/remote.php/dav/
    val username: String = "",
    val syncPath: String = "LumiBooks", // root directory on WebDAV server
    val lastSyncTime: Long = 0,         // epoch millis
    val syncMode: String = "auto",      // "auto" | "manual"
    val syncBookFiles: Boolean = true,
    val syncReadingRecords: Boolean = true,
    val syncBookmarks: Boolean = true,
    val syncNotes: Boolean = true
) {
    val syncsBookData: Boolean
        get() = syncReadingRecords || syncBookmarks || syncNotes

    val hasSelectedContent: Boolean
        get() = syncBookFiles || syncsBookData

    /** Remove leading/trailing whitespace and trailing slashes from serverUrl. */
    fun normalized(): WebdavConfig = copy(
        serverUrl = serverUrl.trim().trimEnd('/'),
        username = username.trim(),
        syncPath = syncPath.trim().trimEnd('/').ifEmpty { "LumiBooks" }
    )
}

enum class WebdavSyncContent {
    BOOK_FILES,
    READING_RECORDS,
    BOOKMARKS,
    NOTES
}
