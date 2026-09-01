package com.huangder.lumibooks.util.parser

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** Lumi-native TXT TOC rule. Rules intentionally contain no executable code. */
data class TxtTocRule(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val chapterRegex: String,
    val volumeRegex: String? = null,
    val chapterTitleTemplate: String? = null,
    val volumeTitleTemplate: String? = null,
    val example: String = "",
    val enabled: Boolean = true,
    val order: Int = 0,
    val origin: TxtTocRuleOrigin = TxtTocRuleOrigin.CUSTOM
)

enum class TxtTocRuleOrigin { BUILTIN, CUSTOM }

enum class TxtTocRuleMode { AUTO, FIXED }

enum class TxtTocHeadingRole { CHAPTER, VOLUME }

data class TxtTocHeadingMatch(
    val role: TxtTocHeadingRole,
    val title: String,
    val sourceLine: String,
    val number: Int? = null
)

data class TxtTocRuleDiagnostics(
    val ruleId: String,
    val ruleName: String,
    val chapterMatches: Int,
    val volumeMatches: Int,
    val nonBlankLines: Int,
    val score: Int,
    val accepted: Boolean,
    val reason: String? = null,
    val samples: List<String> = emptyList()
)

data class CompiledTxtTocRule(
    val rule: TxtTocRule,
    private val chapter: Pattern,
    private val volume: Pattern?
) {
    fun match(line: String): TxtTocHeadingMatch? {
        val candidate = line.trim()
        if (candidate.isEmpty() || candidate.length > TxtTocRuleCompiler.MAX_LINE_LENGTH) return null
        val volumeMatch = volume?.matcher(candidate)?.takeIf { it.matches() }
        if (volumeMatch != null) {
            return TxtTocHeadingMatch(
                role = TxtTocHeadingRole.VOLUME,
                title = TxtTocRuleCompiler.renderTemplate(rule.volumeTitleTemplate, volumeMatch, candidate),
                sourceLine = candidate,
                number = extractNumber(volumeMatch)
            )
        }
        val chapterMatch = chapter.matcher(candidate).takeIf { it.matches() } ?: return null
        return TxtTocHeadingMatch(
            role = TxtTocHeadingRole.CHAPTER,
            title = TxtTocRuleCompiler.renderTemplate(rule.chapterTitleTemplate, chapterMatch, candidate),
            sourceLine = candidate,
            number = extractNumber(chapterMatch)
        )
    }

    private fun extractNumber(match: java.util.regex.Matcher): Int? {
        for (index in 1..match.groupCount()) {
            val value = match.group(index)?.trim() ?: continue
            value.toIntOrNull()?.let { return it }
        }
        return null
    }
}

object TxtTocRuleCompiler {
    const val MAX_REGEX_LENGTH = 2048
    const val MAX_LINE_LENGTH = 512
    private val nestedQuantifierRegex = Regex("\\([^\\r\\n()]{0,256}[+*][^\\r\\n()]{0,256}\\)[+*?]")
    private val unsafeRegex = Regex("(?:\\\\[1-9]|\\(\\?[=!<]|\\(\\?>|\\(\\?<[=!])")

    fun compile(rule: TxtTocRule): Result<CompiledTxtTocRule> = runCatching {
        require(rule.schemaVersion == 1) { "Unsupported TXT TOC rule version" }
        require(rule.id.isNotBlank() && rule.name.isNotBlank()) { "Rule id and name are required" }
        val chapter = compilePattern(rule.chapterRegex)
        val volume = rule.volumeRegex?.trim()?.takeIf { it.isNotEmpty() }?.let(::compilePattern)
        validateTemplate(rule.chapterTitleTemplate, chapter.matcher(""))
        if (volume != null) validateTemplate(rule.volumeTitleTemplate, volume.matcher(""))
        CompiledTxtTocRule(rule, chapter, volume)
    }

    private fun compilePattern(source: String): Pattern {
        require(source.length in 1..MAX_REGEX_LENGTH) { "Regex length must be 1-$MAX_REGEX_LENGTH" }
        require(!unsafeRegex.containsMatchIn(source)) {
            "Lookarounds and backreferences are not supported"
        }
        require(!nestedQuantifierRegex.containsMatchIn(source)) {
            "Nested quantifiers are not supported"
        }
        return try {
            Pattern.compile(source)
        } catch (error: PatternSyntaxException) {
            throw IllegalArgumentException("Invalid regex: ${error.description}", error)
        }
    }

