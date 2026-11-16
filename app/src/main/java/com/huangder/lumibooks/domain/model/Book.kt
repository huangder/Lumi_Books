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
    val isPinned: Boolean = false,
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
    val isMissing: Boolean = false,
    val isHidden: Boolean = false,
    val isManuallyMarkedRead: Boolean = false,
    val isArchived: Boolean = false,
    /** Time the book joined the to-read list; 0 when it is not on it. */
    val wantToReadAt: Long = 0L
) {
    val isWantToRead: Boolean get() = wantToReadAt > 0L

    val isReadingFinished: Boolean
        get() = isManuallyMarkedRead || (readingProgress.isFinite() && readingProgress >= 1f)

    fun toggleReadStatus(): Book = if (isReadingFinished) {
        copy(readingProgress = 0f, isManuallyMarkedRead = false)
    } else {
        copy(isManuallyMarkedRead = true)
    }

    val contentSha256: String? get() = sourceSha256
    val originalSourceUri: String? get() = sourceUri
    val sourceFileName: String? get() = sourceDisplayName
    val lastModified: Long get() = sourceLastModified
}

enum class BookFormat {
    EPUB, PDF, TXT, MOBI, CBZ, MARKDOWN, AZW, AZW3, DOCX, DOC;

    val isKindle: Boolean get() = this == MOBI || this == AZW || this == AZW3
    val isWord: Boolean get() = this == DOCX || this == DOC
    val supportsEpubFeatures: Boolean get() = this == EPUB || isKindle || isWord
    val supportsHighlightRules: Boolean get() = this == TXT || supportsEpubFeatures
    val supportsBookLayout: Boolean get() = supportsEpubFeatures || this == MARKDOWN
    val supportsTextReading: Boolean get() = !isRasterPageFormat
    val supportsAnnotations: Boolean get() = this != CBZ

    /** Bitmap page sources (PDF/CBZ) are rendered by the raster reader instead of the text engines. */
    val isRasterPageFormat: Boolean get() = this == PDF || this == CBZ

    companion object {
        const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        const val DOC_MIME = "application/msword"
        private val extensions = entries.associateBy { it.name.lowercase() } + ("md" to MARKDOWN)
        val importExtensions: Set<String> get() = extensions.keys

        fun fromExtension(extension: String): BookFormat? = when (val key = extension.lowercase()) {
            "mdpack" -> MARKDOWN
            else -> extensions[key]
        }

        fun wordExtensionForMimeType(mimeType: String?): String? = when (mimeType?.substringBefore(';')?.trim()?.lowercase()) {
            DOCX_MIME -> "docx"
            DOC_MIME -> "doc"
            else -> null
        }
    }
}
