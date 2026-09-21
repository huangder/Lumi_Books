package com.huangder.lumibooks.util.parser

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Rule dialects Lumi can execute.
 *
 * [LUMI] rules are native and intentionally contain no executable code.
 * [LEGADO] rules are imported from a third-party reader rule file (`txtTocRule.json` shape).
 */
enum class TxtTocDialect { LUMI, LEGADO }

/**
 * TXT TOC rule.
 *
 * Third-party rules only contribute their regex ([chapterRegex], which is the third-party `rule`
 * field). Their optional `replacement` script is kept so the rule can be written back verbatim,
 * but Lumi never evaluates it.
 */
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
    val origin: TxtTocRuleOrigin = TxtTocRuleOrigin.CUSTOM,
    val dialect: TxtTocDialect = TxtTocDialect.LUMI,
    /** Raw third-party `replacement` value. Stored for round-trips, never executed. */
    val replacement: String? = null,
    /** Third-party `serialNumber`, the ordering hint of its selection algorithm. */
    val serialNumber: Int? = null,
    /** Original third-party rule id, used to rebuild a lossless source JSON array. */
    val thirdPartyId: Long? = null
) {
    /** True when the source rule carries a script snippet that Lumi deliberately ignores. */
    val hasIgnoredScript: Boolean
        get() = dialect == TxtTocDialect.LEGADO && !replacement.isNullOrBlank()
}

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
    val samples: List<String> = emptyList(),
    val dialect: TxtTocDialect = TxtTocDialect.LUMI
)

