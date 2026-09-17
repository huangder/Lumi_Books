package com.huangder.lumibooks.util.epub

import org.jsoup.nodes.Element

/**
 * 一条 CSS 规则（选择器 + 声明），已拍平 `@media`。
 *
 * 这里只服务阅读器真正关心的一小撮声明（宽度、背景图），不做完整 CSS 级联还原：
 * 同名声明按「后出现者胜、`!important` 优先」的近似规则取值。
 */
internal data class EpubCssDeclaration(
    val name: String,
    val value: String,
    val important: Boolean
)

internal data class EpubCssRule(
    val selector: String,
    /** 按书写顺序保留：`background` 简写与 `background-image` 互相覆盖时顺序就是语义。 */
    val declarations: List<EpubCssDeclaration>,
    val sourcePath: String
)

/** 元素命中的背景图声明（`background-image` 或 `background` 简写里的 url）。 */
internal data class EpubCssBackground(
    val url: String,
    val size: String?,
    val position: String?,
    val repeat: String?,
    /** 声明所在样式表路径：`url(...)` 相对它解析。 */
    val sourcePath: String
)

private fun EpubCssDeclaration.isBackground(): Boolean =
    name == "background" || name == "background-image"

/**
 * 书籍 CSS 的规则索引。
 *
 * 出版社的 CSS 常有 `@media`、`@import`、注释与多种简写，这里统一拍平成一条有序规则表，
 * 让「原排版铺满宽判定」和「阅读器排版装饰还原」用同一套解析结果。
 */
internal class EpubCssIndex(private val rules: List<EpubCssRule>) {
    val isEmpty: Boolean get() = rules.isEmpty()

    /** 书籍样式表里是否有背景图声明（没有就不必为装饰还原解析章节）。 */
    val hasBackgroundImageRules: Boolean =
        rules.any { rule -> rule.declarations.any { it.isBackground() && firstUrl(it.value) != null } }

    /** 书籍样式表里是否有宽度声明（没有就不必为尺寸还原解析章节）。 */
    val hasWidthRules: Boolean = rules.any { rule -> rule.declarations.any { it.name == "width" } }

    /** 命中选择器的元素上该声明的最终取值（`!important` 优先，其次取最后一条）。 */
    fun declaration(element: Element, property: String): String? {
        var value: String? = null
        var valueIsImportant = false
        for (rule in rules) {
            if (!matches(rule, element)) continue
            for (declaration in rule.declarations) {
                if (declaration.name != property) continue
                if (valueIsImportant && !declaration.important) continue
                value = declaration.value
                valueIsImportant = declaration.important
            }
        }
        return value
    }

    /** 元素上生效的背景图；纯色底与渐变不算（阅读器排版只还原背景图）。 */
    fun background(element: Element): EpubCssBackground? {
        var winner: String? = null
        var winnerPath: String? = null
        var winnerIsImportant = false
        rules.forEach ruleLoop@{ rule ->
            if (!matches(rule, element)) return@ruleLoop
            rule.declarations.forEach declarationLoop@{ declaration ->
                if (!declaration.isBackground()) return@declarationLoop
                // 后出现的声明覆盖先前的；已生效的 !important 只被 !important 覆盖。
                if (winner != null && winnerIsImportant && !declaration.important) {
                    return@declarationLoop
                }
                winner = declaration.value
                winnerIsImportant = declaration.important
                winnerPath = rule.sourcePath
            }
        }
        val url = firstUrl(winner) ?: return null
        return EpubCssBackground(
            url = url,
            size = declaration(element, BACKGROUND_SIZE),
            position = declaration(element, BACKGROUND_POSITION),
            repeat = declaration(element, BACKGROUND_REPEAT),
            sourcePath = winnerPath.orEmpty()
        )
    }

    /**
     * 发布方是否显式声明了「铺满宽度」。
     *
     * 只看匹配到该元素本身的声明（父容器宽度不算），与本次修复约定的判定口径一致。
     */
    fun declaresFullWidth(element: Element): Boolean {
        if (parseWidthKeywords(element.attr("style"))?.let(::isFullWidthValue) == true) return true
        if (isFullWidthValue(element.attr("width"))) return true
        val declared = declaration(element, "width") ?: return false
        return isFullWidthValue(declared)
    }

    /** 元素命中的宽度声明（原样返回，交给调用方换算）。 */
    fun widthDeclaration(element: Element): String? = declaration(element, "width")

    private fun matches(rule: EpubCssRule, element: Element): Boolean =
        runCatching { element.`is`(rule.selector) }.getOrDefault(false)

