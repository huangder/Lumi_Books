package com.huangder.lumibooks.data.local.dao

import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.huangder.lumibooks.data.local.entity.BookFolderCrossRefEntity
import com.huangder.lumibooks.data.local.entity.FolderEntity
import com.huangder.lumibooks.domain.model.FolderMoveResult
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FolderDao {
    @Query("SELECT * FROM folders ORDER BY createdAt ASC")
    abstract fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM book_folder_cross_refs")
    abstract fun getAllBookFolderLinks(): Flow<List<BookFolderCrossRefEntity>>

    @Query("SELECT * FROM folders WHERE id = :folderId LIMIT 1")
    abstract suspend fun getFolderById(folderId: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE storageDocumentUri = :uri LIMIT 1")
    abstract suspend fun getFolderByStorageUri(uri: String): FolderEntity?

    @Query(
        "SELECT * FROM folders WHERE normalizedName = :normalizedName " +
            "AND ((parentId IS NULL AND :parentId IS NULL) OR parentId = :parentId) LIMIT 1"
    )
    abstract suspend fun getFolderByNormalizedName(
        normalizedName: String,
        parentId: String?
    ): FolderEntity?

    @Insert
    abstract suspend fun insertFolder(folder: FolderEntity)

    @Query(
        "UPDATE folders SET name = :name, normalizedName = :normalizedName, updatedAt = :updatedAt " +
            "WHERE id = :folderId"
    )
    abstract suspend fun updateFolderName(
        folderId: String,
        name: String,
        normalizedName: String,
        updatedAt: Long
    )

    @Query("UPDATE folders SET coverPath = :coverPath, updatedAt = :updatedAt WHERE id = :folderId")
    abstract suspend fun updateFolderCover(folderId: String, coverPath: String?, updatedAt: Long): Int

    open suspend fun updateFolderCover(folderId: String, coverPath: String?): Int =
        updateFolderCover(folderId, coverPath, System.currentTimeMillis())

    @Query(
        "UPDATE folders SET storageTreeUri = :treeUri, storageDocumentUri = :documentUri, " +
            "storageParentUri = :parentUri, storageMissing = 0, updatedAt = :updatedAt " +
            "WHERE id = :folderId"
    )
    abstract suspend fun updateFolderStorage(
        folderId: String,
        treeUri: String?,
        documentUri: String?,
        parentUri: String?,
        updatedAt: Long
    ): Int

    @Query(
        "UPDATE folders SET name = :name, normalizedName = :normalizedName, " +
            "storageTreeUri = :treeUri, storageDocumentUri = :documentUri, " +
            "storageParentUri = :parentUri, storageMissing = :storageMissing, updatedAt = :updatedAt " +
            "WHERE id = :folderId"
    )
    abstract suspend fun updateFolderStorageState(
        folderId: String,
        name: String,
        normalizedName: String,
        treeUri: String?,
        documentUri: String?,
        parentUri: String?,
        storageMissing: Boolean,
        updatedAt: Long
    ): Int

    @Query("UPDATE folders SET storageMissing = :missing, updatedAt = :updatedAt WHERE id = :folderId")
    abstract suspend fun updateFolderStorageMissing(folderId: String, missing: Boolean, updatedAt: Long): Int

    @Query(
        "UPDATE folders SET previewBookIds = :previewBookIds, updatedAt = :updatedAt " +
            "WHERE id = :folderId AND previewBookIds IS NULL " +
            "AND length(trim(:previewBookIds)) > 2"
    )
    abstract suspend fun initializeFolderPreviewIfUnset(
        folderId: String,
        previewBookIds: String,
        updatedAt: Long
    ): Int

    open suspend fun initializeFolderPreviewIfUnset(folderId: String, previewBookIds: String): Int =
        initializeFolderPreviewIfUnset(folderId, previewBookIds, System.currentTimeMillis())

    @Query(
        "UPDATE folders SET previewBookIds = :previewBookIds, updatedAt = :updatedAt " +
            "WHERE id = :folderId AND (" +
            "(previewBookIds IS NULL AND :previewBookIds IS NOT NULL) OR " +
            "(previewBookIds IS NOT NULL AND :previewBookIds IS NULL) OR " +
            "previewBookIds != :previewBookIds)"
    )
    abstract suspend fun updateFolderPreviewIfChanged(
        folderId: String,
        previewBookIds: String?,
        updatedAt: Long
    ): Int

    open suspend fun updateFolderPreviewIfChanged(folderId: String, previewBookIds: String?): Int =
        updateFolderPreviewIfChanged(folderId, previewBookIds, System.currentTimeMillis())

    @Query("UPDATE folders SET parentId = :parentId, updatedAt = :updatedAt WHERE id = :folderId")
    abstract suspend fun updateFolderParent(folderId: String, parentId: String?, updatedAt: Long)

    @Query("UPDATE folders SET parentId = :parentId, storageParentUri = :storageParentUri, storageMissing = 0, updatedAt = :updatedAt WHERE id = :folderId")
    abstract suspend fun reconcileFolderParent(folderId: String, parentId: String?, storageParentUri: String?, updatedAt: Long): Int

    @Query(
        "WITH RECURSIVE folder_tree(id) AS (SELECT id FROM folders WHERE id = :folderId " +
            "UNION ALL SELECT child.id FROM folders child JOIN folder_tree parent " +
            "ON child.parentId = parent.id) SELECT id FROM folder_tree"
    )
    abstract suspend fun getFolderTreeIds(folderId: String): List<String>

    @Query(
        "WITH RECURSIVE folder_tree(id) AS (SELECT id FROM folders WHERE id = :folderId " +
            "UNION ALL SELECT child.id FROM folders child JOIN folder_tree parent " +
            "ON child.parentId = parent.id) SELECT links.* FROM book_folder_cross_refs links " +
            "WHERE links.folderId IN (SELECT id FROM folder_tree)"
    )
    abstract suspend fun getBookLinksInTree(folderId: String): List<BookFolderCrossRefEntity>

    @Query(
        "WITH RECURSIVE folder_tree(id, coverPath) AS (" +
            "SELECT id, coverPath FROM folders WHERE id = :folderId " +
            "UNION ALL " +
            "SELECT child.id, child.coverPath FROM folders child " +
            "JOIN folder_tree parent ON child.parentId = parent.id) " +
            "SELECT coverPath FROM folder_tree WHERE coverPath IS NOT NULL"
    )
    abstract suspend fun getCoverPathsInTree(folderId: String): List<String>

    @Query("DELETE FROM folders WHERE id = :folderId")
    protected abstract suspend fun deleteFolderRow(folderId: String)

    @Upsert
    abstract suspend fun upsertBookFolderLinks(links: List<BookFolderCrossRefEntity>)

    @Query("DELETE FROM book_folder_cross_refs WHERE bookId IN (:bookIds)")
    abstract suspend fun deleteBookFolderLinks(bookIds: Set<String>)

    @Transaction
    open suspend fun createFolderIfAvailable(folder: FolderEntity): FolderEntity? {
        if (getFolderByNormalizedName(folder.normalizedName, folder.parentId) != null) return null
        if (folder.parentId != null && getFolderById(folder.parentId) == null) return null
        insertFolder(folder)
        return folder
    }

    @Transaction
    open suspend fun getOrCreateRootFolder(folder: FolderEntity): FolderEntity {
        getFolderByNormalizedName(folder.normalizedName, null)?.let { return it }
        insertFolder(folder)
        return folder
    }

    /**
     * Gets or creates every folder in an authorized source path atomically. The first entity is
     * always the root folder; child entities are rebound to the ID of the folder that was found
     * or created at the previous level so an existing root can be safely reused.
     */
    @Transaction
    open suspend fun getOrCreateFolderPath(path: List<FolderEntity>): FolderEntity {
        require(path.isNotEmpty()) { "Folder path cannot be empty" }
        val first = path.first()
        var current = first.storageDocumentUri?.let { uri -> getFolderByStorageUri(uri) }
        if (current == null) {
            val sameName = getFolderByNormalizedName(first.normalizedName, null)
            current = when (val existing = sameName) {
                null -> getOrCreateRootFolder(first)
                else -> when {
                    existing.storageDocumentUri == null || first.storageDocumentUri == null -> {
                        if (first.storageDocumentUri != null) {
                            updateFolderStorage(
                                existing.id,
                                first.storageTreeUri,
                                first.storageDocumentUri,
                                first.storageParentUri,
                                System.currentTimeMillis()
                            )
                            existing.copy(
                                storageTreeUri = first.storageTreeUri,
                                storageDocumentUri = first.storageDocumentUri,
                                storageParentUri = first.storageParentUri,
                                storageMissing = false
                            )
                        } else {
                            existing
                        }
                    }
                    sameStorageDocument(existing.storageDocumentUri, first.storageDocumentUri) -> {
                        if (existing.storageDocumentUri != first.storageDocumentUri) {
                            updateFolderStorage(
                                existing.id,
                                first.storageTreeUri,
                                first.storageDocumentUri,
                                first.storageParentUri,
                                System.currentTimeMillis()
                            )
                        }
                        existing.copy(
                            storageTreeUri = first.storageTreeUri,
                            storageDocumentUri = first.storageDocumentUri,
                            storageParentUri = first.storageParentUri
                        )
                    }
                    // Same display name, but a different authorized tree. Keep a distinct
                    // physical root instead of attaching its children to the first tree's folder.
                    else -> first.also { insertFolder(it) }
                }
            }
        }
        var currentFolder = checkNotNull(current)
        for (proposed in path.drop(1)) {
            val byStorage = proposed.storageDocumentUri?.let { uri -> getFolderByStorageUri(uri) }
            if (byStorage != null) {
                currentFolder = byStorage
                continue
            }
            val sameName = getFolderByNormalizedName(proposed.normalizedName, currentFolder.id)
            currentFolder = when (val existing = sameName) {
                null -> proposed.copy(parentId = currentFolder.id).also { insertFolder(it) }
                else -> when {
                    existing.storageDocumentUri == null || proposed.storageDocumentUri == null -> {
                        if (proposed.storageDocumentUri != null) {
                            updateFolderStorage(
                                existing.id,
                                proposed.storageTreeUri,
                                proposed.storageDocumentUri,
                                proposed.storageParentUri,
                                System.currentTimeMillis()
                            )
                            existing.copy(
                                storageTreeUri = proposed.storageTreeUri,
                                storageDocumentUri = proposed.storageDocumentUri,
                                storageParentUri = proposed.storageParentUri,
                                storageMissing = false
                            )
                        } else {
                            existing
                        }
                    }
                    sameStorageDocument(existing.storageDocumentUri, proposed.storageDocumentUri) -> {
                        if (existing.storageDocumentUri != proposed.storageDocumentUri) {
                            updateFolderStorage(
                                existing.id,
                                proposed.storageTreeUri,
                                proposed.storageDocumentUri,
                                proposed.storageParentUri,
                                System.currentTimeMillis()
                            )
                        }
                        existing.copy(
                            storageTreeUri = proposed.storageTreeUri,
                            storageDocumentUri = proposed.storageDocumentUri,
                            storageParentUri = proposed.storageParentUri
                        )
                    }
                    // A same-named directory can exist in another physical tree. Do not merge it
                    // into the current parent just because its display name happens to match.
                    else -> proposed.copy(parentId = currentFolder.id).also { insertFolder(it) }
                }
            }
        }
        return currentFolder
    }

    private fun sameStorageDocument(first: String?, second: String?): Boolean {
        if (first.isNullOrBlank() || second.isNullOrBlank()) return first == second
        if (first == second) return true
        return runCatching {
            val left = Uri.parse(first)
            val right = Uri.parse(second)
            left.authority == right.authority &&
                DocumentsContract.getDocumentId(left) == DocumentsContract.getDocumentId(right)
        }.getOrDefault(false)
    }

    @Transaction
    open suspend fun renameFolderIfAvailable(
        folderId: String,
        name: String,
        normalizedName: String
    ): Boolean {
        val folder = getFolderById(folderId) ?: return false
        val conflict = getFolderByNormalizedName(normalizedName, folder.parentId)
        if (conflict != null && conflict.id != folderId) return false
        updateFolderName(folderId, name, normalizedName, System.currentTimeMillis())
        return true
    }

    @Transaction
    open suspend fun moveBooks(bookIds: Set<String>, targetFolderId: String?) {
        if (bookIds.isEmpty()) return
        if (targetFolderId == null) {
            deleteBookFolderLinks(bookIds)
            return
        }
        checkNotNull(getFolderById(targetFolderId)) { "Folder no longer exists" }
        upsertBookFolderLinks(bookIds.map { BookFolderCrossRefEntity(it, targetFolderId) })
    }

    @Transaction
    open suspend fun moveFolder(folderId: String, targetParentId: String?): FolderMoveResult {
        val folder = getFolderById(folderId) ?: return FolderMoveResult.SourceNotFound
        if (targetParentId == folderId) return FolderMoveResult.InvalidTarget
        if (folder.parentId == targetParentId) return FolderMoveResult.NoChange

        var cursor = targetParentId
        val visited = mutableSetOf<String>()
        while (cursor != null) {
            if (cursor == folderId || !visited.add(cursor)) return FolderMoveResult.InvalidTarget
            val target = getFolderById(cursor) ?: return FolderMoveResult.TargetNotFound
            cursor = target.parentId
        }

        val conflict = getFolderByNormalizedName(folder.normalizedName, targetParentId)
        if (conflict != null && conflict.id != folderId) return FolderMoveResult.DuplicateName

        updateFolderParent(folderId, targetParentId, System.currentTimeMillis())
        return FolderMoveResult.Success
    }

    @Transaction
    open suspend fun deleteFolderTree(folderId: String): List<String> {
        val coverPaths = getCoverPathsInTree(folderId)
        deleteFolderRow(folderId)
        return coverPaths
    }
}
