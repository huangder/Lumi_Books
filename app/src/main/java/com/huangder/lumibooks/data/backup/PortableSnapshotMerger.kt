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
    fun merge(localSnapshot: PortableSnapshot, remoteSnapshot: PortableSnapshot): PortableSnapshot {
        val local = localSnapshot.flattenLegacyTagHierarchy()
        val remote = remoteSnapshot.flattenLegacyTagHierarchy()
        val groups = mergeLatest(local.tagGroups, remote.tagGroups, { it.id }, { it.updatedAt },
            { if (it.isDeleted) "1" else "0" })
        val bookGroups = groups.filter { !it.isDeleted && it.type == "book" }.mapTo(mutableSetOf()) { it.id }
        val annotationGroups = groups.filter { !it.isDeleted && it.type == "annotation" }.mapTo(mutableSetOf()) { it.id }
        val knownBookAssignments = mergeLatest(
            local.tags.filter { local.hasTagGroups && it.id !in local.bookTagsWithoutGroupId },
            remote.tags.filter { remote.hasTagGroups && it.id !in remote.bookTagsWithoutGroupId },
            { it.id }, { it.updatedAt }, { "" }
        ).associateBy { it.id }
        val knownAnnotationAssignments = mergeLatest(
            local.annotationTags.filter { local.hasTagGroups && it.name !in local.annotationTagsWithoutGroupId },
            remote.annotationTags.filter { remote.hasTagGroups && it.name !in remote.annotationTagsWithoutGroupId },
            { it.name }, { it.updatedAt }, { if (it.isDeleted) "1" else "0" }
        ).associateBy { it.name }
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
        ).distinctByLatest({ it.normalizedName }, { it.updatedAt }).map { tag ->
            val groupId = knownBookAssignments[tag.id]?.groupId
            tag.copy(groupId = groupId?.takeIf { it in bookGroups })
        }
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
            tagGroups = groups,
            hasTagGroups = local.hasTagGroups || remote.hasTagGroups,
            bookTagsWithoutGroupId = if (!local.hasTagGroups && !remote.hasTagGroups) emptySet() else
                tags.filterNot { it.id in knownBookAssignments }.mapTo(mutableSetOf()) { it.id },
            annotationTagsWithoutGroupId = if (!local.hasTagGroups && !remote.hasTagGroups) emptySet() else
                (local.annotationTags + remote.annotationTags)
                    .filterNot { it.name in knownAnnotationAssignments }.mapTo(mutableSetOf()) { it.name },
            annotationTags = mergeLatest(
                local.annotationTags, remote.annotationTags, { it.name }, { it.updatedAt },
                { if (it.isDeleted) "1" else "0" }
            ).distinctByLatest({ it.name }, { it.updatedAt }).map { tag ->
                val groupId = knownAnnotationAssignments[tag.name]?.groupId
                tag.copy(groupId = groupId?.takeIf { it in annotationGroups })
            },
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
            ),
            highlightRules = mergeLatest(
                local.highlightRules, remote.highlightRules, { it.id }, { it.updatedAt }, { it.rawJson }
            ),
            bookHighlightRuleStates = (local.bookHighlightRuleStates + remote.bookHighlightRuleStates)
                .distinctBy { "${it.bookId}:${it.ruleId}" },
            bookHighlightSettings = (local.bookHighlightSettings + remote.bookHighlightSettings)
                .groupBy { it.bookId }.map { (_, values) -> values.maxBy { it.revision } },
            highlightRuleExclusions = (local.highlightRuleExclusions + remote.highlightRuleExclusions)
                .distinctBy { "${it.bookId}:${it.ruleId}:${it.matchKey}" }
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
                        isManuallyMarkedRead = progressWinner.isManuallyMarkedRead,
                        isHidden = metadataWinner.isHidden,
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
                    listeningDuration = maxOf(old.listeningDuration, item.listeningDuration),
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
