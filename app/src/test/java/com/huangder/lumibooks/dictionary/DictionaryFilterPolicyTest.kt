package com.huangder.lumibooks.dictionary

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DictionaryFilterPolicyTest {
    private fun policy(exceptions: String = "[]") = DictionaryFilterPolicy.fromJson(JSONObject("""
        {"formatVersion":1,"version":2,"charMap":{"臺":"台","灣":"湾"},"queryBlockRules":[
          {"text":"台湾","mode":"exact"},
          {"text":"Taiwan","mode":"word"}
        ],"rules":[
          {"id":"a","category":"abuse","text":"badword","mode":"word","fields":["headword","definition","example","related"],"exceptions":$exceptions},
          {"id":"b","category":"regional","text":"台湾争议短语","mode":"phrase","fields":["headword","definition","example","related"]}
        ]}
    """))
    private fun entry() = DictionaryEntry("entry", "hello", senses = listOf(
        DictionarySense("1", "a greeting", examples = listOf(DictionaryExample("hello", "你好")), related = listOf("greeting")),
        DictionarySense("2", "a normal definition")))

    @Test fun filtersOnlyAffectedSenseAndRecomputesSummary() {
        val original = entry().copy(senses = entry().senses + DictionarySense("3", "contains BADWORD here"))
        val result = policy().apply("book", original)
        assertTrue(result.filtered)
        assertEquals(2, result.entry!!.senses.size)
        assertFalse(result.entry!!.summary.contains("BADWORD"))
        assertEquals(3, original.senses.size)
    }
    @Test fun translationRemovesEntireExamplePairAndRelatedItem() {
        val sense = DictionarySense("1", "medical definition", examples = listOf(DictionaryExample("benign", "BADWORD"),
            DictionaryExample("healthy example")), related = listOf("badword", "normal"))
        val result = policy().apply("book", entry().copy(senses = listOf(sense)))
        assertEquals(listOf(DictionaryExample("healthy example")), result.entry!!.senses.single().examples)
        assertEquals(listOf("normal"), result.entry!!.senses.single().related)
        assertTrue(result.entry!!.partiallyFiltered)
    }
    @Test fun headwordAliasHidesWholeEntryAndAllSensesHiddenAreDistinguished() {
        assertNull(policy().apply("book", entry().copy(aliases = listOf("ＢＡＤＷＯＲＤ"))).entry)
        val result = policy().apply("book", entry().copy(senses = listOf(DictionarySense("1", "臺灣争议短语"))))
        assertNull(result.entry); assertTrue(result.filtered)
    }
    @Test fun englishWordBoundariesDoNotMatchSubstrings() {
        assertFalse(policy().matches("badwords are different", "definition", "book", "entry"))
        assertTrue(policy().matches("a (BADWORD).", "example", "book", "entry"))
        assertFalse(policy().apply("book", entry().copy(headword = "Taiwan", senses = listOf(DictionarySense("1", "medical anatomy and history")))).filtered)
    }
    @Test fun blockedQueriesAreRejectedWithoutFilteringDictionaryEntries() {
        assertTrue(policy().isQueryBlocked("台湾"))
        assertTrue(policy().isQueryBlocked("臺灣"))
        assertTrue(policy().isQueryBlocked("Taiwan"))
        assertTrue(policy().isQueryBlocked("ＴＡＩＷＡＮ"))
        assertTrue(policy().isQueryBlocked("TAIWAN"))
        assertTrue(policy().isQueryBlocked("tAiWaN"))
        assertFalse(policy().isQueryBlocked("Taiwanese"))
        assertFalse(policy().apply("book", entry().copy(headword = "Taiwan")).filtered)
    }
    @Test fun exceptionsAreRestrictedToDictionaryEntryAndSense() {
        val p = policy("""[{"dictionaryId":"book","entryId":"entry","senseId":"1"}]""")
        assertFalse(p.matches("badword", "definition", "book", "entry", "1"))
        assertTrue(p.matches("badword", "definition", "book", "entry", "2"))
        assertTrue(p.matches("badword", "definition", "other", "entry", "1"))
        assertTrue(p.matches("badword", "headword", "book", "entry"))
    }
    @Test fun buildTimeFilteredMarkerIsNotLostByNewPolicy() {
        assertTrue(policy().apply("book", entry().copy(partiallyFiltered = true)).filtered)
    }
    @Test fun downloadHostsCannotBeChangedByCatalog() {
        assertTrue(isDictionaryDownloadUrl("https://github.com/huangder/Lumi_Books/releases/download/v1/book.zip"))
        assertFalse(isDictionaryDownloadUrl("https://github.com/another/repo/releases/download/v1/book.zip"))
        assertFalse(isDictionaryDownloadUrl("http://github.com/huangder/Lumi_Books/releases/download/v1/book.zip"))
        assertFalse(isDictionaryDownloadUrl("https://github.com.attacker.test/huangder/Lumi_Books/releases/download/v1/book.zip"))
    }
}
