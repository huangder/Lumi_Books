package com.huangder.lumibooks.data.backup

import android.app.Application
import androidx.room.Room
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.AnnotationTagEntity
import com.huangder.lumibooks.data.local.entity.TagEntity
import com.huangder.lumibooks.data.local.entity.TagGroupEntity
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import com.huangder.lumibooks.mineru.MineruTokenStore
import com.huangder.lumibooks.translation.TranslationTokenStore
import com.huangder.lumibooks.tts.ExternalTtsTokenStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PortableTagGroupPersistenceTest {
    @Test
    fun `capture restore and merge preserve classifications and durable group deletions`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val manager = PortableSnapshotManager(context, db, DataStoreManager(context), SyncIdentityStore(db.syncStateDao()),
            MineruTokenStore(context), ExternalTtsTokenStore(context), TranslationTokenStore(context))
        try {
            db.tagGroupDao().upsertGroups(listOf(TagGroupEntity("g", "book", "Shelf", "shelf", 10),
                TagGroupEntity("a", "annotation", "Review", "review", 10)))
            db.syncStateDao().upsertTags(listOf(TagEntity("tag", "Fiction", "fiction", 1, updatedAt = 10, groupId = "g")))
            db.annotationTagDao().upsertTags(listOf(AnnotationTagEntity("Quote", 10, groupId = "a")))
            val captured = PortableSnapshot.fromJson(manager.capture(includeBookFiles = false).snapshot.toJson())
            assertEquals(2, captured.tagGroups.size)

            val legacy = captured.copy(hasTagGroups = false, tagGroups = emptyList(),
                tags = captured.tags.map { it.copy(groupId = null, updatedAt = 20) },
                annotationTags = captured.annotationTags.map { it.copy(groupId = null, updatedAt = 20) })
            manager.apply(legacy, emptyMap(), replace = false)
            assertEquals("g", db.syncStateDao().getAllTags().single().groupId)
            assertEquals("a", db.annotationTagDao().getAllTags().single().groupId)

            val deleted = captured.copy(tagGroups = captured.tagGroups.map { it.copy(isDeleted = true, updatedAt = 30) })
            manager.apply(deleted, emptyMap(), replace = false)
            manager.apply(captured, emptyMap(), replace = false)
            assertTrue(db.tagGroupDao().getAllGroups().all { it.isDeleted })
            assertNull(db.syncStateDao().getAllTags().single().groupId)
            assertNull(db.annotationTagDao().getAllTags().single().groupId)

            manager.apply(captured, emptyMap(), replace = true, replaceAssetDirectories = false)
            assertTrue(db.tagGroupDao().getAllGroups().none { it.isDeleted })
            assertEquals("g", db.syncStateDao().getAllTags().single().groupId)
            assertEquals("a", db.annotationTagDao().getAllTags().single().groupId)
            manager.apply(captured.copy(tagGroups = emptyList()), emptyMap(), replace = true, replaceAssetDirectories = false)
            assertTrue(db.tagGroupDao().getAllGroups().isEmpty())
            assertNull(db.syncStateDao().getAllTags().single().groupId)
            assertNull(db.annotationTagDao().getAllTags().single().groupId)
        } finally {
            db.close()
        }
    }
}