    private fun validateTemplate(template: String?, matcher: java.util.regex.Matcher) {
        if (template.isNullOrEmpty()) return
        val references = Regex("\\$(\\d+)").findAll(template).map { it.groupValues[1].toInt() }
        val maxGroup = matcher.groupCount()
        require(references.all { it <= maxGroup }) { "Title template references a missing capture group" }
    }

    internal fun renderTemplate(
        template: String?,
        matcher: java.util.regex.Matcher,
        original: String
    ): String {
        if (template.isNullOrEmpty()) return original
        return Regex("\\$(\\$|\\d+)").replace(template) { match ->
            when (val reference = match.groupValues[1]) {
                "$$" -> "$"
                "0" -> matcher.group()
                else -> matcher.group(reference.toInt()).orEmpty()
            }
        }.trim().takeIf { it.isNotEmpty() } ?: original
    }
}

object TxtTocRuleBuiltIns {
    val all: List<TxtTocRule> = listOf(
        TxtTocRule(
            id = "builtin-multilingual",
            name = "中英文及日韩章节",
            chapterRegex = "(?:第\\s*[0-9０-９一二三四五六七八九十百千万零〇两]+\\s*[章节回话卷](?:.*)?|(?:序章|楔子|前言|终章|尾声|后记|番外)(?:.*)?|Chapter\\s+[0-9０-９IVXLCDM]+(?:.*)?|Section\\s+[0-9０-９IVXLCDM]+(?:.*)?|Episode\\s+[0-9０-９IVXLCDM]+(?:.*)?|(?:日本語|韓国語)?第\\s*[0-9０-９一二三四五六七八九十百千万零〇两]+\\s*(?:話|章|節)(?:.*)?|제\\s*[0-9０-９]+\\s*(?:장|화)(?:.*)?)",
            volumeRegex = "(?:第\\s*[0-9０-９一二三四五六七八九十百千万零〇两]+\\s*[卷篇部巻](?:.*)?|[卷篇部巻]\\s*[0-9０-９一二三四五六七八九十百千万零〇两]+(?:.*)?|(?:Volume|Vol\\.|Book|Part)\\s*[0-9０-９IVXLCDM]+(?:.*)?|제\\s*[0-9０-９]+\\s*권(?:.*)?)",
            order = 0,
            origin = TxtTocRuleOrigin.BUILTIN
        ),
        TxtTocRule(
            id = "builtin-decorated",
            name = "括号装饰标题",
            chapterRegex = "(?:<[^<>\\r\\n]{1,48}>|【[^【】\\r\\n]{1,48}】|\\[[^\\[\\]\\r\\n]{1,48}\\])",
            order = 1,
            origin = TxtTocRuleOrigin.BUILTIN
        ),
        TxtTocRule(
            id = "builtin-numbered",
            name = "数字编号章节",
            chapterRegex = "(?:第?([0-9]{1,5})[.、:：]\\s*\\S.{0,120}|([一二三四五六七八九十百千零〇两]+))",
            order = 2,
            origin = TxtTocRuleOrigin.BUILTIN
        )
    )

    fun byId(id: String?): TxtTocRule? = all.firstOrNull { it.id == id }
}

object TxtTocRuleCodec {
    const val TYPE = "lumi-txt-toc-rules"
    const val VERSION = 1

    fun encode(rules: List<TxtTocRule>): String {
        val array = JSONArray()
        rules.forEach { rule ->
            array.put(JSONObject().apply {
                put("schemaVersion", rule.schemaVersion)
                put("id", rule.id)
                put("name", rule.name)
                put("chapterRegex", rule.chapterRegex)
                rule.volumeRegex?.let { put("volumeRegex", it) }
                rule.chapterTitleTemplate?.let { put("chapterTitleTemplate", it) }
                rule.volumeTitleTemplate?.let { put("volumeTitleTemplate", it) }
                put("example", rule.example)
                put("enabled", rule.enabled)
                put("order", rule.order)
            })
        }
        return JSONObject().put("type", TYPE).put("version", VERSION).put("rules", array).toString()
    }

