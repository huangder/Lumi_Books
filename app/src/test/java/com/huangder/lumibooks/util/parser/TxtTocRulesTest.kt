package com.huangder.lumibooks.util.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtTocRulesTest {
    @Test
    fun rendersCaptureGroupTemplatesAndPrefersVolume() {
        val rule = TxtTocRule(
            id = "test",
            name = "test",
            chapterRegex = "^##\\s*(\\d+)\\s*(.*)$",
            volumeRegex = "^@@\\s*(\\d+)\\s*(.*)$",
            chapterTitleTemplate = "Chapter \$1: \$2",
            volumeTitleTemplate = "Volume \$1: \$2"
        )
        val compiled = TxtTocRuleCompiler.compile(rule).getOrThrow()

        assertEquals("Chapter 12: Arrival", compiled.match("## 12 Arrival")?.title)
        assertEquals(TxtTocHeadingRole.VOLUME, compiled.match("@@ 2 Arc")?.role)
        assertEquals("Volume 2: Arc", compiled.match("@@ 2 Arc")?.title)
    }

    @Test
    fun rejectsLookaroundAndMissingCaptureGroups() {
        assertFalse(TxtTocRuleCompiler.compile(TxtTocRule(id = "a", name = "a", chapterRegex = "^(?=x).*$")).isSuccess)
        assertFalse(
            TxtTocRuleCompiler.compile(
                TxtTocRule(id = "a", name = "a", chapterRegex = "^(x)$", chapterTitleTemplate = "\$2")
            ).isSuccess
        )
    }

    @Test
    fun jsonRoundTripPreservesCustomRules() {
        val source = listOf(TxtTocRule(id = "custom-1", name = "Custom", chapterRegex = "^CHAPTER (\\d+)$", chapterTitleTemplate = "C\$1"))
        val decoded = TxtTocRuleCodec.decode(TxtTocRuleCodec.encode(source))
        assertEquals(source, decoded)
    }

    @Test
    fun selectorRejectsDenseBodyLikeRules() {
        val (selected, diagnostics) = TxtTocRuleSelector.choose(
            listOf(TxtTocRule(id = "dense", name = "Dense", chapterRegex = ".*")),
            sequenceOf("body", "body", "body")
        )
        assertEquals(null, selected)
        assertTrue(diagnostics.single().reason != null)
    }

    @Test
    fun symbolPrefixedRuleMatchesStarAndSeparator() {
        val rule = TxtTocRuleBuiltIns.byId("builtin-symbol-prefixed")!!
        val compiled = TxtTocRuleCompiler.compile(rule).getOrThrow()

        assertEquals("☆、第一章 误入江湖", compiled.match("☆、第一章 误入江湖")?.title)
        assertEquals("★、新的开始", compiled.match("★、新的开始")?.title)
        assertEquals("◆：章节标题", compiled.match("◆：章节标题")?.title)
        assertEquals(null, compiled.match("正文中的项目符号"))
    }

    @Test
    fun selectorChoosesSymbolPrefixedRule() {
        val (selected, diagnostics) = TxtTocRuleSelector.choose(
            TxtTocRuleBuiltIns.all,
            sequenceOf("前言", "☆、第一章 误入江湖", "正文", "★、第二章 夜探山门", "◆：终章")
        )

        assertEquals("builtin-symbol-prefixed", selected?.id)
        assertTrue(diagnostics.first { it.ruleId == "builtin-symbol-prefixed" }.accepted)
    }
}
