package com.huangder.lumibooks.data.local.dao

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

    @Query("UPDATE folders SET name = :name, normalizedName = :normalizedName WHERE id = :folderId")
    abstract suspend fun updateFolderName(folderId: String, name: String, normalizedName: String)

    @Query("UPDATE folders SET coverPath = :coverPath WHERE id = :folderId")
    abstract suspend fun updateFolderCover(folderId: String, coverPath: String?): Int

    @Query(
        "UPDATE folders SET previewBookIds = :previewBookIds " +
            "WHERE id = :folderId AND previewBookIds IS NULL " +
            "AND length(trim(:previewBookIds)) > 2"
    )
    abstract suspend fun initializeFolderPreviewIfUnset(
        folderId: String,
        previewBookIds: String
    ): Int

    @Query("UPDATE folders SET parentId = :parentId WHERE id = :folderId")
    abstract suspend fun updateFolderParent(folderId: String, parentId: String?)

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

    @Transaction
    open suspend fun renameFolderIfAvailable(
        folderId: String,
        name: String,
        normalizedName: String
    ): Boolean {
        val folder = getFolderById(folderId) ?: return false
        val conflict = getFolderByNormalizedName(normalizedName, folder.parentId)
        if (conflict != null && conflict.id != folderId) return false
        updateFolderName(folderId, name, normalizedName)
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

        updateFolderParent(folderId, targetParentId)
        return FolderMoveResult.Success
    }

    @Transaction
    open suspend fun deleteFolderTree(folderId: String): List<String> {
        val coverPaths = getCoverPathsInTree(folderId)
        deleteFolderRow(folderId)
        return coverPaths
    }
}
