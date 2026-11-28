package com.huangder.lumibooks.data.repository

import android.app.Application
import androidx.room.Room
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.AnnotationTagEntity
import com.huangder.lumibooks.data.local.entity.TagGroupEntity
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import com.huangder.lumibooks.domain.model.TagTypes
import com.huangder.lumibooks.edition.PremiumAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TagBatchPersistenceTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: TagManagementRepository
    private val access = object : PremiumAccess {
        override val unlocked = MutableStateFlow(true)
        override fun isUnlocked() = unlocked.value
        override fun canAddRemoteSource(count: Int) = true
        override fun start() = Unit
    }

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        val sync = SyncIdentityStore(db.syncStateDao())
        val reading = ReadingRepositoryImpl(db.readingRecordDao(), db.bookmarkDao(), db.noteDao(), db.syncStateDao(), sync, db)
        repository = TagManagementRepository(db, access, reading, TagRepositoryImpl(db.tagDao(), db.syncStateDao(), sync))
    }

    @After fun tearDown() = db.close()

    @Test fun bookBatchKeepsExistingTagInItsGroupAndUsesFirstSpellingForNewTags() = runBlocking {
        addGroup(TagTypes.BOOK, "old")
        addGroup(TagTypes.BOOK, "target")
        assertTrue(repository.createTag(TagTypes.BOOK, "Existing", "old").success)

        assertTrue(repository.createTags(TagTypes.BOOK, listOf(" existing ", " New ", "NEW", "Other"), "target").success)
        assertEquals(
            mapOf("Existing" to "old", "New" to "target", "Other" to "target"),
            db.tagDao().getAllTagsSnapshot().associate { it.name to it.groupId }
        )
        assertEquals("duplicate_name", repository.createTags(TagTypes.BOOK, listOf("new", "Other"), "target").message)
    }

    @Test fun invalidBookNameRejectsWholeBatch() = runBlocking {
        assertEquals("invalid_name", repository.createTags(TagTypes.BOOK, listOf("Valid", "a".repeat(21))).message)
        assertTrue(db.tagDao().getAllTagsSnapshot().isEmpty())
    }

    @Test fun ignoredSecondInsertRollsBackFirstInsert() = runBlocking {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER ignore_tag BEFORE INSERT ON tags WHEN NEW.name = 'Blocked' BEGIN SELECT RAISE(IGNORE); END"
        )
        assertEquals("save_failed", repository.createTags(TagTypes.BOOK, listOf("First", "Blocked")).message)
        assertTrue(db.tagDao().getAllTagsSnapshot().isEmpty())
    }

    @Test fun annotationBatchCanReviveTagsAndUsesAnnotationValidation() = runBlocking {
        addGroup(TagTypes.ANNOTATION, "target")
        db.annotationTagDao().upsertTags(listOf(AnnotationTagEntity("Revived", 100L, isDeleted = true)))
        val longName = "a".repeat(21)
        assertTrue(repository.createTags(TagTypes.ANNOTATION, listOf("Revived", longName), "target").success)
        val tags = db.annotationTagDao().getAllTags()
        assertEquals(setOf("Revived", longName), tags.map { it.name }.toSet())
        assertTrue(tags.all { it.groupId == "target" && !it.isDeleted && it.updatedAt > 100L })
        assertTrue(db.tagDao().getAllTagsSnapshot().isEmpty())
    }

    @Test fun lockedAccessRejectsBatch() = runBlocking {
        access.unlocked.value = false
        assertEquals("premium_required", repository.createTags(TagTypes.BOOK, listOf("One", "Two")).message)
        assertTrue(db.tagDao().getAllTagsSnapshot().isEmpty())
    }

    private suspend fun addGroup(type: String, id: String) {
        db.tagGroupDao().upsertGroups(listOf(TagGroupEntity(id, type, id, id, 1L)))
    }
}
