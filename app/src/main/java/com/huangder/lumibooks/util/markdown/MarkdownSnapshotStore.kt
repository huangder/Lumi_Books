package com.huangder.lumibooks.util.markdown

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.huangder.lumibooks.util.BookFileAccess
import com.huangder.lumibooks.util.AuthorizedStorageManager
import com.huangder.lumibooks.util.FileUtils
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Immutable, versioned snapshots survive cache eviction, permission loss and deferred updates. */
object MarkdownSnapshotStore {
    private const val MAX_DOCUMENT = 64L * 1024 * 1024
    private const val MAX_PACKAGE = 256L * 1024 * 1024
    private const val MAX_ENTRIES = 5002
    private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp")

    fun sourceLastModified(context: Context, source: String): Long =
        if (BookFileAccess.isContentUri(source)) AuthorizedStorageManager().queryLastModified(context, Uri.parse(source))
        else File(source).lastModified()
    private fun bookRoot(context: Context, bookId: String): File {
        require(bookId.matches(Regex("[A-Za-z0-9_-]+")))
        return File(FileUtils.getBooksDirectory(context), "markdown/$bookId").apply { mkdirs() }
    }

    fun create(context: Context, bookId: String, source: String, parentUri: String? = null): File {
        val stage = File(bookRoot(context, bookId), "stage-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val markdown = File(stage, "document.md")
            BookFileAccess.openInputStream(context, source).use { input -> markdown.outputStream().use { copyBounded(input, it, MAX_DOCUMENT) } }
            val raw = markdown.readText(Charsets.UTF_8)
            val parsed = com.huangder.lumibooks.util.markdown.native.MarkdownNativeParser.inspect(raw)
            val assets = JSONObject()
            var total = markdown.length()
            parsed.images.forEach { src ->
                val relative = MarkdownRenderer.safeRelativePath(src) ?: return@forEach
                if (relative.substringAfterLast('.', "").lowercase() !in imageExtensions || assets.length() >= MAX_ENTRIES - 2) return@forEach
                val input = resolveAsset(context, source, parentUri, relative) ?: return@forEach
                val target = File(stage, "assets/$relative")
                target.parentFile?.mkdirs()
                // Missing images are non-fatal; resource-limit failures must not discard the document.
                val copied = runCatching { input.use { stream -> target.outputStream().use { copyBounded(stream, it, MAX_PACKAGE - total) } } }.isSuccess
                if (copied) { total += target.length(); assets.put(relative, sha256(target)) } else target.delete()
            }
            val hash = sha256(markdown)
            val manifest = JSONObject().put("version", 1).put("sourceSha256", hash)
                .put("sourceSize", markdown.length()).put("sourceLastModified", sourceLastModified(context, source))
                .put("originalName", BookFileAccess.displayName(context, source) ?: "document.md")
                .put("title", parsed.title ?: BookFileAccess.displayName(context, source)?.substringBeforeLast('.').orEmpty())
                .put("author", parsed.author.orEmpty()).put("assets", assets)
            File(stage, "manifest.json").writeText(manifest.toString())
            return commit(stage, bookRoot(context, bookId))
        } catch (error: Throwable) { stage.deleteRecursively(); throw error }
    }

    private fun commit(stage: File, root: File): File {
        val manifest = File(stage, "manifest.json").readText()
        val key = sha256(manifest.toByteArray())
        val destination = File(root, key)
        if (destination.isDirectory) stage.deleteRecursively()
        else check(stage.renameTo(destination)) { "Unable to install Markdown snapshot" }
        return File(destination, "document.md")
    }

    fun manifest(document: File): JSONObject? = runCatching {
        JSONObject(File(document.parentFile, "manifest.json").readText())
    }.getOrNull()

    /** No provider timestamps are assumed reliable. Hash the MD on open off the UI thread. */
    fun sourceChanged(context: Context, document: File, source: String): Boolean {
        val original = manifest(document)?.optString("sourceSha256") ?: sha256(document)
        return BookFileAccess.openInputStream(context, source).use { sha256(it, MAX_DOCUMENT) } != original
    }

    fun assets(document: File): Map<String, File> {
        val root = File(document.parentFile, "assets").canonicalFile
        val entries = manifest(document)?.optJSONObject("assets") ?: return emptyMap()
        return entries.keys().asSequence().mapNotNull { path ->
            val safe = MarkdownRenderer.safeRelativePath(path) ?: return@mapNotNull null
            val file = File(root, safe).canonicalFile
            if (file.toPath().startsWith(root.toPath()) && file.isFile &&
                safe.substringAfterLast('.', "").lowercase() in imageExtensions) safe to file else null
        }.toMap()
    }

