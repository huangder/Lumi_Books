package com.huangder.lumibooks.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import javax.inject.Inject

/**
 * SAF operations used by authorized-library reconciliation and physical folder moves.
 * All methods are deliberately small and side-effect explicit so callers can update Room only
 * after the corresponding document operation has succeeded.
 */
class AuthorizedStorageManager @Inject constructor() {
    data class ScannedDirectory(
        val uri: Uri,
        val treeUri: Uri,
        val parentUri: Uri?,
        val name: String,
        val relativePath: String?
    )

    data class ScannedDocument(
        val uri: Uri,
        val treeUri: Uri,
        val parentUri: Uri,
        val name: String,
        val relativeDirectory: String?,
        val lastModified: Long,
        val size: Long,
        val documentKey: String?
    )

    data class ScanResult(
        val rootUri: Uri,
        val directories: List<ScannedDirectory>,
        val documents: List<ScannedDocument>
    )

    data class MoveResult(
        val destinationUri: Uri,
        val sha256: String,
        val usedCopyFallback: Boolean
    )

    fun scan(context: Context, treeUri: Uri): ScanResult {
        val resolver = context.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val queue = ArrayDeque<Pair<String, String?>>()
        queue += rootId to null
        val visited = mutableSetOf<String>()
        val directories = mutableListOf<ScannedDirectory>()
        val documents = mutableListOf<ScannedDocument>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE
        )

        val rootName = queryDisplayName(context, rootUri).orEmpty().ifBlank {
            Uri.decode(rootId)
                .substringAfter(':', rootId)
                .trim('/')
                .substringAfterLast('/')
                .trim()
        }
        directories += ScannedDirectory(rootUri, treeUri, null, rootName, null)

        while (queue.isNotEmpty()) {
            val (parentId, relativePath) = queue.removeFirst()
            if (!visited.add(parentId)) continue
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val flagsIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                if (idIndex < 0 || nameIndex < 0 || mimeIndex < 0) return@use
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex).orEmpty()
                    val mime = cursor.getString(mimeIndex).orEmpty()
                    val flags = if (flagsIndex >= 0 && !cursor.isNull(flagsIndex)) {
                        cursor.getLong(flagsIndex)
                    } else {
                        0L
                    }
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    // A few third-party providers omit MIME_TYPE_DIR but still expose the
                    // directory capability flag. Recognize both forms so empty/pre-existing
                    // folders are included in a refresh as well.
                    val isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR ||
                        flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE.toLong() != 0L
                    if (isDirectory) {
                        val childPath = name.trim().takeIf { it.isNotBlank() }?.let {
                            listOfNotNull(relativePath, it).joinToString("/")
                        }
                        directories += ScannedDirectory(childUri, treeUri, parentUri, name, childPath)
                        queue += id to childPath
                    } else if (FileUtils.getFileExtension(name) in SUPPORTED_EXTENSIONS) {
                        documents += ScannedDocument(
                            uri = childUri,
                            treeUri = treeUri,
                            parentUri = parentUri,
                            name = name,
                            relativeDirectory = relativePath,
                            lastModified = if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) cursor.getLong(modifiedIndex) else 0L,
                            size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex).coerceAtLeast(0L) else 0L,
                            documentKey = documentKey(childUri)
                        )
                    }
                }
            }
        }
        return ScanResult(rootUri, directories, documents)
    }

    fun documentKey(uri: Uri): String? = runCatching {
        val id = DocumentsContract.getDocumentId(uri)
        val authority = uri.authority ?: return@runCatching null
        "$authority:$id"
    }.getOrNull()

    fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    }.getOrNull()

    fun queryLastModified(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).coerceAtLeast(0L) else 0L
        } ?: 0L
    }.getOrDefault(0L)

    fun createDirectory(context: Context, parentUri: Uri, name: String): Uri =
        DocumentsContract.createDocument(
            context.contentResolver,
            parentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            name
        ) ?: error("Unable to create directory")

    /** Returns the document URI represented by an OpenDocumentTree result. */
    fun treeRootUri(treeUri: Uri): Uri {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
    }

    /**
     * Rebuilds a document URI under [treeUri]. Some DocumentsProviders return plain
     * document URIs from createDocument while moveDocument expects both parents to use
     * the same tree-qualified form.
     */
    fun documentUriUsingTree(treeUri: Uri, documentUri: Uri): Uri = runCatching {
        DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(documentUri)
        )
    }.getOrDefault(documentUri)

    fun rename(context: Context, documentUri: Uri, name: String): Uri =
        DocumentsContract.renameDocument(context.contentResolver, documentUri, name)
            ?: error("Unable to rename document")

    fun move(
        context: Context,
        documentUri: Uri,
        sourceParentUri: Uri,
        targetParentUri: Uri
    ): Uri = DocumentsContract.moveDocument(
        context.contentResolver,
        documentUri,
        sourceParentUri,
        targetParentUri
    ) ?: error("Unable to move document")

    /**
     * Copies a book into an authorized directory and deletes the source only after a hash match.
     * The caller decides whether a local source is app-managed before invoking this method.
     */
    fun copyThenDelete(
        context: Context,
        sourceLocation: String,
        targetParentUri: Uri,
        fileName: String,
        expectedSha256: String? = null,
        deleteSource: Boolean = true
    ): MoveResult {
        val resolver = context.contentResolver
        val mime = mimeTypeFor(fileName)
        val destination = DocumentsContract.createDocument(resolver, targetParentUri, mime, fileName)
            ?: error("Unable to create destination document")
        var completed = false
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            openSource(context, sourceLocation).use { input ->
                resolver.openOutputStream(destination, "w").use { output ->
                    requireNotNull(output) { "Unable to open destination document" }
                    copyDigest(input, output, digest)
                }
            }
            val hash = digest.toHex()
            if (expectedSha256 != null && !expectedSha256.equals(hash, ignoreCase = true)) {
                DocumentsContract.deleteDocument(resolver, destination)
                error("Copied file verification failed")
            }
            if (deleteSource) deleteSource(context, sourceLocation)
            completed = true
            return MoveResult(destination, hash, usedCopyFallback = true)
        } finally {
            if (!completed) runCatching { DocumentsContract.deleteDocument(resolver, destination) }
        }
    }

    fun sha256(context: Context, location: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        openSource(context, location).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.toHex()
    }

    private fun openSource(context: Context, location: String): InputStream =
        if (BookFileAccess.isContentUri(location)) {
            context.contentResolver.openInputStream(Uri.parse(location))
                ?: error("Unable to open source document")
        } else {
            File(location).inputStream().buffered()
        }

    private fun deleteSource(context: Context, location: String) {
        if (BookFileAccess.isContentUri(location)) {
            if (!DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(location))) {
                error("Unable to delete source document")
            }
        } else if (!FileUtils.deleteAppManagedBookFile(context, location)) {
            error("Unable to delete source file")
        }
    }

    private fun mimeTypeFor(fileName: String): String = when (FileUtils.getFileExtension(fileName)) {
        "epub" -> "application/epub+zip"
        "pdf" -> "application/pdf"
        "mobi" -> "application/x-mobipocket-ebook"
        else -> "text/plain"
    }

    private fun copyDigest(input: InputStream, output: OutputStream, digest: MessageDigest) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            digest.update(buffer, 0, count)
            output.write(buffer, 0, count)
        }
    }

    private fun MessageDigest.toHex(): String = digest().joinToString("") { "%02x".format(it) }

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf("epub", "pdf", "txt", "mobi")
    }
}
