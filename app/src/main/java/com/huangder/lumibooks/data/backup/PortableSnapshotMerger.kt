package com.huangder.lumibooks.data.backup

import com.huangder.lumibooks.data.local.entity.BookFolderCrossRefEntity
import com.huangder.lumibooks.data.local.entity.BookTagCrossRefEntity
import com.huangder.lumibooks.data.local.entity.BookmarkEntity
import com.huangder.lumibooks.data.local.entity.FolderEntity
import com.huangder.lumibooks.data.local.entity.NoteEntity
import com.huangder.lumibooks.data.local.entity.ReadingRecordEntity
import com.huangder.lumibooks.data.local.entity.SyncTombstoneEntity
import com.huangder.lumibooks.data.local.entity.TagEntity

object PortableSnapshotMerger {
    fun merge(local: PortableSnapshot, remote: PortableSnapshot): PortableSnapshot {
        val tombstones = mergeLatest(
            local.tombstones,
            remote.tombstones,
            key = { "${it.namespace}:${it.itemId}" },
            updatedAt = { it.deletedAt },
            deviceId = { it.deviceId }
        )
        val tombstoneMap = tombstones.associateBy { "${it.namespace}:${it.itemId}" }
        val tags = mergeLive(
            local.tags,
            remote.tags,
            "tag",
            { it.id },
            { it.updatedAt },
            tombstoneMap
        ).distinctByLatest({ it.normalizedName }, { it.updatedAt })
        val validTagIds = tags.mapTo(mutableSetOf()) { it.id }
        val folders = mergeLive(
            local.folders,
            remote.folders,
            "folder",
            { it.id },
            { it.updatedAt },
            tombstoneMap
        )
        val validFolderIds = folders.mapTo(mutableSetOf()) { it.id }

        val books = mergeBooks(local.books, remote.books, tombstoneMap)
        val validBookIds = books.mapTo(mutableSetOf()) { it.id }
        val folderLinks = mergeLive(
            local.bookFolderLinks,
            remote.bookFolderLinks,
            "book_folder",
            { it.bookId },
            { it.updatedAt },
            tombstoneMap
        ).filter { it.bookId in validBookIds && it.folderId in validFolderIds }
        val tagLinks = mergeLive(
            local.bookTagLinks,
            remote.bookTagLinks,
            "book_tag",
            { "${it.bookId}:${it.tagId}" },
            { it.updatedAt },
            tombstoneMap
        ).filter { it.bookId in validBookIds && it.tagId in validTagIds }

        return PortableSnapshot(
            createdAt = maxOf(local.createdAt, remote.createdAt),
            sourceDeviceId = minOf(local.sourceDeviceId, remote.sourceDeviceId),
            preferences = mergeLatest(
                local.preferences,
                remote.preferences,
                { it.key },
                { it.updatedAt },
                { it.deviceId }
            ),
            books = books,
            folders = folders,
            bookFolderLinks = folderLinks,
            tags = tags,
            bookTagLinks = tagLinks,
            readingRecords = mergeReadingRecords(local.readingRecords, remote.readingRecords)
                .filter { it.bookId in validBookIds },
            bookmarks = mergeLive(
                local.bookmarks,
                remote.bookmarks,
                "bookmark",
                { it.syncId },
                { it.updatedAt },
                tombstoneMap
            ).filter { it.bookId in validBookIds },
            notes = mergeLive(
                local.notes,
                remote.notes,
                "note",
                { it.syncId },
                { it.updatedAt },
                tombstoneMap
            ).filter { it.bookId in validBookIds },
            tombstones = tombstones,
            assets = mergeLatest(
                local.assets,
                remote.assets,
                { it.id },
                { 0L },
                { it.toString() }
            )
        )
    }

