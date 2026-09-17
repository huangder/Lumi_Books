package com.huangder.lumibooks.util.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CbzComicInfoTest {
    private val sidecar = """
        <?xml version="1.0" encoding="utf-8"?>
        <ComicInfo>
          <Title>第一卷</Title>
          <Series>示例漫画</Series>
          <Number>1</Number>
          <Writer>作者甲, 作者乙</Writer>
          <Penciller>作画丙</Penciller>
          <Manga>YesAndRightToLeft</Manga>
          <PageCount>24</PageCount>
        </ComicInfo>
    """.trimIndent()

    @Test
    fun `parses the fields the reader understands`() {
        val info = CbzComicInfo.parse(sidecar)!!

        assertEquals("第一卷", info.title)
        assertEquals("示例漫画", info.series)
        assertEquals(24, info.pageCount)
        assertTrue(info.prefersRightToLeft)
        assertEquals("第一卷", info.resolveTitle("fallback"))
        assertEquals("作者甲, 作者乙", info.resolveAuthor("未知作者"))
    }

    @Test
    fun `title falls back through series and file name`() {
        val seriesOnly = CbzComicInfo.parse("<ComicInfo><Series>示例漫画</Series></ComicInfo>")!!
        assertEquals("示例漫画", seriesOnly.resolveTitle("fallback"))

        val numbered = CbzComicInfo.parse(
            "<ComicInfo><Series>示例漫画</Series><Number>2</Number></ComicInfo>"
        )!!
        assertEquals("示例漫画 #2", numbered.resolveTitle("fallback"))

        val empty = CbzComicInfo.parse("<ComicInfo><LanguageISO>zh</LanguageISO></ComicInfo>")
        assertEquals("fallback", empty?.resolveTitle("fallback"))
    }

    @Test
    fun `author falls back to penciller then author then unknown`() {
        val penciller = CbzComicInfo.parse("<ComicInfo><Penciller>作画丙</Penciller></ComicInfo>")!!
        assertEquals("作画丙", penciller.resolveAuthor("未知作者"))

        val author = CbzComicInfo.parse("<ComicInfo><Author>作者丁</Author></ComicInfo>")!!
        assertEquals("作者丁", author.resolveAuthor("未知作者"))

        val blank = CbzComicInfo.parse("<ComicInfo><Series>示例漫画</Series></ComicInfo>")!!
        assertEquals("未知作者", blank.resolveAuthor("未知作者"))
    }

    @Test
    fun `left to right manga flag is not treated as right to left`() {
        val info = CbzComicInfo.parse("<ComicInfo><Manga>NoAndLeftToRight</Manga></ComicInfo>")!!

        assertFalse(info.prefersRightToLeft)
    }

    @Test
    fun `malformed or foreign xml is ignored`() {
        assertNull(CbzComicInfo.parse(null))
        assertNull(CbzComicInfo.parse("   "))
        assertNull(CbzComicInfo.parse("<ComicInfo><Title>unclosed"))
        assertNull(CbzComicInfo.parse("<NotComicInfo><Title>x</Title></NotComicInfo>"))
        assertNull(CbzComicInfo.parse("<ComicInfo></ComicInfo>"))
    }
}
