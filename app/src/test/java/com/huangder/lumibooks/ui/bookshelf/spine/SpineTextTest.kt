package com.huangder.lumibooks.ui.bookshelf.spine

import org.junit.Assert.assertEquals
import org.junit.Test

class SpineTextTest {
    @Test fun `subtitles and edition prefixes are dropped`() {
        assertEquals("红楼梦", SpineText.mainTitle("红楼梦（脂评汇校本）"))
        assertEquals("人类简史", SpineText.mainTitle("人类简史：从动物到上帝"))
        assertEquals("红楼梦", SpineText.mainTitle("（新版）红楼梦"))
        assertEquals("三体", SpineText.mainTitle("《三体》"))
        assertEquals("Sapiens", SpineText.mainTitle("Sapiens: A Brief History of Humankind"))
        assertEquals("Dune", SpineText.mainTitle("Dune - Deluxe Edition"))
        assertEquals("活着", SpineText.mainTitle("活着——余华作品"))
    }

    @Test fun `names with a middle dot and hyphenated words stay whole`() {
        assertEquals("哈利·波特与魔法石", SpineText.mainTitle("哈利·波特与魔法石"))
        assertEquals("Spider-Man", SpineText.mainTitle("Spider-Man"))
    }

    @Test fun `a title that is only a subtitle mark is kept`() {
        assertEquals("(1)", SpineText.mainTitle("(1)"))
    }

    @Test fun `short author keeps a surname or a few characters`() {
        assertEquals("曹雪芹", SpineText.shortAuthor("曹雪芹"))
        assertEquals("马尔克斯", SpineText.shortAuthor("[哥伦比亚] 加西亚·马尔克斯"))
        assertEquals("刘慈欣", SpineText.shortAuthor("刘慈欣 著"))
        assertEquals("Harari", SpineText.shortAuthor("Yuval Noah Harari"))
        assertEquals("Gaiman", SpineText.shortAuthor("Neil Gaiman, Terry Pratchett"))
        assertEquals("", SpineText.shortAuthor(""))
    }

    @Test fun `vertical units keep short digit runs together`() {
        assertEquals(listOf("第", "3", "卷"), SpineText.verticalUnits("第3卷"))
        assertEquals(listOf("第", "12", "回"), SpineText.verticalUnits("第12回"))
        assertEquals(listOf("A", "B", "C"), SpineText.verticalUnits("ABC"))
        assertEquals(listOf("红", "楼", "梦"), SpineText.verticalUnits("红 楼梦"))
    }

    @Test fun `vertical fit shrinks before it cuts`() {
        val (size, count) = SpineText.fitVertical(4, length = 100f, maxSize = 15f, minSize = 9f, lineHeight = 1.1f)
        assertEquals(15f, size, 0.001f)
        assertEquals(4, count)
        val (shrunk, all) = SpineText.fitVertical(8, length = 100f, maxSize = 15f, minSize = 9f, lineHeight = 1.1f)
        assertEquals(100f / (8 * 1.1f), shrunk, 0.001f)
        assertEquals(8, all)
        val (min, cut) = SpineText.fitVertical(30, length = 100f, maxSize = 15f, minSize = 9f, lineHeight = 1.1f)
        assertEquals(9f, min, 0f)
        assertEquals(10, cut)
    }
}
