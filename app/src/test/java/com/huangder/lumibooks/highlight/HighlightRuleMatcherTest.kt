package com.huangder.lumibooks.highlight

import com.huangder.lumibooks.domain.model.HighlightRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HighlightRuleMatcherTest {
    @Test
    fun nestedDoubleQuotesKeepTheWholeDialogue() {
        val dialogue = "“我相信“他”翻译的历史正文！既然“他”说了那座岛在这里，我们就去那里！”"
        for (pattern in listOf("“[^”]+”", "[“][^”]+[”]", "(?:“[^”\\n]{1,1000}”)", "“(.*?)”")) {
            assertEquals(listOf(dialogue), matches("他说：$dialogue 旁白", pattern))
        }
    }

    @Test
    fun greedyAndLazyRulesBothLeaveNarrationBetweenDialoguesUncolored() {
        val text = "“你好。”他点点头，走到门口。“我们出发吧。”"
        for (pattern in listOf("“.*”", "“.+”", "“.+?”", "“[^”]+”", "“[^“”]+”", "“[\\s\\S]+”")) {
            assertEquals(listOf("“你好。”", "“我们出发吧。”"), matches(text, pattern))
        }
    }

    @Test
    fun mixedNestedAndSeparateQuotesDoNotSwallowNarration() {
        val text = "前言“外层“内层“更深”内层”外层”中间旁白“下一段”结尾”"
        assertEquals(listOf("“外层“内层“更深”内层”外层”", "“下一段”"), matches(text, "“.*”"))
    }

    @Test
    fun alternativeQuoteStylesUseTheOutermostMatch() {
        val text = "“外层‘内层’外层”旁白「另一段『内层』」旁白\"英文\""
        assertEquals(listOf("“外层‘内层’外层”", "「另一段『内层』」", "\"英文\""),
            matches(text, "(?:“[^”]+”|‘[^’]+’|「[^」]+」|『[^』]+』|\"[^\"]+\")"))
    }

    @Test
    fun unpairedQuotesDoNotColorNarration() {
        assertEquals(emptyList<String>(), matches("只有旁白，最后一个右引号”", "“.*”"))
        assertEquals(emptyList<String>(), matches("“没有结束的对话", "“.*”"))
        assertEquals(listOf("“完整对话”"), matches("“未闭合\n旁白“完整对话”尾声”", "“[^”\\n]+”"))
    }

    @Test
    fun newlinePolicyAndEmptyDialogueStillFollowTheRule() {
        assertEquals(emptyList<String>(), matches("“第一行\n第二行”", "“[^”\\n]+”"))
        assertEquals(emptyList<String>(), matches("“第一行\n第二行”", "“.+”"))
        assertEquals(listOf("“第一行\n第二行”"), matches("“第一行\n第二行”", "“[^”]+”"))
        assertEquals(listOf("“第一行\r第二行”"), matches("“第一行\r第二行”", "“.+”"))
        assertEquals(emptyList<String>(), matches("“第一行\r第二行”", "“[^”\\r\\n]+”"))
        assertEquals(emptyList<String>(), matches("“”", "“.+”"))
        assertEquals(listOf("“”"), matches("“”", "“.*”"))
    }

    @Test
    fun boundsApplyToTheWholeDialogueWithoutPartialInnerMatches() {
        assertEquals(emptyList<String>(), matches("“外层“内层”继续”", "“[^”]{1,5}”"))
        assertEquals(listOf("“短句”"), matches("“外层“内层”继续”旁白“短句”", "“[^”]{1,5}”"))
    }

    @Test
    fun normalizedImportedRulesRetainTheirLengthLimitAndSupportNesting() {
        val json = """[{"id":"dialogue","pattern":"(?:“[^”\\n]{1,1200}”)"}]"""
        val rule = HighlightRuleCodec.decode(json.toByteArray()).single()
        assertNull(HighlightRuleMatcher.validationError(rule))
        val dialogue = "“" + "文".repeat(1000) + "“他”继续”"
        assertEquals(listOf(dialogue), HighlightRuleMatcher.match(dialogue, 0, listOf(rule)).map { it.text })
        assertEquals(emptyList<String>(), HighlightRuleMatcher.match("“" + "文".repeat(1201) + "”", 0, listOf(rule)).map { it.text })
    }

    @Test
    fun contentSpecificAndOtherRegexesKeepTheirOriginalSemantics() {
        val text = "“他说“你好”又走了”旁白“别的话”"
        assertEquals(listOf("“他说“你好”"), matches(text, "“他说[^”]+”"))
        assertEquals(listOf("旁白"), matches(text, "旁白"))
        assertEquals(listOf("“他说“你好”", "“别的话”"), matches(text, "“[^”()]+”"))
        assertEquals(listOf("“你好”", "旁白", "“别的话”"), matches(text, "“[^“”]+”|旁白"))
    }

    @Test
    fun offsetsKeysAndStylesReferToTheOriginalText() {
        val text = "😀旁白“外“内”外”尾声"
        val rule = rule("“[^”]+”").copy(textColor = 123, fontType = "serif", position = 2)
        val match = HighlightRuleMatcher.match(text, 4, listOf(rule)).single()
        assertEquals(4, match.start)
        assertEquals(text.indexOf('尾'), match.end)
        assertEquals(text.substring(match.start, match.end), match.text)
        assertEquals(HighlightRuleMatcher.matchKey(4, match.start, match.end, match.text), match.matchKey)
        assertEquals(123, match.style.textColor)
        assertEquals("serif", match.style.fontType)
        assertEquals(2, match.position)
    }

    @Test
    fun disabledInvalidAndExcessMatchesRemainExcluded() {
        val text = "“句子”".repeat(HighlightRuleMatcher.MAX_MATCHES_PER_RULE_AND_CHAPTER + 1)
        assertEquals(HighlightRuleMatcher.MAX_MATCHES_PER_RULE_AND_CHAPTER,
            HighlightRuleMatcher.match(text, 0, listOf(rule("“.*”"))).size)
        assertEquals(emptyList<String>(), HighlightRuleMatcher.match(text, 0,
            listOf(rule("“.*”").copy(enabled = false), rule("["))).map { it.text })
    }

    private fun matches(text: String, pattern: String) =
        HighlightRuleMatcher.match(text, 0, listOf(rule(pattern))).map { it.text }

    private fun rule(pattern: String) = HighlightRule(id = "dialogue", name = "对白", pattern = pattern)
}