    companion object {
        private const val BACKGROUND = "background"
        private const val BACKGROUND_IMAGE = "background-image"
        private const val BACKGROUND_SIZE = "background-size"
        private const val BACKGROUND_POSITION = "background-position"
        private const val BACKGROUND_REPEAT = "background-repeat"

        val EMPTY = EpubCssIndex(emptyList())

        private val URL_REGEX = Regex("""url\(\s*(["']?)([^"')]+)\1\s*\)""", RegexOption.IGNORE_CASE)
        private val FULL_WIDTH_REGEX = Regex("""^(100(\.0+)?%|100(\.0+)?vw)$""", RegexOption.IGNORE_CASE)
        private val IMPORT_REGEX = Regex(
            """@import\s+(?:url\(\s*)?["']?([^"')\s;]+)["']?\s*\)?[^;]*;""",
            RegexOption.IGNORE_CASE
        )

        /** 解析若干样式表（按链接顺序传入）为一个索引。 */
        fun parse(stylesheets: List<Pair<String, String>>): EpubCssIndex {
            if (stylesheets.isEmpty()) return EMPTY
            val rules = mutableListOf<EpubCssRule>()
            stylesheets.forEach { (path, text) -> collectRules(text, path, rules) }
            return EpubCssIndex(rules)
        }

        /** 取出样式表里的 `@import` 目标（一层，交给调用方读取）。 */
        fun imports(cssText: String): List<String> =
            IMPORT_REGEX.findAll(stripComments(cssText)).map { it.groupValues[1].trim() }.toList()

        fun firstUrl(value: String?): String? {
            val declaration = value?.trim().orEmpty()
            if (declaration.isEmpty()) return null
            if (declaration.startsWith("none", ignoreCase = true)) return null
            return URL_REGEX.find(declaration)?.groupValues?.get(2)?.trim()?.takeIf { it.isNotEmpty() }
        }

        fun isFullWidthValue(value: String?): Boolean =
            FULL_WIDTH_REGEX.matches(value?.trim()?.lowercase().orEmpty())

        private fun collectRules(cssText: String, sourcePath: String, out: MutableList<EpubCssRule>) {
            parseInto(stripComments(cssText), sourcePath, out)
        }

        private fun parseInto(css: String, sourcePath: String, out: MutableList<EpubCssRule>) {
            var index = 0
            while (index < css.length) {
                val brace = topLevelIndexOf(css, '{', index)
                if (brace < 0) return
                // `@import url(...);`、`@charset "...";` 这类没有块的 at-rule 必须先跳过，
                // 否则它的前导会被当成下一条规则的选择器，把整条规则一起吞掉。
                val semicolon = topLevelIndexOf(css, ';', index)
                if (semicolon in 0 until brace) {
                    index = semicolon + 1
                    continue
                }
                val prelude = css.substring(index, brace).trim()
                val close = matchingBrace(css, brace)
                if (close < 0) return
                val body = css.substring(brace + 1, close)
                if (prelude.startsWith("@")) {
                    val lower = prelude.lowercase()
                    // 条件组（@media/@supports）里的规则拍平进主表；打印样式与阅读器无关。
                    if ((lower.startsWith("@media") || lower.startsWith("@supports")) &&
                        !lower.startsWith("@media print")
                    ) {
                        parseInto(body, sourcePath, out)
                    }
                } else if (prelude.isNotEmpty()) {
                    val declarations = parseDeclarations(body)
                    if (declarations.isNotEmpty()) {
                        splitTopLevel(prelude, ',').forEach { selector ->
                            if (selector.isNotBlank()) {
                                out += EpubCssRule(
                                    selector = selector,
                                    declarations = declarations,
                                    sourcePath = sourcePath
                                )
                            }
                        }
                    }
                }
                index = close + 1
            }
        }

        private fun parseDeclarations(body: String): List<EpubCssDeclaration> {
            val declarations = mutableListOf<EpubCssDeclaration>()
            splitTopLevel(body, ';').forEach { declaration ->
                val separator = topLevelIndexOf(declaration, ':')
                if (separator <= 0) return@forEach
                val name = declaration.substring(0, separator).trim().lowercase()
                if (name.isEmpty()) return@forEach
                var value = declaration.substring(separator + 1).trim()
                if (value.isEmpty()) return@forEach
                var important = false
                if (value.endsWith("!important", ignoreCase = true)) {
                    value = value.dropLast("!important".length).trim()
                    important = true
                }
                declarations += EpubCssDeclaration(name = name, value = value, important = important)
            }
            return declarations
        }

        /** `style="width: 100%; height: auto"` 里的宽度声明。 */
        private fun parseWidthKeywords(inlineStyle: String?): String? {
            val style = inlineStyle?.trim().orEmpty()
            if (style.isEmpty()) return null
            splitTopLevel(style, ';').forEach { declaration ->
                val separator = topLevelIndexOf(declaration, ':')
                if (separator <= 0) return@forEach
                if (declaration.substring(0, separator).trim().equals("width", ignoreCase = true)) {
                    return declaration.substring(separator + 1).trim()
                }
            }
            return null
        }

        private fun stripComments(css: String): String {
            if (!css.contains("/*")) return css
            return css.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        }

        private fun matchingBrace(text: String, openIndex: Int): Int {
            var depth = 0
            var index = openIndex
            var quote: Char? = null
            while (index < text.length) {
                val character = text[index]
                when {
                    quote != null -> if (character == quote) quote = null
                    character == '"' || character == '\'' -> quote = character
                    character == '{' -> depth++
                    character == '}' -> {
                        depth--
                        if (depth == 0) return index
                    }
                }
                index++
            }
            return -1
        }

        private fun splitTopLevel(text: String, separator: Char): List<String> {
            val parts = mutableListOf<String>()
            var depth = 0
            var quote: Char? = null
            var start = 0
            text.forEachIndexed { index, character ->
                when {
                    quote != null -> if (character == quote) quote = null
                    character == '"' || character == '\'' -> quote = character
                    character == '(' || character == '[' -> depth++
                    character == ')' || character == ']' -> if (depth > 0) depth--
                    character == separator && depth == 0 -> {
                        parts += text.substring(start, index).trim()
                        start = index + 1
                    }
                }
            }
            parts += text.substring(start).trim()
            return parts.filter(String::isNotEmpty)
        }

        private fun topLevelIndexOf(text: String, target: Char, from: Int = 0): Int {
            var depth = 0
            var quote: Char? = null
            for (index in from until text.length) {
                val character = text[index]
                when {
                    quote != null -> if (character == quote) quote = null
                    character == '"' || character == '\'' -> quote = character
                    character == '(' || character == '[' -> depth++
                    character == ')' || character == ']' -> if (depth > 0) depth--
                    character == target && depth == 0 -> return index
                }
            }
            return -1
        }
    }
}
