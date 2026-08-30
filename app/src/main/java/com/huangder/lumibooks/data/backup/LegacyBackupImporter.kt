package com.huangder.lumibooks.data.backup

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.entity.BookFolderCrossRefEntity
import com.huangder.lumibooks.data.local.entity.BookTagCrossRefEntity
import com.huangder.lumibooks.data.local.entity.BookmarkEntity
import com.huangder.lumibooks.data.local.entity.FolderEntity
import com.huangder.lumibooks.data.local.entity.NoteEntity
import com.huangder.lumibooks.data.local.entity.ReadingRecordEntity
import com.huangder.lumibooks.data.local.entity.TagEntity
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class LegacyBackupConversion(
    val snapshot: PortableSnapshot,
    val assetFiles: Map<String, File>
)

@Singleton
class LegacyBackupImporter @Inject constructor(
    private val dataStoreManager: DataStoreManager,
    private val syncIdentityStore: SyncIdentityStore
) {
    suspend fun convert(extracted: File): LegacyBackupConversion {
        val databaseFile = File(extracted, "database/ebook_reader_database")
        require(databaseFile.isFile) { "Legacy backup does not contain a database" }
        val deviceId = syncIdentityStore.deviceId()
        val now = System.currentTimeMillis()
        val assets = linkedMapOf<String, PortableAsset>()
        val assetFiles = linkedMapOf<String, File>()

        fun addAsset(kind: String, ownerId: String, file: File): String? {
            if (!file.isFile || file.length() <= 0L) return null
            val sha = file.sha256Legacy()
            val extension = file.extension.lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,10}")) } ?: "bin"
            assets.putIfAbsent(sha, PortableAsset(sha, kind, ownerId, "$sha.$extension", sha, file.length()))
            assetFiles.putIfAbsent(sha, file)
            return sha
        }

        val legacyBookFiles = File(extracted, "books").walkTopDown().filter { it.isFile }.toList()
        fun locateBook(oldPath: String): File? {
            val normalized = oldPath.replace('\\', '/')
            val relative = normalized.substringAfterLast("/books/", missingDelimiterValue = "")
            return legacyBookFiles.firstOrNull {
                relative.isNotBlank() && it.relativeTo(File(extracted, "books")).path.replace('\\', '/') == relative
            } ?: legacyBookFiles.firstOrNull { it.name == File(oldPath).name }
        }

        SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val books = db.rows("books") { cursor ->
                val id = cursor.string("id")
                val bodyId = locateBook(cursor.string("filePath"))?.let { addAsset("book", id, it) }
                PortableBook(
                    id = id,
                    title = cursor.string("title"),
                    author = cursor.string("author"),
                    format = cursor.string("format", "TXT"),
                    lastReadTime = cursor.long("lastReadTime"),
                    readingProgress = cursor.float("readingProgress"),
                    locatorJson = cursor.nullableString("locatorJson"),
                    createdAt = cursor.long("createdAt"),
                    isFavorite = cursor.boolean("isFavorite"),
                    isCloudOnly = cursor.boolean("isCloudOnly") && bodyId == null,
                    metadataUpdatedAt = cursor.long("metadataUpdatedAt", cursor.long("createdAt")),
                    bodyAssetId = bodyId,
                    coverAssetId = null,
                    remoteLibraryKey = cursor.nullableString("remoteLibraryKey"),
                    remoteFileName = cursor.nullableString("remoteFileName"),
                    remoteFileSize = cursor.long("remoteFileSize"),
                    remoteFileSha256 = cursor.nullableString("remoteFileSha256")
                )
            }
            val folders = db.rows("folders") { cursor ->
                FolderEntity(
                    id = cursor.string("id"), name = cursor.string("name"),
                    normalizedName = cursor.string("normalizedName"), parentId = cursor.nullableString("parentId"),
                    createdAt = cursor.long("createdAt"), coverPath = null,
                    previewBookIds = cursor.nullableString("previewBookIds"),
                    updatedAt = cursor.long("createdAt")
                )
            }
            val folderLinks = db.rows("book_folder_cross_refs") { cursor ->
                BookFolderCrossRefEntity(cursor.string("bookId"), cursor.string("folderId"), now)
            }
            val tags = db.rows("tags") { cursor ->
                TagEntity(
                    id = cursor.string("id"), name = cursor.string("name"),
                    normalizedName = cursor.string("normalizedName"), createdAt = cursor.long("createdAt"),
                    parentId = cursor.nullableString("parentId"), updatedAt = cursor.long("createdAt")
                )
            }
            val tagLinks = db.rows("book_tag_cross_refs") { cursor ->
                BookTagCrossRefEntity(cursor.string("bookId"), cursor.string("tagId"), now)
            }
            val records = db.rows("reading_records") { cursor ->
                ReadingRecordEntity(
                    bookId = cursor.string("bookId"), date = cursor.string("date"),
                    duration = cursor.long("duration"), startTime = cursor.long("startTime"),
                    endTime = cursor.long("endTime"), sourceDeviceId = deviceId,
                    updatedAt = cursor.long("endTime")
                )
            }
            val bookmarks = db.rows("bookmarks") { cursor ->
                val syncId = legacyId("bookmark", cursor, "bookId", "chapterIndex", "position", "createdAt")
                BookmarkEntity(
                    bookId = cursor.string("bookId"), chapterIndex = cursor.int("chapterIndex"),
                    position = cursor.float("position"), locatorJson = cursor.nullableString("locatorJson"),
                    title = cursor.string("title"), createdAt = cursor.long("createdAt"),
                    syncId = syncId, updatedAt = cursor.long("createdAt")
                )
            }
            val notes = db.rows("notes") { cursor ->
                val syncId = legacyId("note", cursor, "bookId", "chapterIndex", "startPosition", "endPosition", "createdAt")
                NoteEntity(
                    bookId = cursor.string("bookId"), chapterIndex = cursor.int("chapterIndex"),
                    startPosition = cursor.int("startPosition"), endPosition = cursor.int("endPosition"),
                    startLocatorJson = cursor.nullableString("startLocatorJson"),
                    endLocatorJson = cursor.nullableString("endLocatorJson"),
                    selectedText = cursor.string("selectedText"), note = cursor.string("note"),
                    color = cursor.string("color"), createdAt = cursor.long("createdAt"),
                    type = cursor.string("type", "highlight"), syncId = syncId,
                    updatedAt = cursor.long("createdAt")
                )
            }

            val avatarFile = File(extracted, "avatars/avatar.jpg")
            val avatarId = addAsset("avatar", "profile", avatarFile)
            val legacyPreferences = listOf(
                File(extracted, "datastore/settings.preferences_pb"),
                File(extracted, "datastore/settings.preferences")
            ).firstOrNull { it.isFile }
            val preferences = dataStoreManager.readLegacyPortablePreferences(
                legacyPreferences ?: File(extracted, "datastore/settings.preferences_pb"),
                deviceId
            ).map { preference ->
                if (avatarId != null && preference.key == "avatar_uri") {
                    preference.copy(value = "asset://$avatarId")
                } else preference
            }
            return LegacyBackupConversion(
                PortableSnapshot(
                    createdAt = now,
                    sourceDeviceId = deviceId,
                    preferences = preferences,
                    books = books,
                    folders = folders,
                    bookFolderLinks = folderLinks,
                    tags = tags,
                    bookTagLinks = tagLinks,
                    readingRecords = records,
                    bookmarks = bookmarks,
                    notes = notes,
                    tombstones = emptyList(),
                    assets = assets.values.toList()
                ),
                assetFiles
            )
        }
    }

    private fun <T> SQLiteDatabase.rows(table: String, mapper: (Cursor) -> T): List<T> =
        if (!hasTable(table)) emptyList() else rawQuery("SELECT * FROM `$table`", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(mapper(cursor)) }
        }

    private fun SQLiteDatabase.hasTable(table: String): Boolean = rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
        arrayOf(table)
    ).use { it.moveToFirst() }

    private fun legacyId(prefix: String, cursor: Cursor, vararg columns: String): String {
        val raw = buildString {
            append(prefix)
            columns.forEach { column -> append('|').append(cursor.string(column)) }
        }
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

private fun Cursor.index(name: String): Int = getColumnIndex(name)
private fun Cursor.string(name: String, default: String = ""): String =
    index(name).takeIf { it >= 0 && !isNull(it) }?.let(::getString) ?: default
private fun Cursor.nullableString(name: String): String? =
    index(name).takeIf { it >= 0 && !isNull(it) }?.let(::getString)?.takeIf { it.isNotBlank() }
private fun Cursor.long(name: String, default: Long = 0L): Long =
    index(name).takeIf { it >= 0 && !isNull(it) }?.let(::getLong) ?: default
private fun Cursor.int(name: String, default: Int = 0): Int =
    index(name).takeIf { it >= 0 && !isNull(it) }?.let(::getInt) ?: default
private fun Cursor.float(name: String, default: Float = 0f): Float =
    index(name).takeIf { it >= 0 && !isNull(it) }?.let(::getFloat) ?: default
private fun Cursor.boolean(name: String): Boolean = int(name) != 0

private fun File.sha256Legacy(): String = inputStream().buffered().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}
