package com.huangder.lumibooks.data.sync

/** Phase of a full WebDAV sync, exposed for the bookshelf banner and the status sheet. */
enum class WebdavSyncPhase {
    PREPARING,
    MANIFEST,
    REMOTE_CHANGES,
    UPLOADING_BOOKS,
    BOOK_DATA,
    COMMIT,
    PORTABLE_STATE
}

enum class WebdavSyncTrigger { MANUAL, AUTO }

sealed interface WebdavSyncStatus {
    data object Idle : WebdavSyncStatus

    data class Running(
        val phase: WebdavSyncPhase,
        val bookTitle: String? = null,
        val done: Int = 0,
        val total: Int = 0,
        val startedAt: Long
    ) : WebdavSyncStatus {
        val fraction: Float?
            get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
    }

    data class Succeeded(val summary: String, val finishedAt: Long) : WebdavSyncStatus

    data class Failed(val message: String, val finishedAt: Long) : WebdavSyncStatus
}

/** Shelf-level overview of where each book's file lives. */
data class WebdavLibraryOverview(
    val localOnly: Int,
    val remoteOnly: Int,
    val both: Int,
    val missing: Int,
    val remoteOnlyBookIds: List<String>
) {
    val total: Int get() = localOnly + remoteOnly + both + missing
}

enum class WebdavBookPlacement { LOCAL_ONLY, REMOTE_ONLY, BOTH, MISSING }

/**
 * Classifies a shelf book. [hasLocalFile] means a readable body exists on this device,
 * [hasRemoteFile] means the WebDAV books directory currently contains its file.
 */
internal fun webdavBookPlacement(hasLocalFile: Boolean, hasRemoteFile: Boolean): WebdavBookPlacement =
    when {
        hasLocalFile && hasRemoteFile -> WebdavBookPlacement.BOTH
        hasLocalFile -> WebdavBookPlacement.LOCAL_ONLY
        hasRemoteFile -> WebdavBookPlacement.REMOTE_ONLY
        else -> WebdavBookPlacement.MISSING
    }
