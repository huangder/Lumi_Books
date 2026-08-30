package com.huangder.lumibooks.data.backup

import com.huangder.lumibooks.data.local.entity.BookFolderCrossRefEntity
import com.huangder.lumibooks.data.local.entity.BookTagCrossRefEntity
import com.huangder.lumibooks.data.local.entity.BookmarkEntity
import com.huangder.lumibooks.data.local.entity.FolderEntity
import com.huangder.lumibooks.data.local.entity.NoteEntity
import com.huangder.lumibooks.data.local.entity.ReadingRecordEntity
import com.huangder.lumibooks.data.local.entity.SyncTombstoneEntity
import com.huangder.lumibooks.data.local.entity.TagEntity
import org.json.JSONArray
import org.json.JSONObject

data class PortableAsset(
    val id: String,
    val kind: String,
    val ownerId: String,
    val fileName: String,
    val sha256: String,
    val sizeBytes: Long
) {
    val archivePath: String get() = "assets/$fileName"
}

data class PortableBook(
    val id: String,
    val title: String,
    val author: String,
    val format: String,
    val lastReadTime: Long,
    val readingProgress: Float,
    val locatorJson: String?,
    val createdAt: Long,
    val isFavorite: Boolean,
    val isCloudOnly: Boolean,
    val metadataUpdatedAt: Long,
    val bodyAssetId: String?,
    val coverAssetId: String?,
    val remoteLibraryKey: String?,
    val remoteFileName: String?,
    val remoteFileSize: Long,
    val remoteFileSha256: String?
)

data class PortableSnapshot(
    val schemaVersion: Int = CURRENT_VERSION,
    val createdAt: Long,
    val sourceDeviceId: String,
    val preferences: List<PortablePreference>,
    val books: List<PortableBook>,
    val folders: List<FolderEntity>,
    val bookFolderLinks: List<BookFolderCrossRefEntity>,
    val tags: List<TagEntity>,
    val bookTagLinks: List<BookTagCrossRefEntity>,
    val readingRecords: List<ReadingRecordEntity>,
    val bookmarks: List<BookmarkEntity>,
    val notes: List<NoteEntity>,
    val tombstones: List<SyncTombstoneEntity>,
    val assets: List<PortableAsset>
) {
    fun toJson(): String = JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("createdAt", createdAt)
        put("sourceDeviceId", sourceDeviceId)
        put("preferences", preferences.toJsonArray { it.toJson() })
        put("books", books.toJsonArray(::bookToJson))
        put("folders", folders.toJsonArray(::folderToJson))
        put("bookFolderLinks", bookFolderLinks.toJsonArray(::bookFolderLinkToJson))
        put("tags", tags.toJsonArray(::tagToJson))
        put("bookTagLinks", bookTagLinks.toJsonArray(::bookTagLinkToJson))
        put("readingRecords", readingRecords.toJsonArray(::readingRecordToJson))
        put("bookmarks", bookmarks.toJsonArray(::bookmarkToJson))
        put("notes", notes.toJsonArray(::noteToJson))
        put("tombstones", tombstones.toJsonArray(::tombstoneToJson))
        put("assets", assets.toJsonArray(::assetToJson))
    }.toString()

    companion object {
        const val CURRENT_VERSION = 1

        fun fromJson(raw: String): PortableSnapshot {
            val root = JSONObject(raw)
            val version = root.optInt("schemaVersion", 0)
            require(version in 1..CURRENT_VERSION) { "Unsupported backup schema version: $version" }
            return PortableSnapshot(
                schemaVersion = version,
                createdAt = root.optLong("createdAt"),
                sourceDeviceId = root.optString("sourceDeviceId"),
                preferences = root.array("preferences").mapObjects(PortablePreference::fromJson),
                books = root.array("books").mapObjects(::bookFromJson),
                folders = root.array("folders").mapObjects(::folderFromJson),
                bookFolderLinks = root.array("bookFolderLinks").mapObjects(::bookFolderLinkFromJson),
                tags = root.array("tags").mapObjects(::tagFromJson),
                bookTagLinks = root.array("bookTagLinks").mapObjects(::bookTagLinkFromJson),
                readingRecords = root.array("readingRecords").mapObjects(::readingRecordFromJson),
                bookmarks = root.array("bookmarks").mapObjects(::bookmarkFromJson),
                notes = root.array("notes").mapObjects(::noteFromJson),
                tombstones = root.array("tombstones").mapObjects(::tombstoneFromJson),
                assets = root.array("assets").mapObjects(::assetFromJson)
            )
        }
    }
}

