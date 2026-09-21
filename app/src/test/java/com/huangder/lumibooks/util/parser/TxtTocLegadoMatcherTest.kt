package com.huangder.lumibooks.util.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtTocLegadoMatcherTest {

    private fun compile(regex: String): CompiledTxtTocRule = TxtTocRuleCompiler.compile(
        TxtTocRule(
            id = "legado-test",
            name = "third party",
            chapterRegex = regex,
            dialect = TxtTocDialect.LEGADO
        )
    ).getOrThrow()

    @Test
    fun leadingLookbehindSeesTheVirtualLineStart() {
        val rule = compile("(?<=[　\\s])(?:第\\s{0,4}[\\d〇一二两三四五六七八九十]+\\s{0,4}章).{0,30}${'$'}")

        assertEquals("第一章 假装第一章前面有空白但我不要", rule.match("第一章 假装第一章前面有空白但我不要")?.title)
        assertEquals("第二章 全角空格缩进", rule.match("　　第二章 全角空格缩进")?.title)
        assertNull(rule.match("　　正文段落，不含标题特征"))
    }

    @Test
    fun indentedTitlesWithOptionalWhitespaceLookbehindMatch() {
        val rule = compile("(?<=[ 　\\t]{0,4})(?:[☆★].{1,30}|第[\\d]+章.{0,20})${'$'}")

        assertEquals("第3章 缩进标题", rule.match("　　第3章 缩进标题")?.title)
        assertEquals("☆ 符号标题", rule.match("☆ 符号标题")?.title)
    }

    @Test
    fun endAnchorRejectsLinesWithTrailingBodyText() {
        val rule = compile("^第[0-9]{1,4}章.{0,3}${'$'}")

        assertEquals("第12章 归途", rule.match("第12章 归途")?.title)
        assertNull(rule.match("第12章 这是一个很长的标题"))
    }

    @Test
    fun neighborsProvideCrossLineContext() {
        val regex = "(?m)(?<=[ \\t　]{0,4})第[\\d〇一二三四五六七八九十]{1,8}章.{0,30}" +
            "${'$'}(?=[\\s　]{0,8}第[\\d〇一二三四五六七八九十]{1,8}章)"
        val rule = compile(regex)

        assertEquals(
            "第1章 开始",
            rule.match("第1章 开始", previousLine = "", nextLine = "第2章 继续")?.title
        )
        assertNull(rule.match("第2章 继续", previousLine = "第1章 开始", nextLine = "正文段落"))
    }

    @Test
    fun matchedRegionBecomesTheTitle() {
        val rule = compile("(?<=\\={3,6}).{1,40}?(?=\\=)")

        assertEquals("标题", rule.match("===标题===")?.title)
        assertNull(rule.match("没有装饰的正文"))
    }

    @Test
    fun longLinesAreSkipped() {
        val rule = compile("^第.{1,10}章${'$'}")
        val longLine = "第" + "a".repeat(4000) + "章"

        assertNull(rule.match(longLine))
    }

    @Test
    fun nestedQuantifiersAndBackreferencesStayRejected() {
        assertTrue(
            TxtTocRuleCompiler.compile(
                TxtTocRule(
                    id = "legado-a",
                    name = "a",
                    chapterRegex = "(a+)+b",
                    dialect = TxtTocDialect.LEGADO
                )
            ).isFailure
        )
        assertTrue(
            TxtTocRuleCompiler.compile(
                TxtTocRule(
                    id = "legado-b",
                    name = "b",
                    chapterRegex = "(a)\\1",
                    dialect = TxtTocDialect.LEGADO
                )
            ).isFailure
        )
    }

    @Test
    fun nativeDialectStillRejectsLookAround() {
        assertTrue(
            TxtTocRuleCompiler.compile(
                TxtTocRule(id = "custom", name = "native", chapterRegex = "^(?=x).*${'$'}")
            ).isFailure
        )
    }

    @Test
    fun nativeAndCompatibleRulesAgreeOnIndentedTitles() {
        val native = TxtTocRuleCompiler.compile(
            TxtTocRule(id = "custom", name = "native", chapterRegex = "^第[0-9]{1,4}章.*${'$'}")
        ).getOrThrow()
        val compatible = compile("(?<=[　\\s])第[0-9]{1,4}章.{0,30}${'$'}")

        assertEquals("第7章 出发", native.match("　　第7章 出发")?.title)
        assertEquals("第7章 出发", compatible.match("　　第7章 出发")?.title)
    }
}
