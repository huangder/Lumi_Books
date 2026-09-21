package com.huangder.lumibooks.util.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtTocCompatImportTest {

    private val thirdPartyPayload = """
        [
          {"id":-1,"enable":true,"name":"目录(去空白)","rule":"(?<=[　\\s])第\\s{0,4}\\d+\\s{0,4}章.{0,30}${'$'}","example":"第一章 x","serialNumber":0},
          {"id":-100,"enable":false,"name":"默认分章规则","rule":"","serialNumber":99},
          {"id":7,"name":"带脚本","rule":"^第.{1,10}章${'$'}","replacement":"result.replace(/x/,'')","enable":false,"serialNumber":3},
          {"id":8,"name":"坏正则","rule":"^第(章${'$'}","enable":true,"serialNumber":4}
        ]
    """.trimIndent()

    @Test
    fun importsThirdPartyArrayAndSkipsBlankOrInvalidRules() {
        val parsed = TxtTocRuleImport.parse(thirdPartyPayload)

        assertTrue(parsed.lumiRules.isEmpty())
        assertEquals(2, parsed.thirdPartyRules.size)
        assertEquals(2, parsed.skipped)
        assertEquals(1, parsed.scriptIgnored)

        val first = parsed.thirdPartyRules.first()
        assertEquals("legado--1", first.id)
        assertEquals(TxtTocDialect.LEGADO, first.dialect)
        assertEquals(0, first.order)
        assertEquals(0, first.serialNumber)
        assertEquals(-1L, first.thirdPartyId)
        assertTrue(first.enabled)
        assertNull(first.replacement)
        assertFalse(first.hasIgnoredScript)

        val scripted = parsed.thirdPartyRules[1]
        assertEquals("legado-7", scripted.id)
        assertFalse(scripted.enabled)
        assertTrue(scripted.hasIgnoredScript)
        assertEquals(3, scripted.serialNumber)
        assertEquals(1, scripted.order)
        assertEquals("result.replace(/x/,'')", scripted.replacement)
    }

    @Test
    fun importsSingleThirdPartyObject() {
        val parsed = TxtTocRuleImport.parse("""{"name":"单条","rule":"^第\\d+章${'$'}","serialNumber":5}""")

        assertEquals(1, parsed.thirdPartyRules.size)
        val rule = parsed.thirdPartyRules.single()
        assertEquals(5, rule.serialNumber)
        assertTrue(rule.id.startsWith(LegadoTxtTocRuleCodec.ID_PREFIX))
        assertEquals(TxtTocDialect.LEGADO, rule.dialect)
    }

    @Test
    fun rejectsBookSourcePayloads() {
        val arrayPayload =
            """[{"bookSourceUrl":"https://example.com","bookSourceName":"站点","ruleToc":{"chapterList":"tag"}}]"""
        val objectPayload = """{"bookSourceUrl":"https://example.com","name":"站点"}"""

        listOf(arrayPayload, objectPayload).forEach { payload ->
            val error = runCatching { TxtTocRuleImport.parse(payload) }.exceptionOrNull()
            assertTrue("expected a book-source rejection for $payload", error is TxtTocRuleImportException)
            assertEquals(
                TxtTocRuleImportException.Reason.BOOK_SOURCE_UNSUPPORTED,
                (error as TxtTocRuleImportException).reason
            )
        }
    }

    @Test
    fun rejectsEmptyUnknownAndUnsupportedPayloads() {
        assertEquals(
            TxtTocRuleImportException.Reason.EMPTY,
            (runCatching { TxtTocRuleImport.parse("   ") }
                .exceptionOrNull() as TxtTocRuleImportException).reason
        )
        assertEquals(
            TxtTocRuleImportException.Reason.NOT_JSON,
            (runCatching { TxtTocRuleImport.parse("hello world") }
                .exceptionOrNull() as TxtTocRuleImportException).reason
        )
        assertEquals(
            TxtTocRuleImportException.Reason.UNKNOWN_FORMAT,
            (runCatching { TxtTocRuleImport.parse("""{"foo":1}""") }
                .exceptionOrNull() as TxtTocRuleImportException).reason
        )
    }

    @Test
    fun lumiEnvelopeRoundTripsWithThirdPartySection() {
        val lumiRule = TxtTocRule(
            id = "custom-1",
            name = "C",
            chapterRegex = "^C(\\d+)${'$'}",
            chapterTitleTemplate = "C${'$'}1"
        )
        val thirdPartyRule = TxtTocRule(
            id = "legado--2",
            name = "T",
            chapterRegex = "^第.{1,8}章${'$'}",
            dialect = TxtTocDialect.LEGADO,
            serialNumber = 1,
            thirdPartyId = -2L,
            replacement = "result.trim()"
        )
        val payload = TxtTocRuleCodec.encode(listOf(lumiRule), listOf(thirdPartyRule))

        assertEquals(listOf(lumiRule), TxtTocRuleCodec.decode(payload))

        val parsed = TxtTocRuleImport.parse(payload)
        assertEquals(1, parsed.lumiRules.size)
        assertEquals(1, parsed.thirdPartyRules.size)
        val restored = parsed.thirdPartyRules.single()
        assertEquals("^第.{1,8}章${'$'}", restored.chapterRegex)
        assertEquals(-2L, restored.thirdPartyId)
        assertEquals(1, restored.serialNumber)
        assertEquals("result.trim()", restored.replacement)
        assertTrue(restored.hasIgnoredScript)
    }

    @Test
    fun compatibleFingerprintDiffersFromNativeRuleWithSameRegex() {
        val regex = "^第.{1,8}章${'$'}"
        val native = TxtTocRule(id = "custom-x", name = "n", chapterRegex = regex)
        val compatible = TxtTocRule(
            id = "legado--2",
            name = "n",
            chapterRegex = regex,
            dialect = TxtTocDialect.LEGADO
        )

        assertTrue(TxtTocRuleCodec.fingerprint(native) != TxtTocRuleCodec.fingerprint(compatible))
    }

    @Test
    fun mergesThirdPartyRulesByTheirOriginalId() {
        val existing = LegadoTxtTocRuleCodec.decode(
            """[{"id":1,"name":"旧","rule":"^第\\d+章${'$'}","serialNumber":2,"enable":true}]"""
        )
        val imported = LegadoTxtTocRuleCodec.decode(
            """[{"id":1,"name":"新","rule":"^第\\d+节${'$'}","serialNumber":1,"enable":true},
                {"id":2,"name":"新增","rule":"^第\\d+回${'$'}","serialNumber":0,"enable":true}]"""
        )

        val merged = LegadoTxtTocRuleCodec.merge(existing, imported)

        assertEquals(2, merged.size)
        assertEquals(listOf("legado-2", "legado-1"), merged.map { it.id })
        assertEquals(listOf("新增", "新"), merged.map { it.name })
        assertEquals(listOf(0, 1), merged.map { it.order })
    }
}