data class CompiledTxtTocRule(
    val rule: TxtTocRule,
    private val chapter: Pattern,
    private val volume: Pattern?
) {
    /**
     * Matches one decoded line.
     *
     * [line] is the raw line for [TxtTocDialect.LEGADO] and is trimmed internally for
     * [TxtTocDialect.LUMI]; [previousLine]/[nextLine] are only used by the third-party dialect,
     * which needs look-around context because its patterns were written against a whole file.
     */
    fun match(line: String, previousLine: String? = null, nextLine: String? = null): TxtTocHeadingMatch? =
        if (rule.dialect == TxtTocDialect.LEGADO) {
            matchThirdParty(line, previousLine, nextLine)
        } else {
            matchLumi(line)
        }

    private fun matchLumi(line: String): TxtTocHeadingMatch? {
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

    /**
     * Third-party rules are written against the whole decoded file with [Pattern.MULTILINE] and
     * `find()`. Lumi streams the file, so the current line is matched inside a small window that
     * also carries the neighbouring lines; a hit only counts when it starts and ends inside the
     * current line, and the matched text is the title (third-party rules have no `$1` templates).
     */
    private fun matchThirdParty(line: String, previousLine: String?, nextLine: String?): TxtTocHeadingMatch? {
        if (line.length > TxtTocRuleCompiler.MAX_THIRD_PARTY_LINE_LENGTH) return null
        val previous = previousLine.orEmpty().takeLast(TxtTocRuleCompiler.THIRD_PARTY_CONTEXT_LENGTH)
        val next = nextLine.orEmpty().take(TxtTocRuleCompiler.THIRD_PARTY_CONTEXT_LENGTH)
        val window = buildString(previous.length + line.length + next.length + 3) {
            append('\n').append(previous).append('\n').append(line).append('\n').append(next)
        }
        val lineStart = previous.length + 2
        val lineEnd = lineStart + line.length
        val matcher = chapter.matcher(window)
        while (matcher.find()) {
            val start = matcher.start()
            if (start < lineStart) continue
            if (start > lineEnd) return null
            val end = matcher.end()
            if (end > lineEnd) continue
            if (end <= start) continue
            return TxtTocHeadingMatch(
                role = TxtTocHeadingRole.CHAPTER,
                title = window.substring(start, end),
                sourceLine = line,
                number = null
            )
        }
        return null
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

    /** Third-party rules may carry long alternations, but a paragraph is never a heading. */
    const val MAX_THIRD_PARTY_LINE_LENGTH = 2048

    /** How much of the previous/next line is exposed to third-party look-around patterns. */
    const val THIRD_PARTY_CONTEXT_LENGTH = 512

    private val nestedQuantifierRegex = Regex("\\([^\\r\\n()]{0,256}[+*][^\\r\\n()]{0,256}\\)[+*?]")
    private val lumiUnsafeRegex = Regex("(?:\\\\[1-9]|\\(\\?[=!<]|\\(\\?>|\\(\\?<[=!])")
    private val backReferenceRegex = Regex("\\\\[1-9]")

    fun compile(rule: TxtTocRule): Result<CompiledTxtTocRule> = runCatching {
        require(rule.schemaVersion == 1) { "Unsupported TXT TOC rule version" }
        require(rule.id.isNotBlank() && rule.name.isNotBlank()) { "Rule id and name are required" }
        when (rule.dialect) {
            TxtTocDialect.LUMI -> compileLumi(rule)
            TxtTocDialect.LEGADO -> compileThirdParty(rule)
        }
    }

    private fun compileLumi(rule: TxtTocRule): CompiledTxtTocRule {
        val chapter = compilePattern(rule.chapterRegex)
        val volume = rule.volumeRegex?.trim()?.takeIf { it.isNotEmpty() }?.let(::compilePattern)
        validateTemplate(rule.chapterTitleTemplate, chapter.matcher(""))
        if (volume != null) validateTemplate(rule.volumeTitleTemplate, volume.matcher(""))
        return CompiledTxtTocRule(rule, chapter, volume)
    }

    /**
     * Third-party patterns rely on look-around (12 of the 26 shipped defaults use `(?<=...)`), so
     * that guard is relaxed here while back-references and nested quantifiers stay forbidden to
     * keep catastrophic backtracking bounded.
     */
    private fun compileThirdParty(rule: TxtTocRule): CompiledTxtTocRule {
        val source = rule.chapterRegex
        require(source.length in 1..MAX_REGEX_LENGTH) { "Regex length must be 1-$MAX_REGEX_LENGTH" }
        require(!backReferenceRegex.containsMatchIn(source)) { "Backreferences are not supported" }
        require(!nestedQuantifierRegex.containsMatchIn(source)) {
            "Nested quantifiers are not supported"
        }
        val chapter = try {
            Pattern.compile(source, Pattern.MULTILINE)
        } catch (error: PatternSyntaxException) {
            throw IllegalArgumentException("Invalid regex: ${error.description}", error)
        }
        // Third-party rules have no volume or title-template concept.
        return CompiledTxtTocRule(rule, chapter, null)
    }

    private fun compilePattern(source: String): Pattern {
        require(source.length in 1..MAX_REGEX_LENGTH) { "Regex length must be 1-$MAX_REGEX_LENGTH" }
        require(!lumiUnsafeRegex.containsMatchIn(source)) {
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
        ),
        TxtTocRule(
            id = "builtin-symbol-prefixed",
            name = "符号开头章节",
            chapterRegex = "^[☆★✦✧◆◇●○■□▪▫•※✱✲✳✴✵✶✷✸✹✺✻✼✽✾✿]\\s*[、,.．:：\\-—–]\\s*\\S.{0,120}$",
            order = 3,
            origin = TxtTocRuleOrigin.BUILTIN
        )
    )

    fun byId(id: String?): TxtTocRule? = all.firstOrNull { it.id == id }
}

object TxtTocRuleCodec {
    const val TYPE = "lumi-txt-toc-rules"
    const val VERSION = 1

    /** Optional envelope key holding third-party compatible rules verbatim. */
    const val LEGADO_SECTION = "legadoRules"

    fun encode(rules: List<TxtTocRule>, thirdPartyRules: List<TxtTocRule> = emptyList()): String {
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
                // Only third-party rules carry the dialect marker, so native rule fingerprints
                // (and therefore cached chapter indexes) stay identical to earlier versions.
                if (rule.dialect == TxtTocDialect.LEGADO) {
                    put("dialect", TxtTocDialect.LEGADO.name)
                    rule.replacement?.let { put("replacement", it) }
                    rule.serialNumber?.let { put("serialNumber", it) }
                    rule.thirdPartyId?.let { put("thirdPartyId", it) }
                }
            })
        }
        val root = JSONObject().put("type", TYPE).put("version", VERSION).put("rules", array)
        if (thirdPartyRules.isNotEmpty()) {
            root.put(LEGADO_SECTION, LegadoTxtTocRuleCodec.encodeArray(thirdPartyRules))
        }
        return root.toString()
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
                origin = TxtTocRuleOrigin.CUSTOM,
                dialect = if (item.optString("dialect") == TxtTocDialect.LEGADO.name) {
                    TxtTocDialect.LEGADO
                } else {
                    TxtTocDialect.LUMI
                },
                replacement = item.optString("replacement").takeIf { it.isNotBlank() },
                serialNumber = item.optInt("serialNumber").takeIf { item.has("serialNumber") },
                thirdPartyId = item.optLong("thirdPartyId").takeIf { item.has("thirdPartyId") }
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

/**
 * Reader/writer for third-party `txtTocRule.json` files
 * (`id`/`name`/`rule`/`replacement`/`example`/`serialNumber`/`enable`).
 *
 * Only the regex is executed. The optional `replacement` script and the other fields are kept so
 * the file can be written back unchanged.
 */
object LegadoTxtTocRuleCodec {
    const val ID_PREFIX = "legado-"

    /** Asset holding the third-party default rule set. */
    const val PRESET_ASSET = "legado_txt_toc_rules.json"

    private val bookSourceKeys = listOf(
        "bookSourceUrl",
        "bookSourceName",
        "bookSourceGroup",
        "bookSourceType",
        "bookSourceComment",
        "bookSources",
        "ruleToc",
        "ruleContent",
        "ruleSearch",
        "ruleBookInfo",
        "ruleExplore"
    )

    fun toRuleId(thirdPartyId: Long): String = "$ID_PREFIX$thirdPartyId"

    fun thirdPartyId(ruleId: String): Long? =
        ruleId.takeIf { it.startsWith(ID_PREFIX) }?.removePrefix(ID_PREFIX)?.toLongOrNull()

    /** True when the node carries online book-source fields, which Lumi never accepts. */
    fun looksLikeBookSource(node: JSONObject): Boolean = bookSourceKeys.any { node.has(it) }

    fun encodeArray(rules: List<TxtTocRule>): JSONArray {
        val array = JSONArray()
        rules.forEach { rule ->
            array.put(JSONObject().apply {
                put("id", rule.thirdPartyId ?: 0L)
                put("name", rule.name)
                put("rule", rule.chapterRegex)
                put("replacement", rule.replacement.orEmpty())
                put("example", rule.example)
                put("serialNumber", rule.serialNumber ?: rule.order)
                put("enable", rule.enabled)
            })
        }
        return array
    }

    fun encode(rules: List<TxtTocRule>): String = encodeArray(rules).toString()

    /** Reads a stored compatible-rule array, dropping entries that no longer compile. */
    fun decode(payload: String): List<TxtTocRule> {
        if (payload.isBlank()) return emptyList()
        return runCatching { toRules(JSONArray(payload), TxtTocRuleImportCounters()) }
            .getOrDefault(emptyList())
    }

    fun toRules(array: JSONArray, counters: TxtTocRuleImportCounters): List<TxtTocRule> {
        val result = mutableListOf<TxtTocRule>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
            if (item == null) {
                counters.skipped++
                continue
            }
            val rule = toModel(item)
            if (rule == null) {
                counters.skipped++
                continue
            }
            if (TxtTocRuleCompiler.compile(rule).isFailure) {
                counters.skipped++
                continue
            }
            if (rule.hasIgnoredScript) counters.scriptIgnored++
            result += rule
        }
        return normalize(result)
    }

    /** Sorts by `serialNumber` (then import order) and refreshes the display order. */
    fun normalize(rules: List<TxtTocRule>): List<TxtTocRule> = rules
        .withIndex()
        .sortedWith(
            compareBy<IndexedValue<TxtTocRule>> { it.value.serialNumber ?: Int.MAX_VALUE }
                .thenBy { it.index }
        )
        .mapIndexed { index, indexed -> indexed.value.copy(order = index) }

    fun merge(existing: List<TxtTocRule>, imported: List<TxtTocRule>): List<TxtTocRule> {
        val merged = ArrayList<TxtTocRule>(existing.size + imported.size)
        val importedById = imported.associateBy { it.id }
        existing.forEach { rule -> merged += importedById[rule.id] ?: rule }
        val existingIds = existing.mapTo(HashSet()) { it.id }
        imported.filterNotTo(merged) { it.id in existingIds }
        return normalize(merged)
    }

    private fun toModel(item: JSONObject): TxtTocRule? {
        val ruleText = item.optString("rule")
        if (ruleText.isBlank()) return null
        val rawId = if (item.has("id")) {
            item.optLong("id")
        } else {
            "${item.optString("name")}\u0000$ruleText".hashCode().toLong()
        }
        val name = item.optString("name").ifBlank { "Rule $rawId" }
        val serialNumber = item.optInt("serialNumber", -1)
        return TxtTocRule(
            id = toRuleId(rawId),
            name = name,
            chapterRegex = ruleText,
            example = item.optString("example"),
            enabled = item.optBoolean("enable", true),
            order = serialNumber.coerceAtLeast(0),
            origin = TxtTocRuleOrigin.CUSTOM,
            dialect = TxtTocDialect.LEGADO,
            replacement = item.optString("replacement").takeIf { it.isNotBlank() },
            serialNumber = serialNumber,
            thirdPartyId = rawId
        )
    }
}

