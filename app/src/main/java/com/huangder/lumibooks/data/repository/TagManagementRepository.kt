package com.huangder.lumibooks.data.repository

import androidx.room.withTransaction
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.TagEntity
import com.huangder.lumibooks.data.local.entity.TagGroupEntity
import com.huangder.lumibooks.domain.model.ManagedTag
import com.huangder.lumibooks.domain.model.AnnotationTags
import com.huangder.lumibooks.domain.model.TagCatalogMutation
import com.huangder.lumibooks.domain.model.TagNameValidator
import com.huangder.lumibooks.domain.model.TagOperationResult
import com.huangder.lumibooks.domain.model.TagTypes
import com.huangder.lumibooks.domain.repository.ReadingRepository
import com.huangder.lumibooks.domain.repository.TagRepository
import com.huangder.lumibooks.edition.PremiumAccess
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine

@Singleton
class TagManagementRepository @Inject constructor(
    private val db: AppDatabase,
    private val premium: PremiumAccess,
    private val reading: ReadingRepository,
    private val tags: TagRepository
) {
    private val _mutations = MutableSharedFlow<TagCatalogMutation>(extraBufferCapacity = 64)
    val mutations: SharedFlow<TagCatalogMutation> = _mutations.asSharedFlow()

    fun observeGroups(type: String): Flow<List<TagGroupEntity>> = db.tagGroupDao().observeGroups(type)

    fun observeCatalog(type: String): Flow<List<ManagedTag>> {
        require(TagTypes.isValid(type))
        if (type == TagTypes.BOOK) return db.tagDao().observeCatalog()
        return combine(
            db.annotationTagDao().observeTags(), reading.observeAnnotationTags(),
            reading.observeTextNotes(), reading.observeAllBookmarks()
        ) { metadata, names, notes, bookmarks ->
            val byName = metadata.associateBy { it.name }
            val counts = (notes.map { it.tags } + bookmarks.map { it.tags })
                .flatMap { it.distinct() }.groupingBy { it }.eachCount()
            names.map { name -> ManagedTag(name, name, byName[name]?.groupId, counts[name] ?: 0) }
        }
    }

    suspend fun createGroup(type: String, name: String): TagOperationResult = operation(type, premiumOnly = true) {
        if (!TagNameValidator.isValid(name)) return@operation failure("invalid_name")
        val normalized = TagNameValidator.normalized(name)
        val groups = db.tagGroupDao().getAllGroups()
        if (groups.any { it.type == type && it.normalizedName == normalized && !it.isDeleted }) return@operation failure("duplicate_name")
        val deleted = groups.find { it.type == type && it.normalizedName == normalized }
        db.tagGroupDao().upsertGroups(listOf(TagGroupEntity(
            deleted?.id ?: UUID.randomUUID().toString(), type, name.trim(), normalized, nextTime()
        )))
        success()
    }

    suspend fun renameGroup(type: String, id: String, name: String): TagOperationResult = operation(type, premiumOnly = true) {
        if (!TagNameValidator.isValid(name)) return@operation failure("invalid_name")
        val groups = db.tagGroupDao().getAllGroups()
        val group = groups.find { it.id == id && it.type == type && !it.isDeleted } ?: return@operation failure("not_found")
        val normalized = TagNameValidator.normalized(name)
        if (groups.any { it.id != id && it.type == type && it.normalizedName == normalized && !it.isDeleted }) return@operation failure("duplicate_name")
        db.tagGroupDao().upsertGroups(listOf(group.copy(name = name.trim(), normalizedName = normalized, updatedAt = nextTime())))
        success()
    }

    suspend fun deleteGroup(type: String, id: String): TagOperationResult = operation(type, premiumOnly = true) {
        val group = db.tagGroupDao().getAllGroups().find { it.id == id && it.type == type && !it.isDeleted }
            ?: return@operation failure("not_found")
        val now = nextTime()
        if (type == TagTypes.BOOK) {
            val keys = db.tagDao().getAllTagsSnapshot().filter { it.groupId == id }.map { it.id }
            if (keys.isNotEmpty()) db.tagDao().moveTags(keys, null, now)
        } else {
            db.annotationTagDao().clearGroup(id, now)
        }
        db.tagGroupDao().upsertGroups(listOf(group.copy(isDeleted = true, updatedAt = now)))
        success()
    }

    suspend fun createTag(type: String, name: String, groupId: String? = null): TagOperationResult = operation(type) {
        if (!validTagName(type, name)) return@operation failure("invalid_name")
        if (!validGroup(type, groupId)) return@operation failure("invalid_group")
        val normalized = TagNameValidator.normalized(name)
        if (catalogSnapshot(type).any { TagNameValidator.normalized(it.name) == normalized }) return@operation failure("duplicate_name")
        val now = nextTime()
        if (type == TagTypes.BOOK) {
            db.tagDao().insertTag(TagEntity(UUID.randomUUID().toString(), name.trim(), normalized, now, groupId = groupId))
        } else {
            reading.createAnnotationTag(name.trim())
            db.annotationTagDao().moveTags(listOf(name.trim()), groupId, nextTime())
        }
        success()
    }

    suspend fun renameTag(type: String, key: String, name: String): TagOperationResult {
        var newKey = key
        val result = operation(type) {
            if (!validTagName(type, name)) return@operation failure("invalid_name")
            val catalog = catalogSnapshot(type)
            if (catalog.none { it.key == key }) return@operation failure("not_found")
            if (catalog.any { it.key != key && TagNameValidator.normalized(it.name) == TagNameValidator.normalized(name) }) return@operation failure("duplicate_name")
            if (type == TagTypes.BOOK) {
                if (!tags.renameTag(key, name)) return@operation failure("duplicate_name")
            } else {
                newKey = name.trim()
                reading.renameAnnotationTag(key, newKey)
            }
            success()
        }
        if (result.success) _mutations.emit(TagCatalogMutation(type, key, newKey))
        return result
    }

    suspend fun deleteTag(type: String, key: String): TagOperationResult = delete(type, listOf(key), premiumOnly = false)

    suspend fun deleteTags(type: String, keys: List<String>): TagOperationResult = delete(type, keys, premiumOnly = true)

    private suspend fun delete(type: String, keys: List<String>, premiumOnly: Boolean): TagOperationResult {
        val selected = keys.distinct()
        val result = operation(type, premiumOnly) {
            if (!catalogSnapshot(type).map { it.key }.containsAll(selected)) return@operation failure("not_found")
            selected.forEach { if (type == TagTypes.BOOK) tags.deleteTag(it) else reading.deleteAnnotationTag(it) }
            success()
        }
        if (result.success) selected.forEach { _mutations.emit(TagCatalogMutation(type, it, null)) }
        return result
    }

    suspend fun moveTags(type: String, keys: List<String>, groupId: String?): TagOperationResult = operation(type, premiumOnly = true) {
        if (!validGroup(type, groupId)) return@operation failure("invalid_group")
        if (!catalogSnapshot(type).map { it.key }.containsAll(keys)) return@operation failure("not_found")
        if (keys.isNotEmpty()) {
            if (type == TagTypes.BOOK) db.tagDao().moveTags(keys.distinct(), groupId, nextTime())
            else db.annotationTagDao().moveTags(keys.distinct(), groupId, nextTime())
        }
        success()
    }

    suspend fun replaceBookTags(bookId: String, keys: Set<String>): TagOperationResult = operation(TagTypes.BOOK) {
        if (db.bookDao().getBookById(bookId) == null || !db.tagDao().getAllTagsSnapshot().map { it.id }.containsAll(keys)) return@operation failure("not_found")
        val current = db.tagDao().getAllBookTagLinksSnapshot().filter { it.bookId == bookId }.map { it.tagId }.toSet()
        (current - keys).forEach { tags.removeTagFromBook(bookId, it) }
        (keys - current).forEach { tags.assignTag(bookId, it) }
        success()
    }

    suspend fun addTagsToBooks(bookIds: Set<String>, keys: Set<String>): TagOperationResult = operation(TagTypes.BOOK) {
        if (!db.tagDao().getAllTagsSnapshot().map { it.id }.containsAll(keys) || bookIds.any { db.bookDao().getBookById(it) == null }) return@operation failure("not_found")
        bookIds.forEach { bookId -> keys.forEach { tags.assignTag(bookId, it) } }
        success()
    }

    private suspend fun catalogSnapshot(type: String): List<ManagedTag> = if (type == TagTypes.BOOK) {
        db.tagDao().getAllTagsSnapshot().map { ManagedTag(it.id, it.name, it.groupId, 0) }
    } else {
        val metadata = db.annotationTagDao().getAllTags().associateBy { it.name }
        val referenced = (db.noteDao().getAllNotes().map { it.tagsJson } + db.bookmarkDao().getAllBookmarks().map { it.tagsJson })
            .flatMap(AnnotationTags::decode)
        (metadata.values.filterNot { it.isDeleted }.map { it.name } + referenced).distinct()
            .filterNot { metadata[it]?.isDeleted == true }
            .map { ManagedTag(it, it, metadata[it]?.groupId, 0) }
    }

    private suspend fun validGroup(type: String, groupId: String?): Boolean = groupId == null ||
        db.tagGroupDao().getAllGroups().any { it.id == groupId && it.type == type && !it.isDeleted }

    private fun validTagName(type: String, name: String): Boolean = name.isNotBlank() &&
        (type == TagTypes.ANNOTATION || TagNameValidator.isValid(name))

    private suspend fun nextTime(): Long = maxOf(
        System.currentTimeMillis(),
        (db.tagGroupDao().getAllGroups().maxOfOrNull { it.updatedAt } ?: 0L) + 1,
        (db.tagDao().getAllTagsSnapshot().maxOfOrNull { it.updatedAt } ?: 0L) + 1,
        (db.annotationTagDao().getAllTags().maxOfOrNull { it.updatedAt } ?: 0L) + 1
    )

    private suspend fun operation(type: String, premiumOnly: Boolean = false, block: suspend () -> TagOperationResult): TagOperationResult {
        if (!TagTypes.isValid(type)) return failure("invalid_type")
        if (premiumOnly && !premium.isUnlocked()) return failure("premium_required")
        return try {
            db.withTransaction {
                if (premiumOnly && !premium.isUnlocked()) throw PremiumRequired()
                val result = block()
                if (premiumOnly && !premium.isUnlocked()) throw PremiumRequired()
                result
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: PremiumRequired) {
            failure("premium_required")
        } catch (_: Exception) {
            failure("save_failed")
        }
    }

    private fun success() = TagOperationResult(true)
    private fun failure(code: String) = TagOperationResult(false, code)
    private class PremiumRequired : RuntimeException()
}
