package com.huangder.lumibooks.util.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CbzPageIndexTest {
    @Test
    fun `keeps only image entries`() {
        assertTrue(CbzPageIndex.isImageEntry("001.jpg"))
        assertTrue(CbzPageIndex.isImageEntry("Chapter 1/page-002.PNG"))
        assertTrue(CbzPageIndex.isImageEntry("a/b/003.webp"))

        assertFalse(CbzPageIndex.isImageEntry("Chapter 1/"))
        assertFalse(CbzPageIndex.isImageEntry("__MACOSX/._001.jpg"))
        assertFalse(CbzPageIndex.isImageEntry("Chapter 1/._001.jpg"))
        assertFalse(CbzPageIndex.isImageEntry(".DS_Store"))
        assertFalse(CbzPageIndex.isImageEntry("Thumbs.db"))
        assertFalse(CbzPageIndex.isImageEntry("ComicInfo.xml"))
        assertFalse(CbzPageIndex.isImageEntry("notes.txt"))
        assertFalse(CbzPageIndex.isImageEntry("noextension"))
    }

    @Test
    fun `sorts pages naturally inside a chapter`() {
        val index = CbzPageIndex.build(
            listOf("page_10.jpg", "page_2.jpg", "page_1.jpg", "page_20.jpg")
        )

        assertEquals(
            listOf("page_1.jpg", "page_2.jpg", "page_10.jpg", "page_20.jpg"),
            index.pages.map { it.entryName }
        )
        assertEquals(1, index.chapters.size)
        assertEquals(null, index.chapters.first().directory)
        assertEquals(4, index.chapters.first().pageCount)
    }

    @Test
    fun `subdirectories become chapters in natural order`() {
        val index = CbzPageIndex.build(
            listOf(
                "第10话/01.jpg",
                "第2话/01.jpg",
                "第2话/02.jpg",
                "第10话/02.jpg"
            )
        )

        assertEquals(2, index.chapters.size)
        assertEquals("第2话", index.chapters[0].directory)
        assertEquals(0, index.chapters[0].firstPageIndex)
        assertEquals(2, index.chapters[0].pageCount)
        assertEquals("第10话", index.chapters[1].directory)
        assertEquals(2, index.chapters[1].firstPageIndex)
        assertEquals(
            listOf("第2话/01.jpg", "第2话/02.jpg", "第10话/01.jpg", "第10话/02.jpg"),
            index.pages.map { it.entryName }
        )
        assertEquals(listOf(0, 1, 0, 1), index.pages.map { it.pageInChapter })
        assertEquals(listOf(0, 0, 1, 1), index.pages.map { it.chapterIndex })
    }

    @Test
    fun `junk entries are dropped before chapters are built`() {
        val index = CbzPageIndex.build(
            listOf(
                "Chapter 1/",
                "__MACOSX/Chapter 1/._001.jpg",
                "Chapter 1/001.jpg",
                "Chapter 1/ComicInfo.xml",
                "Chapter 1/002.jpeg"
            )
        )

        assertEquals(2, index.pages.size)
        assertEquals(1, index.chapters.size)
        assertEquals("Chapter 1", index.chapters.first().directory)
    }

    @Test
    fun `archive without images is empty`() {
        val index = CbzPageIndex.build(listOf("info/readme.txt", "ComicInfo.xml"))

        assertEquals(CbzIndex.EMPTY, index)
        assertEquals(0, index.pages.size)
        assertEquals(0, index.chapters.size)
    }

    @Test
    fun `duplicate entry names are collapsed`() {
        val index = CbzPageIndex.build(listOf("001.jpg", "001.jpg", "002.jpg"))

        assertEquals(listOf("001.jpg", "002.jpg"), index.pages.map { it.entryName })
    }

    @Test
    fun `windows separators are normalized`() {
        val index = CbzPageIndex.build(listOf("Chapter 1\\page 1.jpg"))

        assertEquals("Chapter 1/page 1.jpg", index.pages.single().entryName)
        assertEquals("Chapter 1", index.chapters.single().directory)
    }

    @Test
    fun `chapter label prefers the deepest directory segment`() {
        assertEquals("第03话", CbzPageIndex.chapterDirectoryLabel("合集/第03话"))
        assertEquals("Chapter 1", CbzPageIndex.chapterDirectoryLabel("Chapter 1"))
        assertEquals("001", CbzPageIndex.chapterDirectoryLabel("001"))
        assertEquals(null, CbzPageIndex.chapterDirectoryLabel(null))
        assertEquals(null, CbzPageIndex.chapterDirectoryLabel(""))
        // Undecodable ZIP names must not leak mojibake into the chapter list.
        assertEquals(null, CbzPageIndex.chapterDirectoryLabel("\uFFFD\uFFFD\uFFFD"))
        assertEquals(null, CbzPageIndex.chapterDirectoryLabel("---"))
    }

    @Test
    fun `natural order compares digit runs by value`() {
        assertTrue(NaturalOrder.compare("2", "10") < 0)
        assertTrue(NaturalOrder.compare("page9", "page10") < 0)
        assertTrue(NaturalOrder.compare("Chapter 02", "chapter 2") == 0)
        assertTrue(NaturalOrder.compare("a", "a1") < 0)
    }
}
