package com.huangder.lumibooks.replacement

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplacementRulesTest {
    @Test fun orderedRulesCaptureGroupsAndRemoveWholeLines() {
        val ad = ReplacementRule("ad", find = "(?m)^广告.*\\n", regex = true)
        val rename = ReplacementRule("rename", find = "(小)(明)", replacement = "$2$1", regex = true)
        val polish = ReplacementRule("polish", find = "明小", replacement = "阿明")
        val input = "开始\n广告读取更多\n小明回家"
        val result = ReplacementProgram(listOf(ad, rename, polish)).apply(input, previewRule = "rename")
        assertEquals("开始\n阿明回家", result.display)
        assertEquals(1, result.preview.count)
        assertTrue(result.preview.before.contains("小明"))
        assertTrue(result.preview.after.contains("明小"))
        assertEquals(input.indexOf("小明"), result.offsets.source(result.display.indexOf("阿明")))
        assertEquals(result.display.indexOf("回家"), result.offsets.display(input.indexOf("回家")))
    }

    @Test fun fullyDeletedRangesCollapseAndAdjacentSelectionsRetainOriginalBounds() {
        val result = ReplacementProgram(listOf(ReplacementRule("delete", find = "广告"))).apply("甲广告乙")
        assertEquals("甲乙", result.display)
        assertEquals(1, result.offsets.display(1))
        assertEquals(1, result.offsets.display(3, end = true))
        assertEquals(1, result.offsets.source(1, end = true))
        assertEquals(3, result.offsets.source(1))
    }

    @Test fun rejectsUnsupportedPatternsAndSkipsZeroWidthMatches() {
        assertEquals(ReplacementError.EMPTY_FIND, ReplacementProgram.validate(ReplacementRule("empty")))
        assertEquals(ReplacementError.INVALID_REGEX,
            ReplacementProgram.validate(ReplacementRule("look", find = "(?=x)", regex = true)))
        assertEquals(ReplacementError.INVALID_GROUP,
            ReplacementProgram.validate(ReplacementRule("bad", find = "(x)", replacement = "$2", regex = true)))
        val rule = ReplacementRule("zero", find = "x*", replacement = "y", regex = true)
        assertNull(ReplacementProgram.validate(rule))
        assertEquals("ayb", ReplacementProgram(listOf(rule)).apply("axb").display)
    }

    @Test fun htmlMatchesAcrossInlineMarkupAndKeepsOriginalStructure() {
        val document = Jsoup.parse(
            """<p id="chapter"><a href="#where">小<b>明</b></a>去<img src="cat.jpg">看猫。</p>""" +
                """<p id="where">小</p><p>明</p><p hidden>小明</p><script>小明</script>"""
        )
        val rule = ReplacementRule("rename", find = "小明", replacement = "<新名>")
        val crossingImage = ReplacementRule("image", find = "去看猫", replacement = "消失")
        val result = document.applyReplacementRules(ReplacementProgram(listOf(rule, crossingImage)), "rename")
        assertEquals(1, result.preview.count)
        assertEquals("<新名>", document.selectFirst("a")?.text())
        assertEquals("#where", document.selectFirst("a")?.attr("href"))
        assertEquals("cat.jpg", document.selectFirst("img")?.attr("src"))
        assertEquals("小", document.selectFirst("#where")?.text())
        assertEquals("小明", document.selectFirst("[hidden]")?.text())
        assertTrue(document.outerHtml().contains("&lt;新名&gt;"))
        assertFalse(result.display.contains("消失"))
    }

    @Test fun projectionsUseUtf16AndAnchorSelectionsInsideReplacement() {
        val result = ReplacementProgram(listOf(ReplacementRule("name", find = "旧", replacement = "新名字")))
            .apply("📚旧人")
        assertEquals("📚新名字人", result.display)
        assertEquals(2, result.offsets.source(3))
        assertEquals(3, result.offsets.source(4, end = true))
        assertEquals(5, result.offsets.display(3))
    }
}