    fun decode(payload: String): List<TxtTocRule> {
        val root = JSONObject(payload)
        require(root.optString("type") == TYPE) { "Not a Lumi TXT TOC rules file" }
        require(root.optInt("version") == VERSION) { "Unsupported TXT TOC rules version" }
        val result = mutableListOf<TxtTocRule>()
        val array = root.optJSONArray("rules") ?: JSONArray()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val rule = TxtTocRule(
                schemaVersion = item.optInt("schemaVersion", 1),
                id = item.getString("id"),
                name = item.getString("name"),
                chapterRegex = item.getString("chapterRegex"),
                volumeRegex = item.optString("volumeRegex").takeIf { it.isNotBlank() },
                chapterTitleTemplate = item.optString("chapterTitleTemplate").takeIf { it.isNotEmpty() },
                volumeTitleTemplate = item.optString("volumeTitleTemplate").takeIf { it.isNotEmpty() },
                example = item.optString("example"),
                enabled = item.optBoolean("enabled", true),
                order = item.optInt("order", index),
                origin = TxtTocRuleOrigin.CUSTOM
            )
            TxtTocRuleCompiler.compile(rule).getOrThrow()
            result += rule
        }
        return result
    }

    fun fingerprint(rule: TxtTocRule?): String {
        val source = rule?.let { encode(listOf(it)) } ?: "auto-v1"
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
        return digest.joinToString("") { "%02x".format(Locale.ROOT, it) }.take(16)
    }
}

object TxtTocRuleSelector {
    fun choose(
        rules: List<TxtTocRule>,
        sampleLines: Sequence<String>
    ): Pair<TxtTocRule?, List<TxtTocRuleDiagnostics>> {
        val lines = sampleLines.map(String::trim).filter { it.isNotEmpty() }.toList()
        val diagnostics = rules.filter { it.enabled }.sortedBy { it.order }.mapNotNull { rule ->
            val compiled = TxtTocRuleCompiler.compile(rule).getOrNull() ?: return@mapNotNull TxtTocRuleDiagnostics(
                rule.id, rule.name, 0, 0, lines.size, Int.MIN_VALUE, false, "Invalid regex"
            )
            val matches = lines.mapNotNull(compiled::match)
            val chapterCount = matches.count { it.role == TxtTocHeadingRole.CHAPTER }
            val volumeCount = matches.count { it.role == TxtTocHeadingRole.VOLUME }
            val total = chapterCount + volumeCount
            val densityPenalty = if (lines.isEmpty()) 0 else total * 100 / lines.size
            val first = lines.indexOfFirst { compiled.match(it) != null }
            val coverage = if (first < 0) 0 else ((lines.size - first) * 100 / lines.size)
            val score = chapterCount * 20 + volumeCount * 25 + coverage - densityPenalty * 3
            val baseAccepted = chapterCount + volumeCount >= 2 &&
                (densityPenalty <= 20 || (total <= 5 && densityPenalty < 80))
            val numbering = matches.mapNotNull { it.number }
            val contiguousRatio = if (numbering.size < 2) 0f else {
                numbering.zipWithNext().count { (a, b) -> b == a + 1 }.toFloat() / (numbering.size - 1)
            }
            val highRiskAccepted = rule.id != "builtin-numbered" ||
                (first >= 0 && first <= lines.size / 4 && contiguousRatio >= 0.7f)
            val accepted = baseAccepted && highRiskAccepted
            TxtTocRuleDiagnostics(rule.id, rule.name, chapterCount, volumeCount, lines.size, score, accepted,
                reason = if (accepted) null else "Fewer than two reliable headings or matches are too dense",
                samples = matches.take(3).map { it.title })
        }
        return diagnostics.maxWithOrNull(compareBy<TxtTocRuleDiagnostics> { it.accepted }.thenBy { it.score })
            ?.takeIf { it.accepted }
            ?.let { selected -> rules.firstOrNull { it.id == selected.ruleId } } to diagnostics
    }
}