class TxtTocRuleImportException(
    val reason: Reason,
    cause: Throwable? = null
) : IllegalArgumentException(reason.name, cause) {
    enum class Reason { NOT_JSON, UNKNOWN_FORMAT, BOOK_SOURCE_UNSUPPORTED, EMPTY }
}

class TxtTocRuleImportCounters {
    var skipped = 0
    var scriptIgnored = 0
}

data class TxtTocRuleImportResult(
    val lumiRules: List<TxtTocRule>,
    val thirdPartyRules: List<TxtTocRule>,
    val skipped: Int,
    val scriptIgnored: Int
)

/**
 * Detects the payload shape of an imported TXT TOC rule file.
 *
 * Accepted shapes: the Lumi envelope, a third-party rule array, a single third-party rule object
 * and a Lumi envelope carrying an optional third-party section. Online book-source payloads are
 * rejected explicitly: Lumi has no book-source features and never will.
 */
object TxtTocRuleImport {
    fun parse(payload: String): TxtTocRuleImportResult {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) {
            throw TxtTocRuleImportException(TxtTocRuleImportException.Reason.EMPTY)
        }
        val counters = TxtTocRuleImportCounters()
        val parsed = when (trimmed.first()) {
            '[' -> parseThirdPartyArray(trimmed, counters)
            '{' -> parseObject(trimmed, counters)
            else -> throw TxtTocRuleImportException(TxtTocRuleImportException.Reason.NOT_JSON)
        }
        return TxtTocRuleImportResult(
            lumiRules = parsed.first,
            thirdPartyRules = parsed.second,
            skipped = counters.skipped,
            scriptIgnored = counters.scriptIgnored
        )
    }

    private fun parseObject(
        payload: String,
        counters: TxtTocRuleImportCounters
    ): Pair<List<TxtTocRule>, List<TxtTocRule>> {
        val root = try {
            JSONObject(payload)
        } catch (error: JSONException) {
            throw TxtTocRuleImportException(TxtTocRuleImportException.Reason.NOT_JSON, error)
        }
        if (LegadoTxtTocRuleCodec.looksLikeBookSource(root)) {
            throw TxtTocRuleImportException(TxtTocRuleImportException.Reason.BOOK_SOURCE_UNSUPPORTED)
        }
        if (root.optString("type") == TxtTocRuleCodec.TYPE) {
            val lumi = TxtTocRuleCodec.decode(payload)
            val thirdParty = root.optJSONArray(TxtTocRuleCodec.LEGADO_SECTION)
                ?.let { LegadoTxtTocRuleCodec.toRules(it, counters) }
                .orEmpty()
            return lumi to thirdParty
        }
        root.optJSONArray(TxtTocRuleCodec.LEGADO_SECTION)?.let { section ->
            return emptyList<TxtTocRule>() to LegadoTxtTocRuleCodec.toRules(section, counters)
        }
        if (root.has("rule")) {
            val single = JSONArray().put(root)
            return emptyList<TxtTocRule>() to LegadoTxtTocRuleCodec.toRules(single, counters)
        }
        throw TxtTocRuleImportException(TxtTocRuleImportException.Reason.UNKNOWN_FORMAT)
    }

    private fun parseThirdPartyArray(
        payload: String,
        counters: TxtTocRuleImportCounters
    ): Pair<List<TxtTocRule>, List<TxtTocRule>> {
        val array = try {
            JSONArray(payload)
        } catch (error: JSONException) {
            throw TxtTocRuleImportException(TxtTocRuleImportException.Reason.NOT_JSON, error)
        }
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            if (LegadoTxtTocRuleCodec.looksLikeBookSource(item)) {
                throw TxtTocRuleImportException(TxtTocRuleImportException.Reason.BOOK_SOURCE_UNSUPPORTED)
            }
        }
        return emptyList<TxtTocRule>() to LegadoTxtTocRuleCodec.toRules(array, counters)
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
            val matches = lines.mapNotNull { line -> compiled.match(line) }
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
            val highRiskAccepted = when (rule.id) {
                "builtin-numbered" -> first >= 0 && first <= lines.size / 4 && contiguousRatio >= 0.7f
                // Symbol-led lines are also used for lists, so require an early first hit and
                // keep the match count proportional for larger samples instead of capping it.
                "builtin-symbol-prefixed" -> first >= 0 && first <= lines.size / 3 &&
                    total <= maxOf(20, lines.size / 10)
                else -> true
            }
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

