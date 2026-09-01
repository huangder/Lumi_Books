package com.huangder.lumibooks.domain.model

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val filePath: String,
    val coverPath: String?,
    val format: BookFormat,
    val lastReadTime: Long,
    val readingProgress: Float,
    val locatorJson: String? = null,
    val createdAt: Long,
    val isFavorite: Boolean = false,
    val isCloudOnly: Boolean = false,
    val remoteLibraryKey: String? = null,
    val remoteFileName: String? = null,
    val remoteFileSize: Long = 0L,
    val remoteFileSha256: String? = null,
    val metadataUpdatedAt: Long = createdAt,
    /** Original picker/share URI, retained even when the file is copied into app storage. */
    val sourceUri: String? = null,
    /** Stable provider key (authority + document id) for SAF reconciliation. */
    val sourceDocumentKey: String? = null,
    /** Current physical parent document URI when the source is in an authorized tree. */
    val sourceParentUri: String? = null,
    /** SHA-256 of the current source bytes, used as a deduplication fallback. */
    val sourceSha256: String? = null,
    /** Last display name observed at import/reconciliation time. */
    val sourceDisplayName: String? = null,
    val sourceLastModified: Long = 0L,
    val isMissing: Boolean = false
) {
    val contentSha256: String? get() = sourceSha256
    val originalSourceUri: String? get() = sourceUri
    val sourceFileName: String? get() = sourceDisplayName
    val lastModified: Long get() = sourceLastModified
}

enum class BookFormat {
    EPUB, PDF, TXT, MOBI
}