private fun bookToJson(book: PortableBook) = JSONObject().apply {
    put("id", book.id)
    put("title", book.title)
    put("author", book.author)
    put("format", book.format)
    put("lastReadTime", book.lastReadTime)
    put("readingProgress", book.readingProgress.toDouble())
    putNullable("locatorJson", book.locatorJson)
    put("createdAt", book.createdAt)
    put("isFavorite", book.isFavorite)
    put("isCloudOnly", book.isCloudOnly)
    put("metadataUpdatedAt", book.metadataUpdatedAt)
    putNullable("bodyAssetId", book.bodyAssetId)
    putNullable("coverAssetId", book.coverAssetId)
    putNullable("remoteLibraryKey", book.remoteLibraryKey)
    putNullable("remoteFileName", book.remoteFileName)
    put("remoteFileSize", book.remoteFileSize)
    putNullable("remoteFileSha256", book.remoteFileSha256)
}

private fun bookFromJson(json: JSONObject) = PortableBook(
    id = json.getString("id"),
    title = json.optString("title"),
    author = json.optString("author"),
    format = json.optString("format", "TXT"),
    lastReadTime = json.optLong("lastReadTime"),
    readingProgress = json.optDouble("readingProgress").toFloat(),
    locatorJson = json.nullableString("locatorJson"),
    createdAt = json.optLong("createdAt"),
    isFavorite = json.optBoolean("isFavorite"),
    isCloudOnly = json.optBoolean("isCloudOnly"),
    metadataUpdatedAt = json.optLong("metadataUpdatedAt", json.optLong("createdAt")),
    bodyAssetId = json.nullableString("bodyAssetId"),
    coverAssetId = json.nullableString("coverAssetId"),
    remoteLibraryKey = json.nullableString("remoteLibraryKey"),
    remoteFileName = json.nullableString("remoteFileName"),
    remoteFileSize = json.optLong("remoteFileSize"),
    remoteFileSha256 = json.nullableString("remoteFileSha256")
)

private fun folderToJson(item: FolderEntity) = JSONObject().apply {
    put("id", item.id); put("name", item.name); put("normalizedName", item.normalizedName)
    putNullable("parentId", item.parentId); put("createdAt", item.createdAt)
    putNullable("coverPath", item.coverPath); putNullable("previewBookIds", item.previewBookIds)
    put("updatedAt", item.updatedAt)
}

private fun folderFromJson(json: JSONObject) = FolderEntity(
    id = json.getString("id"), name = json.optString("name"),
    normalizedName = json.optString("normalizedName"), parentId = json.nullableString("parentId"),
    createdAt = json.optLong("createdAt"), coverPath = json.nullableString("coverPath"),
    previewBookIds = json.nullableString("previewBookIds"),
    updatedAt = json.optLong("updatedAt", json.optLong("createdAt"))
)

private fun bookFolderLinkToJson(item: BookFolderCrossRefEntity) = JSONObject().apply {
    put("bookId", item.bookId); put("folderId", item.folderId); put("updatedAt", item.updatedAt)
}

private fun bookFolderLinkFromJson(json: JSONObject) = BookFolderCrossRefEntity(
    json.getString("bookId"), json.getString("folderId"), json.optLong("updatedAt")
)

private fun tagToJson(item: TagEntity) = JSONObject().apply {
    put("id", item.id); put("name", item.name); put("normalizedName", item.normalizedName)
    put("createdAt", item.createdAt); putNullable("parentId", item.parentId); put("updatedAt", item.updatedAt)
}

private fun tagFromJson(json: JSONObject) = TagEntity(
    id = json.getString("id"), name = json.optString("name"),
    normalizedName = json.optString("normalizedName"), createdAt = json.optLong("createdAt"),
    parentId = json.nullableString("parentId"), updatedAt = json.optLong("updatedAt", json.optLong("createdAt"))
)

private fun bookTagLinkToJson(item: BookTagCrossRefEntity) = JSONObject().apply {
    put("bookId", item.bookId); put("tagId", item.tagId); put("updatedAt", item.updatedAt)
}

private fun bookTagLinkFromJson(json: JSONObject) = BookTagCrossRefEntity(
    json.getString("bookId"), json.getString("tagId"), json.optLong("updatedAt")
)

