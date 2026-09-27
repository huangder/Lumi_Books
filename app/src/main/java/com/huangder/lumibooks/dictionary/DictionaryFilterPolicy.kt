package com.huangder.lumibooks.dictionary

import org.json.JSONObject

data class DictionaryFilterException(val dictionaryId: String, val entryId: String, val senseId: String? = null)
data class DictionaryFilterRule(val id: String, val category: String, val text: String, val mode: String,
    val fields: Set<String>, val exceptions: List<DictionaryFilterException> = emptyList())
data class FilteredDictionaryEntry(val entry: DictionaryEntry?, val filtered: Boolean)

/** Literal-only, deterministic policy shared with tools/dictionaries/build.py. No user regular expressions. */
class DictionaryFilterPolicy(val version: Int, private val charMap: Map<Char, Char>,
    val rules: List<DictionaryFilterRule>) {
    fun normalize(text: String): String = normalizeDictionaryKey(text).map { charMap[it] ?: it }.joinToString("")
    private val normalizedRules = rules.map { it to normalize(it.text) }

    fun matches(text: String, field: String, dictionaryId: String, entryId: String, senseId: String? = null): Boolean {
        val value = normalize(text)
        return normalizedRules.any { (rule, needle) ->
            field in rule.fields && !rule.exceptions.any { exception ->
                exception.dictionaryId == dictionaryId && exception.entryId == entryId &&
                    (exception.senseId == null || exception.senseId == senseId)
            } && when (rule.mode) {
                "exact" -> value == needle
                "phrase" -> needle in value
                "word" -> {
                    var offset = value.indexOf(needle)
                    var found = false
                    while (offset >= 0 && !found) {
                        val end = offset + needle.length
                        found = (offset == 0 || !value[offset - 1].isLetterOrDigit()) &&
                            (end == value.length || !value[end].isLetterOrDigit())
                        if (!found) offset = value.indexOf(needle, offset + 1)
                    }
                    found
                }
                else -> false
            }
        }
    }

    fun apply(dictionaryId: String, entry: DictionaryEntry): FilteredDictionaryEntry {
        if ((entry.aliases + entry.headword).any { matches(it, "headword", dictionaryId, entry.id) }) {
            return FilteredDictionaryEntry(null, true)
        }
        var filtered = entry.partiallyFiltered
        val senses = entry.senses.mapNotNull { sense ->
            if (matches(sense.definition, "definition", dictionaryId, entry.id, sense.id) ||
                matches(sense.partOfSpeech, "definition", dictionaryId, entry.id, sense.id)) {
                filtered = true
                null
            } else {
                val examples = sense.examples.filterNot {
                    val hit = matches(it.text, "example", dictionaryId, entry.id, sense.id) ||
                        matches(it.translation, "example", dictionaryId, entry.id, sense.id)
                    if (hit) filtered = true
                    hit
                }
                val related = sense.related.filterNot {
                    matches(it, "related", dictionaryId, entry.id, sense.id).also { hit -> if (hit) filtered = true }
                }
                sense.copy(examples = examples, related = related)
            }
        }
        if (senses.isEmpty()) return FilteredDictionaryEntry(null, true)
        val pronunciation = if (matches(entry.pronunciation, "related", dictionaryId, entry.id)) {
            filtered = true; ""
        } else entry.pronunciation
        val kind = if (matches(entry.kind, "related", dictionaryId, entry.id)) {
            filtered = true; ""
        } else entry.kind
        return FilteredDictionaryEntry(entry.copy(senses = senses, pronunciation = pronunciation,
            kind = kind, partiallyFiltered = filtered), filtered)
    }

    companion object {
        fun fromJson(json: JSONObject): DictionaryFilterPolicy {
            val version = json.getInt("version")
            require(version > 0 && json.getInt("formatVersion") == 1)
            val mapping = json.getJSONObject("charMap")
            val charMap = mapping.keys().asSequence().associate { key ->
                val value = mapping.getString(key)
                require(key.length == 1 && value.length == 1)
                key.single() to value.single()
            }
            val rules = json.getJSONArray("rules").objects().map { r ->
                val rule = DictionaryFilterRule(r.getString("id"), r.getString("category"), r.getString("text"),
                    r.getString("mode"), r.strings("fields").toSet(),
                    r.optJSONArray("exceptions")?.objects().orEmpty().map {
                        DictionaryFilterException(it.getString("dictionaryId"), it.getString("entryId"),
                            it.optString("senseId").takeIf(String::isNotBlank))
                    })
                require(rule.text.isNotBlank() && rule.text.length <= 160)
                require(rule.mode in setOf("exact", "phrase", "word"))
                require(rule.fields.isNotEmpty() && rule.fields.all { it in setOf("headword", "definition", "example", "related") })
                require(rule.exceptions.all { it.dictionaryId.isNotBlank() && it.entryId.isNotBlank() })
                rule
            }
            require(rules.size <= 10_000 && rules.map { it.id }.distinct().size == rules.size)
            return DictionaryFilterPolicy(version, charMap, rules)
        }
    }
}
