package com.huangder.lumibooks.highlight

import com.huangder.lumibooks.domain.model.HighlightRule
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object HighlightRuleCodec {
    const val JSON_ENTRY = "highlightRule.json"
    const val MANIFEST_ENTRY = "LUMI-MANIFEST.json"
    const val MAX_IMPORT_BYTES = 5 * 1024 * 1024
    const val MAX_RULES = 2_000

    fun decode(bytes: ByteArray): List<HighlightRule> {
        require(bytes.size <= MAX_IMPORT_BYTES) { "规则文件不能超过 5 MB" }
        val json = if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            readJsonFromZip(bytes)
        } else {
            bytes.toString(Charsets.UTF_8)
        }
        val array = JSONArray(json)
        require(array.length() <= MAX_RULES) { "规则数量不能超过 $MAX_RULES 条" }
        return List(array.length()) { index -> decodeRule(array.getJSONObject(index), index) }
    }

    fun encodeJson(rules: List<HighlightRule>, exportedAt: Long = System.currentTimeMillis()): ByteArray {
        val array = JSONArray()
        rules.sortedBy(HighlightRule::position).forEach { rule ->
            val json = runCatching { JSONObject(rule.rawJson) }.getOrElse { JSONObject() }
            json.put("id", rule.id)
            json.put("name", rule.name)
            json.put("pattern", rule.pattern)
            json.put("enabled", rule.enabled)
            json.put("position", rule.position)
            json.put("targetScope", rule.targetScope)
            rule.textColor?.let { json.put("textColor", it) } ?: json.remove("textColor")
            json.put("underlineMode", rule.underlineMode)
            json.put("underlineOffset", rule.underlineOffset.toDouble())
            json.put("underlineWidth", rule.underlineWidth.toDouble())
            json.put("fontWeight", rule.fontWeight)
            json.put("isItalic", rule.isItalic)
            json.put("sampleText", rule.sampleText)
            json.put("_lumi", JSONObject().apply {
                put("exportedBy", "LUMI")
                put("schemaVersion", 1)
                put("exportedAt", exportedAt)
            })
            array.put(json)
        }
        return array.toString(2).toByteArray(Charsets.UTF_8)
    }

    fun encodeZip(rules: List<HighlightRule>, exportedAt: Long = System.currentTimeMillis()): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(encodeJson(rules, exportedAt))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(JSONObject().apply {
                put("name", "LUMI Highlight Rules")
                put("generatedBy", "LUMI")
                put("schemaVersion", 1)
                put("exportedAt", exportedAt)
                put("notice", "Compatible rule format. Not affiliated with or endorsed by third parties.")
            }.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun decodeRule(json: JSONObject, index: Int): HighlightRule {
        val id = json.optString("id").ifBlank { UUID.randomUUID().toString() }
        val sourcePattern = json.optString("pattern")
        val pattern = normalizePattern(sourcePattern)
        require(pattern.isNotBlank()) { "第 ${index + 1} 条规则缺少正则" }
        require(pattern.length <= 4_096) { "第 ${index + 1} 条规则正则过长" }
        val rule = HighlightRule(
            id = id,
            name = json.optString("name").ifBlank { "规则 ${index + 1}" },
            pattern = pattern,
            enabled = json.optBoolean("enabled", true),
            position = json.optInt("position", index),
            targetScope = json.optInt("targetScope", HighlightRule.TARGET_BODY),
            textColor = if (json.has("textColor") && !json.isNull("textColor")) json.optInt("textColor") else null,
            underlineMode = json.optInt("underlineMode", HighlightRule.UNDERLINE_NONE),
            underlineOffset = json.optDouble("underlineOffset", 2.0).toFloat(),
            underlineWidth = json.optDouble("underlineWidth", 1.0).toFloat(),
            fontWeight = json.optInt("fontWeight", 400),
            isItalic = json.optBoolean("isItalic", false),
            sampleText = json.optString("sampleText"),
            rawJson = json.put("pattern", pattern).toString(),
            updatedAt = System.currentTimeMillis()
        )
        // Unsupported input remains in rawJson for round-trip export, but is never executed.
        // Literal control escapes are valid regex constructs and need no conversion here.
        val validation = HighlightRuleMatcher.validationError(rule)
        return if (validation == null || validation == "invalid escape sequence: \\n" ||
            validation == "invalid escape sequence: \\t"
        ) rule else rule.copy(enabled = false)
    }

    /**
     * legado exports JSON-escaped Unicode in some rule packs as a literal `\\uXXXX`
     * sequence. JSON decoding leaves that text untouched when it was double-escaped;
     * RE2 then rejects it as an invalid escape. Decode only Unicode escapes here, while
     * preserving all other regex syntax and the original unknown fields for export.
     */
    private fun normalizePattern(value: String): String {
        if (!value.contains("\\u")) return rewriteLargeRepetitions(value)
        val unicodeNormalized = buildString(value.length) {
            var index = 0
            while (index < value.length) {
                val slash = value[index] == '\\'
                val unicode = slash && index + 1 < value.length && value[index + 1] == 'u'
                val doubleUnicode = slash && index + 2 < value.length &&
                    value[index + 1] == '\\' && value[index + 2] == 'u'
                val unicodeStart = when {
                    unicode -> index + 2
                    doubleUnicode -> index + 3
                    else -> -1
                }
                if (unicodeStart >= 0 && unicodeStart + 4 <= value.length) {
                    val hex = value.substring(unicodeStart, unicodeStart + 4)
                    if (hex.all { it in "0123456789abcdefABCDEF" }) {
                        append(hex.toInt(16).toChar())
                        index = unicodeStart + 4
                        continue
                    }
                }
                append(value[index])
                index++
            }
        }
        return rewriteLargeRepetitions(unicodeNormalized)
    }

    /** RE2/J caps a bounded repetition at 1000; split larger bounds into safe chunks. */
    private fun rewriteLargeRepetitions(value: String): String {
        val output = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] == '{') {
                val close = value.indexOf('}', index + 1)
                if (close > index) {
                    val body = value.substring(index + 1, close)
                    val comma = body.indexOf(',')
                    val lowerText = if (comma < 0) body else body.substring(0, comma)
                    val upperText = when {
                        comma < 0 -> lowerText
                        comma == body.lastIndex -> null
                        else -> body.substring(comma + 1)
                    }
                    val lower = lowerText.toIntOrNull()
                    val upper = upperText?.toIntOrNull()
                    if (lower != null && upper != null && lower <= upper && upper > 1000) {
                        val atomStart = findAtomStart(output)
                        if (atomStart >= 0) {
                            val atom = output.substring(atomStart)
                            output.delete(atomStart, output.length)
                            appendRepeatExpansion(output, atom, lower, upper)
                            index = close + 1
                            continue
                        }
                    }
                }
            }
            output.append(value[index])
            index++
        }
        return output.toString()
    }

    private fun appendRepeatExpansion(output: StringBuilder, atom: String, lower: Int, upper: Int) {
        var remainingLower = lower
        var remainingUpper = upper
        while (remainingLower >= 1000) {
            output.append(atom).append("{1000}")
            remainingLower -= 1000
            remainingUpper -= 1000
        }
        if (remainingUpper <= 0) return
        val firstUpper = minOf(remainingUpper, 1000)
        val firstLower = minOf(remainingLower, firstUpper)
        output.append(atom)
            .append('{').append(firstLower).append(',').append(firstUpper).append('}')
        remainingUpper -= firstUpper
        while (remainingUpper > 0) {
            val chunk = minOf(remainingUpper, 1000)
            output.append(atom).append("{0,").append(chunk).append('}')
            remainingUpper -= chunk
        }
    }

    private fun findAtomStart(expression: StringBuilder): Int {
        if (expression.isEmpty()) return -1
        val end = expression.lastIndex
        return when (expression[end]) {
            ']' -> {
                var index = end - 1
                var escaped = false
                while (index >= 0) {
                    val char = expression[index]
                    if (!escaped && char == '[') return index
                    escaped = !escaped && char == '\\'
                    if (char != '\\') escaped = false
                    index--
                }
                -1
            }
            ')' -> {
                var depth = 1
                var index = end - 1
                while (index >= 0) {
                    when (expression[index]) {
                        ')' -> depth++
                        '(' -> {
                            depth--
                            if (depth == 0) return index
                        }
                    }
                    index--
                }
                -1
            }
            '\\' -> if (end > 0) end - 1 else -1
            else -> end
        }
    }

    private fun readJsonFromZip(bytes: ByteArray): String {
        var json: String? = null
        var total = 0
        val seen = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory) { "规则 ZIP 不能包含目录" }
                require(entry.name == JSON_ENTRY || entry.name == MANIFEST_ENTRY) { "规则 ZIP 包含不支持的文件" }
                require(seen.add(entry.name)) { "规则 ZIP 包含重复文件" }
                val data = zip.readEntryBytes(MAX_IMPORT_BYTES - total).also { total += it.size }
                require(total <= MAX_IMPORT_BYTES) { "解压后的规则文件不能超过 5 MB" }
                if (entry.name == JSON_ENTRY) json = data.toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        return requireNotNull(json) { "ZIP 根目录缺少 $JSON_ENTRY" }
    }

    private fun InputStream.readEntryBytes(limit: Int): ByteArray {
        require(limit >= 0) { "解压后的规则文件不能超过 5 MB" }
        val output = ByteArrayOutputStream(minOf(limit, 16 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "解压后的规则文件不能超过 5 MB" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
