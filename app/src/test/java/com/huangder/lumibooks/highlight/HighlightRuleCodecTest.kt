package com.huangder.lumibooks.highlight

import com.huangder.lumibooks.domain.model.HighlightRule
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class HighlightRuleCodecTest {
    @Test
    fun compatibleJsonPreservesUnknownFieldsAndAddsLumiMetadata() {
        val source = """
            [{
              "id":"dialogue",
              "name":"对白-波浪线",
              "pattern":"[“][^”]+[”]",
              "enabled":true,
              "position":2,
              "targetScope":0,
              "textColor":-14575885,
              "underlineMode":3,
              "fontWeight":700,
              "isItalic":true,
              "bgImageFit":"cover",
              "npLeft":12
            }]
        """.trimIndent().toByteArray()

        val rule = HighlightRuleCodec.decode(source).single()
        assertEquals("对白-波浪线", rule.name)
        assertEquals(HighlightRule.UNDERLINE_WAVE, rule.underlineMode)

        val exported = JSONArray(HighlightRuleCodec.encodeJson(listOf(rule)).toString(Charsets.UTF_8))
            .getJSONObject(0)
        assertEquals("cover", exported.getString("bgImageFit"))
        assertEquals(12, exported.getInt("npLeft"))
        assertEquals("LUMI", exported.getJSONObject("_lumi").getString("exportedBy"))
        assertEquals(1, exported.getJSONObject("_lumi").getInt("schemaVersion"))
    }

    @Test
    fun zipUsesCompatibleEntryAndProminentLumiManifest() {
        val bytes = HighlightRuleCodec.encodeZip(listOf(rule("one", "a+")), exportedAt = 123L)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }

        assertEquals(setOf("highlightRule.json", "LUMI-MANIFEST.json"), entries.keys)
        val manifest = JSONObject(entries.getValue("LUMI-MANIFEST.json"))
        assertEquals("LUMI", manifest.getString("generatedBy"))
        assertTrue(manifest.getString("notice").contains("Not affiliated"))
        assertEquals("one", HighlightRuleCodec.decode(bytes).single().id)
    }

    @Test
    fun unsupportedRe2SyntaxIsPreservedButDisabled() {
        val source = """[{"id":"java-only","name":"Java","pattern":"(?&lt;=a)b","enabled":true}]"""
            .replace("&lt;", "<")
            .toByteArray()

        val decoded = HighlightRuleCodec.decode(source).single()

        assertFalse(decoded.enabled)
        assertNotNull(HighlightRuleMatcher.validationError(decoded))
        assertTrue(decoded.rawJson.contains("(?<=a)b"))
    }

    @Test
    fun zipRejectsNestedPathsAndOversizedEntries() {
        val nested = zip("nested/highlightRule.json" to "[]".toByteArray())
        assertFails { HighlightRuleCodec.decode(nested) }

        val oversized = zip(
            "highlightRule.json" to ByteArray(HighlightRuleCodec.MAX_IMPORT_BYTES + 1) { ' '.code.toByte() }
        )
        assertFails { HighlightRuleCodec.decode(oversized) }
    }

    @Test
    fun decodesDoubleEscapedUnicodeRegexFromCompatibleRulePack() {
        val source = """[{"id":"dialogue","pattern":"(?:\\\\u201c[^\\\\u201d\\\\n]{1,1200}\\\\u201d)","enabled":true}]"""
        val rule = HighlightRuleCodec.decode(source.toByteArray()).single()

        assertTrue(rule.enabled)
        assertTrue(rule.pattern.isNotBlank())
        assertTrue(HighlightRuleMatcher.validationError(rule) == null)
        assertTrue(HighlightRuleMatcher.match("“斗之力，三段！”", 0, listOf(rule)).isNotEmpty())
    }

    @Test
    fun matcherUsesRulePositionAndStableMatchKeys() {
        val later = rule("later", "ab").copy(position = 2)
        val earlier = rule("earlier", "bc").copy(position = 1)

        val matches = HighlightRuleMatcher.match("abc", 4, listOf(later, earlier))

        assertEquals(listOf("earlier", "later"), matches.map { it.ruleId })
        assertEquals(
            HighlightRuleMatcher.matchKey(4, 1, 3, "bc"),
            matches.first().matchKey
        )
    }

    @Test
    fun importPlannerSkipsIdenticalAndKeepsConflictsByDefault() {
        val current = listOf(rule("same", "a").copy(position = 4, rawJson = "{\"custom\":1}"))
        val incoming = listOf(
            current.single().copy(position = 0),
            rule("same", "b").copy(rawJson = "{\"custom\":2}"),
            rule("new", "c")
        )

        val plan = HighlightRuleImportPlanner.plan(
            current = current,
            incoming = incoming,
            replaceConflicts = false,
            importedName = { "$it imported" },
            newId = { "renamed" }
        )

        assertEquals(1, plan.skipped)
        assertEquals(1, plan.conflicts)
        assertEquals(listOf("renamed", "new"), plan.rulesToSave.map { it.id })
        assertEquals("same imported", plan.rulesToSave.first().name)

        val replacement = HighlightRuleImportPlanner.plan(
            current = current,
            incoming = listOf(rule("same", "b")),
            replaceConflicts = true,
            importedName = { it }
        ).rulesToSave.single()
        assertEquals("same", replacement.id)
        assertEquals(4, replacement.position)
    }

    @Test
    fun importPlannerReplacesStaleInvalidRuleWithValidSameId() {
        val stale = rule("dialogue", "a{1,1200}").copy(enabled = false)
        val fixed = rule("dialogue", "a{1,1000}")

        val plan = HighlightRuleImportPlanner.plan(
            current = listOf(stale),
            incoming = listOf(fixed),
            replaceConflicts = false,
            importedName = { it }
        )

        assertEquals(listOf("dialogue"), plan.rulesToSave.map { it.id })
        assertEquals("a{1,1000}", plan.rulesToSave.single().pattern)
    }

    private fun rule(id: String, pattern: String) = HighlightRule(
        id = id,
        name = id,
        pattern = pattern,
        rawJson = "{}"
    )

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, data) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(data)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

    private fun assertFails(block: () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue("Expected IllegalArgumentException", failed)
    }
}