    fun packageFile(document: File): File {
        require(document.isFile && manifest(document) != null) { "Markdown snapshot is missing" }
        val output = File(document.parentFile, "book.mdpack")
        if (output.isFile) return output
        val temporary = File(document.parentFile, "package-${UUID.randomUUID()}.tmp")
        try {
            ZipOutputStream(temporary.outputStream().buffered()).use { zip ->
                val files = listOf("manifest.json" to File(document.parentFile, "manifest.json"), "document.md" to document) +
                    assets(document).toSortedMap().map { (path, file) -> "assets/$path" to file }
                files.forEach { (path, file) ->
                    zip.putNextEntry(ZipEntry(path).apply { time = 0 })
                    file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
            check(temporary.renameTo(output) || output.isFile) { "Unable to write Markdown package" }
            return output
        } finally { temporary.delete() }
    }

    fun unpack(context: Context, bookId: String, archive: File): File {
        val root = bookRoot(context, bookId)
        val stage = File(root, "stage-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            ZipFile(archive).use { zip ->
                var total = 0L
                val names = mutableSetOf<String>()
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    require(names.add(entry.name) && names.size <= MAX_ENTRIES) { "Duplicate or excessive Markdown package entries" }
                    val path = MarkdownRenderer.safeRelativePath(entry.name)
                    require(path == entry.name && (path == "document.md" || path == "manifest.json" || path.startsWith("assets/"))) { "Unsafe Markdown package path" }
                    if (entry.isDirectory) continue
                    val output = File(stage, path).canonicalFile
                    require(output.toPath().startsWith(stage.canonicalFile.toPath()))
                    output.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> output.outputStream().use {
                        val bound = if (path == "document.md") minOf(MAX_DOCUMENT, MAX_PACKAGE - total) else MAX_PACKAGE - total
                        total += copyBounded(input, it, bound)
                    } }
                }
            }
            val document = File(stage, "document.md")
            val metadata = manifest(document) ?: error("Markdown package manifest is missing")
            require(metadata.optInt("version") == 1 && metadata.optString("sourceSha256") == sha256(document)) { "Markdown package checksum mismatch" }
            val files = metadata.optJSONObject("assets") ?: JSONObject()
            require(assets(document).size == files.length()) { "Markdown package assets are missing" }
            val allowedFiles = files.keys().asSequence().map { "assets/$it" }.toSet() + setOf("document.md", "manifest.json")
            require(stage.walkTopDown().filter { it.isFile }.all {
                it.relativeTo(stage).invariantSeparatorsPath in allowedFiles
            }) { "Markdown package has undeclared resources" }
            assets(document).forEach { (path, file) -> require(sha256(file) == files.getString(path)) { "Markdown asset checksum mismatch" } }
            return commit(stage, root)
        } catch (error: Throwable) { stage.deleteRecursively(); throw error }
    }

    private fun resolveAsset(context: Context, source: String, parentUri: String?, path: String): InputStream? = runCatching {
        if (!BookFileAccess.isContentUri(source)) {
            val root = File(source).canonicalFile.parentFile ?: return@runCatching null
            val asset = File(root, path).canonicalFile
            if (!asset.toPath().startsWith(root.toPath()) || !asset.isFile) return@runCatching null
            return@runCatching asset.inputStream()
        }
        val parent = parentUri?.let(Uri::parse) ?: return@runCatching null
        var current = parent
        for (part in path.split('/')) {
            val id = DocumentsContract.getDocumentId(current)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, id)
            val childId = context.contentResolver.query(children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null)?.use { cursor ->
                var found: String? = null
                while (cursor.moveToNext()) if (cursor.getString(1) == part) { found = cursor.getString(0); break }
                found
            } ?: return@runCatching null
            current = DocumentsContract.buildDocumentUriUsingTree(parent, childId)
        }
        context.contentResolver.openInputStream(current)
    }.getOrNull()

    fun sha256(file: File): String = file.inputStream().use { sha256(it, MAX_PACKAGE) }
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun sha256(input: InputStream, limit: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) { val count = input.read(buffer); if (count < 0) break; total += count
            require(total <= limit) { "Markdown file exceeds size limit" }; digest.update(buffer, 0, count) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun copyBounded(input: InputStream, output: OutputStream, limit: Long): Long {
        var total = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) { val count = input.read(buffer); if (count < 0) break; total += count
            require(total <= limit) { "Markdown package exceeds size limit" }; output.write(buffer, 0, count) }
        return total
    }
}
