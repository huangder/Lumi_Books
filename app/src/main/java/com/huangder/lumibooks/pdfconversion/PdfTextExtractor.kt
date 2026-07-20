package com.huangder.lumibooks.pdfconversion

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

data class PdfExtractionResult(
    val textPageCount: Int,
    val totalPageCount: Int,
    val meaningfulCharacterCount: Int
)

class NoPdfTextException : Exception("No readable PDF text layer")

class PdfTextExtractor @javax.inject.Inject constructor() {
    suspend fun extract(
        source: File,
        output: File,
        onProgress: suspend (currentPage: Int, totalPages: Int, progress: Int) -> Unit
    ): PdfExtractionResult {
        require(source.isFile) { "PDF file not found: ${source.absolutePath}" }
        output.parentFile?.mkdirs()

        val memoryUsage = MemoryUsageSetting.setupMixed(MEMORY_LIMIT_BYTES).apply {
            tempDir = output.parentFile
        }
        var textPages = 0
        var meaningfulCharacters = 0
        var wroteContent = false
        var previousPageText = ""

        PDDocument.load(source, memoryUsage).use { document ->
            val totalPages = document.numberOfPages
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
                lineSeparator = "\n"
                paragraphStart = "\n"
                paragraphEnd = "\n"
                pageStart = ""
                pageEnd = ""
            }

            output.bufferedWriter(Charsets.UTF_8).use { writer ->
                for (pageNumber in 1..totalPages) {
                    currentCoroutineContext().ensureActive()
                    stripper.startPage = pageNumber
                    stripper.endPage = pageNumber
                    val pageText = PdfTextReflowFormatter.reflow(stripper.getText(document))
                    val pageMeaningfulCharacters = pageText.count { it.isLetterOrDigit() }

                    if (pageText.isNotBlank()) {
                        if (wroteContent) {
                            writer.append(
                                PdfTextReflowFormatter.separatorBetween(previousPageText, pageText)
                            )
                        }
                        writer.append(pageText)
                        wroteContent = true
                        previousPageText = pageText
                    }
                    if (pageMeaningfulCharacters >= MIN_MEANINGFUL_CHARACTERS_PER_PAGE) {
                        textPages++
                    }
                    meaningfulCharacters += pageMeaningfulCharacters

                    val progress = if (totalPages == 0) 0 else pageNumber * 100 / totalPages
                    onProgress(pageNumber, totalPages, progress)
                }
            }

            if (textPages == 0 || meaningfulCharacters < MIN_MEANINGFUL_CHARACTERS_TOTAL) {
                throw NoPdfTextException()
            }
            return PdfExtractionResult(textPages, totalPages, meaningfulCharacters)
        }
    }

    private companion object {
        const val MEMORY_LIMIT_BYTES = 32L * 1024L * 1024L
        const val MIN_MEANINGFUL_CHARACTERS_PER_PAGE = 20
        const val MIN_MEANINGFUL_CHARACTERS_TOTAL = 20
    }
}

internal object PdfTextReflowFormatter {
    private val chapterHeadingPatterns = listOf(
        Regex("^第[一二三四五六七八九十百千万零〇两\\d]+[章节回卷篇部].*"),
        Regex("^(chapter|part)\\s+[\\divxlcdm]+.*", RegexOption.IGNORE_CASE)
    )
    private val bulletItemPattern = Regex("^(?:[•●○▪◦]|[-*])\\s*.+")
    private val numberedHeadingPattern = Regex("^\\d{1,3}[.)、]\\s*.+")

    fun reflow(rawText: String): String {
        if (rawText.isBlank()) return ""

        val paragraphs = mutableListOf<String>()
        val current = StringBuilder()
        var pendingParagraphBreak = false

        fun flushCurrent() {
            val value = current.toString().trim()
            if (value.isNotEmpty()) paragraphs += value
            current.clear()
        }

        rawText
            .replace('\u0000', ' ')
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .forEach { rawLine ->
                val line = removeSpacesBetweenCjk(
                    rawLine.trim().replace(Regex("[\\t ]+"), " ")
                )
                when {
                    line.isEmpty() -> pendingParagraphBreak = true
                    looksLikeStandaloneLine(line) -> {
                        flushCurrent()
                        paragraphs += line
                        pendingParagraphBreak = false
                    }
                    current.isEmpty() -> {
                        current.append(line)
                        pendingParagraphBreak = false
                    }
                    pendingParagraphBreak && endsParagraph(current) -> {
                        flushCurrent()
                        current.append(line)
                        pendingParagraphBreak = false
                    }
                    else -> {
                        appendSoftWrappedLine(current, line)
                        pendingParagraphBreak = false
                    }
                }
            }
        flushCurrent()

        return paragraphs.joinToString("\n\n")
    }

    private fun looksLikeStandaloneLine(line: String): Boolean {
        if (line.length > 80) return false
        if (chapterHeadingPatterns.any { it.matches(line) }) return true
        if (bulletItemPattern.matches(line)) return true
        return line.length <= 32 && numberedHeadingPattern.matches(line)
    }

    fun separatorBetween(previousText: String, nextText: String): String {
        if (previousText.isBlank() || nextText.isBlank()) return ""
        if (endsParagraph(previousText)) return "\n\n"
        val previous = previousText.lastOrNull { !it.isWhitespace() }
        val next = nextText.firstOrNull { !it.isWhitespace() }
        return if (previous != null && next != null && !isCjk(previous) && !isCjk(next)) " " else ""
    }

    private fun endsParagraph(text: CharSequence): Boolean {
        val trimmed = text.toString().trimEnd()
        if (trimmed.isEmpty()) return false
        if (trimmed.last() == ')' || trimmed.last() == '）') return true
        val withoutClosingQuotes = trimmed.trimEnd('”', '’', '"', '\'', '」', '』', '】', '》')
        return withoutClosingQuotes.lastOrNull() in setOf('。', '！', '？', '!', '?', '.')
    }

    private fun appendSoftWrappedLine(target: StringBuilder, nextLine: String) {
        val previous = target.lastOrNull()
        val next = nextLine.firstOrNull()
        if (previous == '-' && next?.isLowerCase() == true) {
            target.deleteCharAt(target.lastIndex)
            target.append(nextLine)
            return
        }

        if (previous != null && next != null && !isCjk(previous) && !isCjk(next)) {
            target.append(' ')
        }
        target.append(nextLine)
    }

    private fun removeSpacesBetweenCjk(line: String): String {
        if (' ' !in line) return line
        val cjkSpaceCount = line.indices.count { index ->
            line[index] == ' ' &&
                line.getOrNull(index - 1)?.let(::isCjk) == true &&
                line.getOrNull(index + 1)?.let(::isCjk) == true
        }
        if (cjkSpaceCount < 2) return line
        return buildString(line.length) {
            line.forEachIndexed { index, character ->
                val previous = line.getOrNull(index - 1)
                val next = line.getOrNull(index + 1)
                if (character != ' ' || previous == null || next == null || !isCjk(previous) || !isCjk(next)) {
                    append(character)
                }
            }
        }
    }

    private fun isCjk(character: Char): Boolean {
        return when (Character.UnicodeScript.of(character.code)) {
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.HANGUL -> true
            else -> false
        }
    }
}
