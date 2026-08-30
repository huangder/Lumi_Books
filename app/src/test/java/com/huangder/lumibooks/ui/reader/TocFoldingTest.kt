package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.util.parser.TocEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TocFoldingTest {
    @Test
    fun infersVolumesInAFlatChapterList() {
        val entries = listOf(
            chapter("序章", 0),
            chapter("第一卷 开端", 1),
            chapter("第一章", 2),
            chapter("第二章", 3),
            chapter("检察官室的提议 第二卷", 4),
            chapter("第三章", 5)
        )

        val groups = findTocFoldGroups(entries)

        assertEquals(mapOf(1 to 4, 4 to 6), groups)
    }

    @Test
    fun doesNotTreatSimilarChapterTitlesAsVolumes() {
        val entries = listOf(
            chapter("第一卷轴的秘密", 0),
            chapter("第二章", 1)
        )

        assertTrue(findTocFoldGroups(entries).isEmpty())
    }

    @Test
    fun explicitGroupsFollowHierarchyLevels() {
        val entries = listOf(
            group("上部", level = 1),
            group("第一卷", level = 2),
            chapter("第一章", 0, level = 3),
            chapter("第二章", 1, level = 3),
            group("第二卷", level = 2),
            chapter("第三章", 2, level = 3),
            group("下部", level = 1),
            chapter("第四章", 3, level = 2)
        )

        val groups = findTocFoldGroups(entries)

        assertEquals(6, groups[0])
        assertEquals(4, groups[1])
        assertEquals(6, groups[4])
        assertEquals(8, groups[6])
    }

    @Test
    fun collapsingAParentHidesAllNestedGroupsAndChapters() {
        val entries = listOf(
            group("上部", level = 1),
            group("第一卷", level = 2),
            chapter("第一章", 0, level = 3),
            group("第二卷", level = 2),
            chapter("第二章", 1, level = 3),
            group("下部", level = 1),
            chapter("第三章", 2, level = 2)
        )
        val groups = findTocFoldGroups(entries)

        val visible = visibleTocEntries(entries, groups, collapsedGroups = setOf(0))

        assertEquals(listOf("上部", "下部", "第三章"), visible.map { it.entry.title })
        assertFalse(visible.any { it.entry.title == "第二卷" })
    }

    @Test
    fun nestedCollapseRemainsIndependentWhenParentIsExpanded() {
        val entries = listOf(
            group("上部", level = 1),
            group("第一卷", level = 2),
            chapter("第一章", 0, level = 3),
            chapter("第二章", 1, level = 3),
            group("第二卷", level = 2),
            chapter("第三章", 2, level = 3)
        )
        val groups = findTocFoldGroups(entries)

        val visible = visibleTocEntries(entries, groups, collapsedGroups = setOf(1))

        assertEquals(listOf("上部", "第一卷", "第二卷", "第三章"), visible.map { it.entry.title })
    }

    @Test
    fun currentChapterFallsBackToItsCollapsedGroupHeader() {
        val entries = listOf(
            chapter("序章", 0),
            chapter("第一卷", 1),
            chapter("第一章", 2),
            chapter("第二章", 3),
            chapter("第二卷", 4),
            chapter("第三章", 5)
        )
        val groups = findTocFoldGroups(entries)
        val collapsed = setOf(1)
        val visible = visibleTocEntries(entries, groups, collapsed)

        val visibleIndex = currentTocVisibleIndex(entries, visible, groups, collapsed, currentChapter = 3)

        assertEquals("第一卷", visible[visibleIndex].entry.title)
    }

    @Test
    fun currentCardCountsAsVisibleWhenItIntersectsViewport() {
        val items = listOf(TocViewportItem(index = 4, offset = 96, size = 40))

        assertTrue(isTocItemVisible(4, viewportStartOffset = 100, viewportEndOffset = 300, items))
        assertFalse(isTocItemVisible(4, viewportStartOffset = 136, viewportEndOffset = 300, items))
        assertFalse(isTocItemVisible(-1, viewportStartOffset = 100, viewportEndOffset = 300, items))
    }

    @Test
    fun returnToCurrentExpandsEveryCollapsedAncestor() {
        val groups = mapOf(0 to 8, 1 to 5, 5 to 8)

        assertEquals(
            setOf(0, 1),
            collapsedTocAncestors(
                sourceIndex = 3,
                foldGroups = groups,
                collapsedGroups = setOf(0, 1, 5)
            )
        )
    }

    private fun chapter(title: String, index: Int, level: Int = 1) =
        TocEntry(title = title, level = level, chapterIndex = index)

    private fun group(title: String, level: Int) =
        TocEntry(title = title, level = level, isGroup = true)
}
