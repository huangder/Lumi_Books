package com.huangder.lumibooks.replacement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplacementPreviewTest {
    private fun preview(rule: ReplacementRule, text: String) =
        ReplacementProgram(listOf(rule)).apply(text, previewRule = rule.id).preview

    private fun ReplacementPreview.slices(text: String, ranges: List<IntRange>) =
        ranges.map { text.substring(it.first, it.last + 1) }

    @Test fun renameMarksWholeNamesAndAppliesEveryMatchInWindow() {
        val result = preview(ReplacementRule("r", find = "小明", replacement = "阿明"), "王老师看着小明，说：“小明，回家吧。”")
        assertEquals(2, result.count)
        assertEquals(listOf("小明", "小明"), result.slices(result.before, result.removed))
        assertEquals(listOf("阿明", "阿明"), result.slices(result.after, result.added))
        assertEquals("王老师看着阿明，说：“阿明，回家吧。”", result.after)
    }

    @Test fun deletionKeepsContextAndAddsEmptyRange() {
        val rule = ReplacementRule("ads", find = "(?m)^本章未完.*\n", regex = true)
        val result = preview(rule, "他推开了门。\n本章未完，点击下一页\n门外站着")
        assertEquals(listOf("本章未完，点击下一页\n"), result.slices(result.before, result.removed))
        assertEquals("他推开了门。\n门外站着", result.after)
        assertTrue(result.added.single().isEmpty())
    }

    @Test fun longMatchIsTruncatedWithinPreview() {
        val long = "广".repeat(400)
        val result = preview(ReplacementRule("l", find = long, replacement = "x"), "前${long}后")
        assertTrue(result.before.length <= 1 + 160 + 1)
        assertEquals("前x", result.after)
    }
}
