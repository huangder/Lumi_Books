package com.huangder.lumibooks.data.sync

import com.huangder.lumibooks.data.backup.PortableSnapshot
import com.huangder.lumibooks.data.local.entity.TagGroupEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class WebdavTagGroupSelectionTest {
    @Test
    fun `group scopes follow their own sync toggles including deletion records`() {
        val local = snapshot("local")
        val merged = snapshot("merged")
        for (library in listOf(false, true)) for (reading in listOf(false, true)) {
            val selected = chooseWebdavTagGroups(merged, local, library, reading)
            assertEquals(if (library) "merged-book" else "local-book", selected.single { it.type == "book" }.id)
            assertEquals(if (reading) "merged-annotation" else "local-annotation", selected.single { it.type == "annotation" }.id)
        }
    }

    private fun snapshot(prefix: String) = PortableSnapshot(createdAt = 0, sourceDeviceId = "device", preferences = emptyList(),
        books = emptyList(), folders = emptyList(), bookFolderLinks = emptyList(), tags = emptyList(),
        bookTagLinks = emptyList(), readingRecords = emptyList(), bookmarks = emptyList(), notes = emptyList(),
        tombstones = emptyList(), assets = emptyList(), tagGroups = listOf(
            TagGroupEntity("$prefix-book", "book", "Group", "group", 10, isDeleted = true),
            TagGroupEntity("$prefix-annotation", "annotation", "Group", "group", 10)))
}
