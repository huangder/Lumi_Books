package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtChapterTitleStyleTest {
    @Test
    fun stylesOnlyExplicitShortChapterHeadings() {
        assertTrue(shouldStyleTxtChapterTitle("第2章 新的开始", "第2章 新的开始"))
        assertFalse(shouldStyleTxtChapterTitle("他确乎有点象一棵树", "第2章 他确乎有点象一棵树"))
        assertFalse(shouldStyleTxtChapterTitle("正文".repeat(50), "正文".repeat(50)))
    }
}
