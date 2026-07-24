package com.huangder.lumibooks.pdfconversion

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfTextReflowFormatterTest {
    @Test
    fun joinsSoftWrappedChineseLinesWithoutSpaces() {
        val result = PdfTextReflowFormatter.reflow("第一行中文\n继续内容\n\n第二段")

        assertEquals("第一行中文继续内容第二段", result)
    }

    @Test
    fun joinsEnglishLinesAndRemovesSoftHyphenBreak() {
        val result = PdfTextReflowFormatter.reflow("This is inter-\nnational text.\nNext sentence.")

        assertEquals("This is international text. Next sentence.", result)
    }

    @Test
    fun keepsChapterHeadingAsItsOwnParagraph() {
        val result = PdfTextReflowFormatter.reflow("第十二章 开始\n这是正文\n下一行")

        assertEquals("第十二章 开始\n\n这是正文下一行", result)
    }

    @Test
    fun removesPdfSpacingBetweenChineseCharacters() {
        val result = PdfTextReflowFormatter.reflow("这 是 中 文\n下 一 行")

        assertEquals("这是中文下一行", result)
    }

    @Test
    fun mergesLongNumberedPdfLineWithItsContinuation() {
        val result = PdfTextReflowFormatter.reflow(
            "2.战略从小米诞生的第一天起就存在隐患：做低端容易，但是从低端转高端难，做\n\n" +
                "高端难，但是高端下放到底端容易。\n\n下一段开始"
        )

        assertEquals(
            "2.战略从小米诞生的第一天起就存在隐患：做低端容易，但是从低端转高端难，做高端难，但是高端下放到底端容易。\n\n下一段开始",
            result
        )
    }

    @Test
    fun keepsShortNumberedHeadingSeparate() {
        val result = PdfTextReflowFormatter.reflow("2.战略原则\n\n正文第一行\n\n正文结束。")

        assertEquals("2.战略原则\n\n正文第一行正文结束。", result)
    }

    @Test
    fun joinsPageContinuationWithoutCreatingAParagraph() {
        assertEquals("", PdfTextReflowFormatter.separatorBetween("上一页未结束", "下一页继续"))
        assertEquals("\n\n", PdfTextReflowFormatter.separatorBetween("上一段结束。", "下一段开始"))
        assertEquals(" ", PdfTextReflowFormatter.separatorBetween("English", "continues"))
    }

    @Test
    fun blankInputProducesBlankOutput() {
        assertEquals("", PdfTextReflowFormatter.reflow(" \n\n "))
    }
}