    private fun mergeBooks(
        local: List<PortableBook>,
        remote: List<PortableBook>,
        tombstones: Map<String, SyncTombstoneEntity>
    ): List<PortableBook> {
        val left = local.associateBy { it.id }
        val right = remote.associateBy { it.id }
        return (left.keys + right.keys).mapNotNull { id ->
            val localBook = left[id]
            val remoteBook = right[id]
            val merged = when {
                localBook == null -> remoteBook
                remoteBook == null -> localBook
                else -> {
                    val metadataWinner = listOf(localBook, remoteBook).maxWith(
                        compareBy<PortableBook> { it.metadataUpdatedAt }.thenBy { it.toString() }
                    )
                    val progressWinner = listOf(localBook, remoteBook).maxWith(
                        compareBy<PortableBook> { it.lastReadTime }.thenBy { it.toString() }
                    )
                    metadataWinner.copy(
                        lastReadTime = progressWinner.lastReadTime,
                        readingProgress = progressWinner.readingProgress,
                        locatorJson = progressWinner.locatorJson,
                        bodyAssetId = localBook.bodyAssetId ?: remoteBook.bodyAssetId,
                        isCloudOnly = localBook.bodyAssetId == null && remoteBook.bodyAssetId == null,
                        remoteLibraryKey = localBook.remoteLibraryKey ?: remoteBook.remoteLibraryKey,
                        remoteFileName = localBook.remoteFileName ?: remoteBook.remoteFileName,
                        remoteFileSize = maxOf(localBook.remoteFileSize, remoteBook.remoteFileSize),
                        remoteFileSha256 = localBook.remoteFileSha256 ?: remoteBook.remoteFileSha256
                    )
                }
            } ?: return@mapNotNull null
            val tombstone = tombstones["book:$id"]
            merged.takeUnless { tombstone != null && tombstone.deletedAt >= maxOf(it.metadataUpdatedAt, it.lastReadTime) }
        }
    }

    private fun mergeReadingRecords(
        local: List<ReadingRecordEntity>,
        remote: List<ReadingRecordEntity>
    ): List<ReadingRecordEntity> {
        val merged = linkedMapOf<String, ReadingRecordEntity>()
        for (item in local + remote) {
            val key = "${item.bookId}:${item.date}:${item.sourceDeviceId}"
            val old = merged[key]
            if (old == null) {
                merged[key] = item
            } else {
                val latest = if (isNewer(item.updatedAt, item.sourceDeviceId, old.updatedAt, old.sourceDeviceId)) item else old
                merged[key] = latest.copy(
                    id = 0,
                    duration = maxOf(old.duration, item.duration),
                    startTime = minOf(old.startTime, item.startTime),
                    endTime = maxOf(old.endTime, item.endTime),
                    updatedAt = maxOf(old.updatedAt, item.updatedAt)
                )
            }
        }
        return merged.values.toList()
    }

    private fun <T> mergeLive(
        local: List<T>,
        remote: List<T>,
        namespace: String,
        key: (T) -> String,
        updatedAt: (T) -> Long,
        tombstones: Map<String, SyncTombstoneEntity>
    ): List<T> = mergeLatest(local, remote, key, updatedAt) { "" }
        .filter { item ->
            val deleted = tombstones["$namespace:${key(item)}"]
            deleted == null || deleted.deletedAt < updatedAt(item)
        }

    private fun <T> mergeLatest(
        local: List<T>,
        remote: List<T>,
        key: (T) -> String,
        updatedAt: (T) -> Long,
        deviceId: (T) -> String
    ): List<T> {
        val merged = linkedMapOf<String, T>()
        for (item in local + remote) {
            val itemKey = key(item)
            val old = merged[itemKey]
            if (
                old == null ||
                isNewer(updatedAt(item), deviceId(item), updatedAt(old), deviceId(old)) ||
                (updatedAt(item) == updatedAt(old) && deviceId(item) == deviceId(old) &&
                    item.toString() > old.toString())
            ) {
                merged[itemKey] = item
            }
        }
        return merged.values.toList()
    }

    private fun isNewer(time: Long, device: String, otherTime: Long, otherDevice: String): Boolean =
        time > otherTime || (time == otherTime && device > otherDevice)

    private fun <T, K> List<T>.distinctByLatest(key: (T) -> K, updatedAt: (T) -> Long): List<T> =
        groupBy(key).values.map { candidates ->
            candidates.maxWith(compareBy<T> { updatedAt(it) }.thenBy { it.toString() })
        }
}