private fun readingRecordToJson(item: ReadingRecordEntity) = JSONObject().apply {
    put("bookId", item.bookId); put("date", item.date); put("duration", item.duration)
    put("startTime", item.startTime); put("endTime", item.endTime)
    put("sourceDeviceId", item.sourceDeviceId); put("updatedAt", item.updatedAt)
}

private fun readingRecordFromJson(json: JSONObject) = ReadingRecordEntity(
    bookId = json.getString("bookId"), date = json.getString("date"), duration = json.optLong("duration"),
    startTime = json.optLong("startTime"), endTime = json.optLong("endTime"),
    sourceDeviceId = json.optString("sourceDeviceId"), updatedAt = json.optLong("updatedAt")
)

private fun bookmarkToJson(item: BookmarkEntity) = JSONObject().apply {
    put("bookId", item.bookId); put("chapterIndex", item.chapterIndex); put("position", item.position.toDouble())
    putNullable("locatorJson", item.locatorJson); put("title", item.title); put("createdAt", item.createdAt)
    put("syncId", item.syncId); put("updatedAt", item.updatedAt)
}

private fun bookmarkFromJson(json: JSONObject) = BookmarkEntity(
    bookId = json.getString("bookId"), chapterIndex = json.optInt("chapterIndex"),
    position = json.optDouble("position").toFloat(), locatorJson = json.nullableString("locatorJson"),
    title = json.optString("title"), createdAt = json.optLong("createdAt"), syncId = json.optString("syncId"),
    updatedAt = json.optLong("updatedAt", json.optLong("createdAt"))
)

private fun noteToJson(item: NoteEntity) = JSONObject().apply {
    put("bookId", item.bookId); put("chapterIndex", item.chapterIndex)
    put("startPosition", item.startPosition); put("endPosition", item.endPosition)
    putNullable("startLocatorJson", item.startLocatorJson); putNullable("endLocatorJson", item.endLocatorJson)
    put("selectedText", item.selectedText); put("note", item.note); put("color", item.color)
    put("createdAt", item.createdAt); put("type", item.type); put("syncId", item.syncId)
    put("updatedAt", item.updatedAt)
}

private fun noteFromJson(json: JSONObject) = NoteEntity(
    bookId = json.getString("bookId"), chapterIndex = json.optInt("chapterIndex"),
    startPosition = json.optInt("startPosition"), endPosition = json.optInt("endPosition"),
    startLocatorJson = json.nullableString("startLocatorJson"),
    endLocatorJson = json.nullableString("endLocatorJson"), selectedText = json.optString("selectedText"),
    note = json.optString("note"), color = json.optString("color"), createdAt = json.optLong("createdAt"),
    type = json.optString("type", "highlight"), syncId = json.optString("syncId"),
    updatedAt = json.optLong("updatedAt", json.optLong("createdAt"))
)

private fun tombstoneToJson(item: SyncTombstoneEntity) = JSONObject().apply {
    put("namespace", item.namespace); put("itemId", item.itemId); put("deletedAt", item.deletedAt)
    put("deviceId", item.deviceId)
}

private fun tombstoneFromJson(json: JSONObject) = SyncTombstoneEntity(
    json.getString("namespace"), json.getString("itemId"), json.optLong("deletedAt"),
    json.optString("deviceId")
)

private fun assetToJson(item: PortableAsset) = JSONObject().apply {
    put("id", item.id); put("kind", item.kind); put("ownerId", item.ownerId); put("fileName", item.fileName)
    put("sha256", item.sha256); put("sizeBytes", item.sizeBytes)
}

private fun assetFromJson(json: JSONObject) = PortableAsset(
    id = json.getString("id"), kind = json.optString("kind"), ownerId = json.optString("ownerId"),
    fileName = json.getString("fileName"), sha256 = json.getString("sha256"), sizeBytes = json.optLong("sizeBytes")
)

private inline fun <T> List<T>.toJsonArray(transform: (T) -> JSONObject): JSONArray =
    JSONArray().also { array -> forEach { array.put(transform(it)) } }

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> = buildList {
    for (index in 0 until length()) add(transform(getJSONObject(index)))
}

private fun JSONObject.array(key: String): JSONArray = optJSONArray(key) ?: JSONArray()

private fun JSONObject.putNullable(key: String, value: String?) {
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
}

private fun JSONObject.nullableString(key: String): String? =
    takeIf { has(key) && !isNull(key) }?.optString(key)?.takeIf { it.isNotEmpty() }
