package com.huangder.lumibooks.util.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class BookFilenameMetadataTest {
    @Test
    fun extractsSupportedTitleAndAuthorPatterns() {
        val cases = mapOf(
            "《三体》作者：刘慈欣.epub" to BookNameMetadata("三体", "刘慈欣"),
            "《三体》刘慈欣.TXT" to BookNameMetadata("三体", "刘慈欣"),
            "三体 - 刘慈欣.txt" to BookNameMetadata("三体", "刘慈欣"),
            "《三体》 — 刘慈欣.txt" to BookNameMetadata("三体", "刘慈欣"),
            "The Hobbit by J. R. R. Tolkien.txt" to BookNameMetadata("The Hobbit", "J. R. R. Tolkien"),
            "Mr. Penumbra's 24-Hour Bookstore 作者: Robin Sloan.PDF" to
                BookNameMetadata("Mr. Penumbra's 24-Hour Bookstore", "Robin Sloan"),
            "《三体》.txt" to BookNameMetadata("三体")
        )
        cases.forEach { (filename, expected) ->
            assertEquals(filename, expected, BookFilenameMetadata.read(filename))
        }
    }

    @Test
    fun keepsFilenameWhenTheAuthorSplitIsUnclear() {
        val stems = listOf("A-B", "三体_刘慈欣", "三体（典藏版）", "三体 - 黑暗森林 - 第2卷",
            "《三体》全集", "三体 - 第三卷", "《三体》【精校】", "三体 - Unknown Author",
            "作者：刘慈欣", "三体 作者：")
        stems.forEach { stem ->
            assertEquals(stem, BookNameMetadata(stem), BookFilenameMetadata.read("$stem.epub"))
        }
    }

    @Test
    fun prioritizesEachFieldIndependently() {
        val filename = BookFilenameMetadata.read("《三体》刘慈欣.epub")
        assertEquals(
            BookNameMetadata("书内书名", "目录作者"),
            BookNameMetadata("  书内书名 ", "저자 불명")
                .withFallback(BookNameMetadata("目录书名", " 目录作者 ")).withFallback(filename)
        )
        assertEquals(
            BookNameMetadata("三体", "书内作者"),
            BookNameMetadata(" ", "书内作者").withFallback(filename)
        )
    }
}
