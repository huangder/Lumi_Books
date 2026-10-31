package com.huangder.lumibooks.data.backup

import com.huangder.lumibooks.data.local.entity.AnnotationTagEntity
import com.huangder.lumibooks.data.local.entity.BookTagCrossRefEntity
import com.huangder.lumibooks.data.local.entity.TagEntity
import com.huangder.lumibooks.data.local.entity.TagGroupEntity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PortableTagGroupsTest {
    @Test
    fun `backup preserves independent group catalogs and assignments`() {
        val source = snapshot().copy(
            tagGroups = listOf(group("book-group", "book"), group("annotation-group", "annotation")),
            tags = listOf(tag("tag", "book-group")),
            annotationTags = listOf(AnnotationTagEntity("Quote", 10, groupId = "annotation-group"))
        )
        assertEquals(source, PortableSnapshot.fromJson(source.toJson()))
    }

    @Test
    fun `older device updates names without clearing local classification`() {
        val local = snapshot().copy(tagGroups = listOf(group("g", "book"), group("a", "annotation")),
            tags = listOf(tag("tag", "g")),
            annotationTags = listOf(AnnotationTagEntity("Quote", 10, groupId = "a")))
        val legacyJson = JSONObject(local.toJson()).apply {
            remove("tagGroups")
            getJSONArray("tags").getJSONObject(0).apply { remove("groupId"); put("updatedAt", 20) }
            getJSONArray("annotationTags").getJSONObject(0).apply { remove("groupId"); put("updatedAt", 20) }
        }
        val legacy = PortableSnapshot.fromJson(legacyJson.toString())
        assertFalse(legacy.hasTagGroups)
        assertFalse(PortableSnapshot.fromJson(legacy.toJson()).hasTagGroups)
        val merged = PortableSnapshotMerger.merge(local, legacy)
        assertEquals("g", merged.tags.single().groupId)
        assertEquals("a", merged.annotationTags.single().groupId)
        assertEquals(merged, PortableSnapshotMerger.merge(legacy, local))
    }

    @Test
    fun `explicit unclassification wins over old assignment`() {
        val local = snapshot().copy(tagGroups = listOf(group("g", "book")), tags = listOf(tag("tag", "g")))
        val remote = local.copy(tags = listOf(tag("tag", null).copy(updatedAt = 20)))
        assertNull(PortableSnapshotMerger.merge(local, remote).tags.single().groupId)
    }

    @Test
    fun `missing assignment remains unknown after selective state upgrades`() {
        val grouped = snapshot().copy(tagGroups = listOf(group("g", "book")), tags = listOf(tag("tag", "g")))
        val partial = grouped.copy(tags = listOf(tag("tag", null).copy(updatedAt = 20)),
            bookTagsWithoutGroupId = setOf("tag"))
        val restored = PortableSnapshot.fromJson(partial.toJson())
        assertEquals(setOf("tag"), restored.bookTagsWithoutGroupId)
        assertEquals("g", PortableSnapshotMerger.merge(grouped, restored).tags.single().groupId)
    }

    @Test
    fun `group deletion wins ties and stale peers cannot restore classification`() {
        val stale = snapshot().copy(tagGroups = listOf(group("g", "book"), group("a", "annotation")),
            tags = listOf(tag("tag", "g")),
            annotationTags = listOf(AnnotationTagEntity("Quote", 10, groupId = "a")))
        val deleted = stale.copy(tagGroups = stale.tagGroups.map { it.copy(isDeleted = true) })
        val merged = PortableSnapshotMerger.merge(stale, deleted)
        assertTrue(merged.tagGroups.all { it.isDeleted })
        assertNull(merged.tags.single().groupId)
        assertNull(merged.annotationTags.single().groupId)
        assertEquals(merged, PortableSnapshotMerger.merge(merged, stale))
    }

    @Test
    fun `missing and wrong type groups leave tags unclassified`() {
        val source = snapshot().copy(tagGroups = listOf(group("g", "book")),
            tags = listOf(tag("tag", "missing")),
            annotationTags = listOf(AnnotationTagEntity("Quote", 10, groupId = "g")))
        val merged = PortableSnapshotMerger.merge(source, snapshot())
        assertNull(merged.tags.single().groupId)
        assertNull(merged.annotationTags.single().groupId)
    }

    @Test
    fun `legacy hierarchy materializes every ancestor without cycles or duplicate links`() {
        val source = snapshot().copy(tags = listOf(tag("parent", null),
            tag("child", null).copy(parentId = "parent"), tag("leaf", null).copy(parentId = "child")),
            bookTagLinks = listOf(BookTagCrossRefEntity("book", "leaf", 20), BookTagCrossRefEntity("book", "parent", 10)))
        val flat = PortableSnapshot.fromJson(source.toJson())
        assertTrue(flat.tags.all { it.parentId == null })
        assertEquals(setOf("leaf", "child", "parent"), flat.bookTagLinks.map { it.tagId }.toSet())
        assertTrue(flat.bookTagLinks.all { it.updatedAt == 20L })
        assertEquals(flat, flat.flattenLegacyTagHierarchy())
        val cycle = source.copy(tags = source.tags.map { if (it.id == "parent") it.copy(parentId = "leaf") else it })
        assertEquals(3, cycle.flattenLegacyTagHierarchy().bookTagLinks.size)
    }

    private fun group(id: String, type: String) = TagGroupEntity(id, type, id, id, 10)
    private fun tag(id: String, groupId: String?) = TagEntity(id, id, id, 1, updatedAt = 10, groupId = groupId)
    private fun snapshot() = PortableSnapshot(createdAt = 100, sourceDeviceId = "device", preferences = emptyList(),
        books = emptyList(), folders = emptyList(), bookFolderLinks = emptyList(), tags = emptyList(),
        bookTagLinks = emptyList(), readingRecords = emptyList(), bookmarks = emptyList(), notes = emptyList(),
        tombstones = emptyList(), assets = emptyList())
}