/**
 * Port of the third-party selection loop: walk the enabled rules in `serialNumber` order and keep
 * the first one whose accepted hits are not swamped by hits that sit suspiciously close together
 * (those are usually volume lines, not chapters).
 */
object LegadoTocRuleSelector {
    private const val OVER_RULE_COUNT = 2
    private const val FAST_ACCEPT_HITS = 70
    private const val DISTANT_CONTENT_LENGTH = 1000
    private const val NEAR_CONTENT_LENGTH = 100

    data class Selection(
        val rule: TxtTocRule?,
        val diagnostics: List<TxtTocRuleDiagnostics>
    )

    fun choose(rules: List<TxtTocRule>, sampleLines: List<String>): Selection {
        val candidates = rules
            .filter { it.enabled && it.dialect == TxtTocDialect.LEGADO }
            .sortedWith(compareBy({ it.serialNumber ?: Int.MAX_VALUE }, { it.order }))
        val diagnostics = mutableListOf<TxtTocRuleDiagnostics>()
        var maxHits = -1
        var selected: TxtTocRule? = null
        for (rule in candidates) {
            val compiled = TxtTocRuleCompiler.compile(rule).getOrNull()
            if (compiled == null) {
                diagnostics += TxtTocRuleDiagnostics(
                    rule.id, rule.name, 0, 0, sampleLines.size, Int.MIN_VALUE, false,
                    reason = "Invalid regex", dialect = TxtTocDialect.LEGADO
                )
                continue
            }
            var start = 0L
            var hits = 0
            var closeHits = 0
            var offset = 0L
            val samples = mutableListOf<String>()
            for (index in sampleLines.indices) {
                val line = sampleLines[index]
                val match = compiled.match(
                    line,
                    sampleLines.getOrNull(index - 1),
                    sampleLines.getOrNull(index + 1)
                )
                if (match != null) {
                    val distance = offset - start
                    if (start == 0L || distance > DISTANT_CONTENT_LENGTH) {
                        hits++
                        start = offset + match.title.length
                        if (samples.size < 3) samples += match.title
                    } else if (distance < NEAR_CONTENT_LENGTH) {
                        closeHits++
                    }
                }
                offset += line.length + 1
            }
            val accepted = hits >= closeHits * 3 && hits > maxHits + OVER_RULE_COUNT
            diagnostics += TxtTocRuleDiagnostics(
                ruleId = rule.id,
                ruleName = rule.name,
                chapterMatches = hits,
                volumeMatches = 0,
                nonBlankLines = sampleLines.size,
                score = hits * 20,
                accepted = accepted,
                reason = if (accepted) null else "Not enough consecutive headings for this rule",
                samples = samples,
                dialect = TxtTocDialect.LEGADO
            )
            if (accepted) {
                maxHits = hits
                selected = rule
                if (maxHits > FAST_ACCEPT_HITS) break
            }
        }
        return Selection(selected, diagnostics)
    }
}
