package com.huangder.lumibooks.util.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TxtTocCompatIntegrationTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val bodyParagraph = "正文".repeat(700)

    private fun compatibleRule(
        id: String = "legado--2",
        regex: String = "^◆◆\\s*.{1,20}${'$'}",
        serialNumber: Int = 1
    ) = TxtTocRule(
        id = id,
        name = "third party",
        chapterRegex = regex,
        dialect = TxtTocDialect.LEGADO,
        serialNumber = serialNumber
    )

    @Test
    fun shippedPresetImportsEveryRuleAndSkipsTheBlankOne() {
        val payload = presetAsset().readText(Charsets.UTF_8)
        val parsed = TxtTocRuleImport.parse(payload)

        assertEquals(25, parsed.thirdPartyRules.size)
        assertEquals(1, parsed.skipped)
        assertEquals(0, parsed.scriptIgnored)
        assertTrue(parsed.thirdPartyRules.all { it.dialect == TxtTocDialect.LEGADO })
        assertTrue(parsed.thirdPartyRules.all { it.chapterRegex.isNotBlank() })
        assertEquals(
            "(?<=[　\\s])(?:序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|第\\s{0,4}[\\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+?\\s{0,4}(?:章|节(?!课)|卷|集(?![合和]))).{0,30}${'$'}",
            parsed.thirdPartyRules.first { it.thirdPartyId == -1L }.chapterRegex
        )
    }

    @Test
    fun automaticDetectionFallsBackToCompatibleRules() {
        val file = temporaryFolder.newFile("fallback.txt")
        file.writeText(
            buildString {
                append("◆◆ 开篇").append('\n')
                append(bodyParagraph).append('\n')
                append("◆◆ 中段").append('\n')
                append(bodyParagraph).append('\n')
                append("◆◆ 收尾").append('\n')
                append(bodyParagraph)
            },
            Charsets.UTF_8
        )
        val parser = TxtParser().apply { thirdPartyTocRules = listOf(compatibleRule()) }

        val book = parser.parse(file.absolutePath)

        assertEquals(listOf("◆◆ 开篇", "◆◆ 中段", "◆◆ 收尾"), book.chapters.map { it.title })
        val applied = parser.lastTocDiagnostics.first { it.accepted }
        assertEquals("legado--2", applied.ruleId)
        assertEquals(3, applied.chapterMatches)
        assertEquals(TxtTocDialect.LEGADO, applied.dialect)
    }

    @Test
    fun explicitlySelectedCompatibleRuleSplitsIndentedTitles() {
        val file = temporaryFolder.newFile("explicit.txt")
        file.writeText(
            "　　第一章 起点\n正文一\n\n　　第二章 全角空格缩进\n正文二\n\n　　第三章 结束\n正文三",
            Charsets.UTF_8
        )
        val rule = compatibleRule(
            id = "legado--1",
            regex = "(?<=[　\\s])(?:第\\s{0,4}[\\d〇一二两三四五六七八九十]+\\s{0,4}章).{0,30}${'$'}"
        )
        val parser = TxtParser().apply { selectedTocRule = rule }

        val book = parser.parse(file.absolutePath)

        assertEquals(listOf("第一章 起点", "第二章 全角空格缩进", "第三章 结束"), book.chapters.map { it.title })
        assertEquals(TxtTocDialect.LEGADO, parser.lastTocDiagnostics.single().dialect)
    }

    @Test
    fun automaticIndexTokenChangesWithTheCompatibleRuleSalt() {
        val withoutCompatRules = TxtParser()
        val withCompatRules = TxtParser().apply { autoRuleSalt = "abcd1234" }
        val baseToken = TxtTocRuleCodec.fingerprint(null)

        assertEquals(baseToken, withoutCompatRules.semanticIndexKey("book.txt").tocRule)
        assertEquals("$baseToken-abcd1234", withCompatRules.semanticIndexKey("book.txt").tocRule)
    }

    @Test
    fun selectorPrefersTheFirstRuleThatPassesTheThresholds() {
        val lines = listOf(
            "◆◆ 开篇", bodyParagraph,
            "◆◆ 中段", bodyParagraph,
            "◆◆ 收尾", bodyParagraph
        )
        val first = compatibleRule(id = "legado--30", serialNumber = 1)
        val later = compatibleRule(id = "legado--31", serialNumber = 5)

        val selection = LegadoTocRuleSelector.choose(listOf(later, first), lines)

        assertEquals("legado--30", selection.rule?.id)
        assertTrue(selection.diagnostics.first { it.ruleId == "legado--30" }.accepted)
        assertFalse(selection.diagnostics.first { it.ruleId == "legado--31" }.accepted)
    }

    @Test
    fun selectorRejectsRulesThatMatchNearlyEveryLine() {
        val lines = List(40) { "正文一行" }
        val dense = compatibleRule(id = "legado--40", regex = "^正文一行${'$'}")

        val selection = LegadoTocRuleSelector.choose(listOf(dense), lines)

        assertNull(selection.rule)
        assertFalse(selection.diagnostics.single().accepted)
    }

    private fun presetAsset(): File {
        val candidates = listOf(
            File("src/main/assets/${LegadoTxtTocRuleCodec.PRESET_ASSET}"),
            File("app/src/main/assets/${LegadoTxtTocRuleCodec.PRESET_ASSET}")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("compatible preset asset is missing: ${candidates.map { it.path }}")
    }
}
