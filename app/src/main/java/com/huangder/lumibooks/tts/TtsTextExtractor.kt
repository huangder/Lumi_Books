package com.huangder.lumibooks.tts

class TtsTextExtractor {
    companion object {
        internal const val MAX_SENTENCE_LENGTH = 200
        internal val TERMINATORS = setOf('。', '！', '？', '；', '.', '!', '?', ';')
        internal val CLOSING_PUNCTUATION = setOf('”', '’', '"', '\'', '》', '】', '）', ')')
        private val SOFT_BREAKS = setOf('，', ',', '、', '：', ':')
    }

    fun extractPageText(
        chapterText: String,
        startCharOffset: Int,
        endCharOffset: Int
    ): String {
        val safeStart = startCharOffset.coerceIn(0, chapterText.length)
        val safeEnd = endCharOffset.coerceIn(safeStart, chapterText.length)
        return normalize(chapterText.substring(safeStart, safeEnd))
    }

    fun splitIntoSentences(text: String): List<String> {
        val normalized = normalize(text)
        if (normalized.isBlank()) return emptyList()

        val sentences = mutableListOf<String>()
        val buffer = StringBuilder()
        var index = 0
        while (index < normalized.length) {
            val char = normalized[index]
            buffer.append(char)

            if (char in TERMINATORS) {
                while (index + 1 < normalized.length && normalized[index + 1] in CLOSING_PUNCTUATION) {
                    index++
                    buffer.append(normalized[index])
                }
                flush(buffer, sentences)
            } else if (char == '\n' && index + 1 < normalized.length && normalized[index + 1] == '\n') {
                flush(buffer, sentences)
            } else if (buffer.length >= MAX_SENTENCE_LENGTH) {
                flushLongBuffer(buffer, sentences)
            }
            index++
        }
        flush(buffer, sentences)
        return sentences
    }

    /**
     * Splits source text without losing its original character offsets. The text sent to a remote
     * service is normalized, while offsets always address the unmodified source text.
     */
    fun splitIntoSegments(
        text: String,
        baseCharacterOffset: Int = 0,
        startCharacterOffset: Int? = null
    ): List<TtsTextSegment> {
        if (text.isBlank()) return emptyList()
        val localStart = startCharacterOffset
            ?.minus(baseCharacterOffset)
            ?.coerceIn(0, text.length)
            ?: 0
        if (localStart >= text.length) return emptyList()

        val segments = mutableListOf<TtsTextSegment>()
        var start = localStart
        var index = localStart
        while (index < text.length) {
            val char = text[index]
            val paragraphBreak = char == '\n' && index + 1 < text.length && text[index + 1] == '\n'
            val reachedLimit = index - start + 1 >= MAX_SENTENCE_LENGTH
            if (char in TERMINATORS || paragraphBreak || reachedLimit) {
                var end = index + 1
                if (char in TERMINATORS) {
                    while (end < text.length && text[end] in CLOSING_PUNCTUATION) end++
                } else if (reachedLimit) {
                    val softBreak = text.substring(start, end).indexOfLast { it in SOFT_BREAKS }
                    if (softBreak >= MAX_SENTENCE_LENGTH / 2) end = start + softBreak + 1
                }
                appendSegment(
                    text,
                    start,
                    end,
                    baseCharacterOffset,
                    segments,
                    canContinueAcrossPage = !reachedLimit
                )
                start = end
                index = end
                continue
            }
            index++
        }
        appendSegment(text, start, text.length, baseCharacterOffset, segments)
        return segments
    }

    data class MergeResult(
        val mergedText: String,
        val consumedSourceChars: Int
    )

    /**
     * True when [text] does not end with a sentence terminator, closing punctuation,
     * or paragraph break — indicating the sentence is likely split across a page boundary.
     */
    fun isTrailing(text: String): Boolean {
        if (text.isBlank()) return false
        val last = text.trimEnd().last()
        return last !in TERMINATORS && last !in CLOSING_PUNCTUATION
    }

    /**
     * Merges an incomplete trailing sentence with continuation raw text.
     * Stops at a sentence terminator, paragraph boundary, or [maxTotalLength].
     */
    fun mergeTrailingSentence(
        trailing: String,
        continuationRawText: String,
        maxTotalLength: Int = MAX_SENTENCE_LENGTH
    ): MergeResult {
        val budget = maxTotalLength - trailing.length
        if (budget <= 0 || continuationRawText.isBlank()) {
            return MergeResult(trailing.trim(), 0)
        }

        val merged = StringBuilder(trailing)
        var rawIndex = 0
        val raw = continuationRawText
        while (rawIndex < raw.length && merged.length < maxTotalLength) {
            val char = raw[rawIndex]
            when {
                char == '\uFFFC' || char == '\u200B' -> {
                    rawIndex++
                }
                char == '\r' -> {
                    val lineBreakLength = if (rawIndex + 1 < raw.length && raw[rawIndex + 1] == '\n') 2 else 1
                    val nextIndex = rawIndex + lineBreakLength
                    if (nextIndex < raw.length && (raw[nextIndex] == '\r' || raw[nextIndex] == '\n')) break
                    merged.append('\n')
                    rawIndex = nextIndex
                }
                char == '\n' -> {
                    if (rawIndex + 1 < raw.length && raw[rawIndex + 1] == '\n') break
                    merged.append(char)
                    rawIndex++
                }
                else -> {
                    merged.append(char)
                    rawIndex++
                    if (char in TERMINATORS) {
                        while (
                            rawIndex < raw.length &&
                            merged.length < maxTotalLength &&
                            raw[rawIndex] in CLOSING_PUNCTUATION
                        ) {
                            merged.append(raw[rawIndex])
                            rawIndex++
                        }
                        break
                    }
                }
            }
        }
        return MergeResult(merged.toString().trim(), rawIndex)
    }

    private fun appendSegment(
        source: String,
        start: Int,
        end: Int,
        baseCharacterOffset: Int,
        output: MutableList<TtsTextSegment>,
        canContinueAcrossPage: Boolean = true
    ) {
        if (start >= end) return
        val normalized = normalize(source.substring(start, end))
        if (normalized.isNotEmpty()) {
            output += TtsTextSegment(
                text = normalized,
                startCharacterOffset = baseCharacterOffset + start,
                endCharacterOffset = baseCharacterOffset + end,
                canContinueAcrossPage = canContinueAcrossPage
            )
        }
    }

    private fun normalize(text: String): String {
        return text
            .replace('\uFFFC', ' ')
            .replace('\u200B', ' ')
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("[\\t\\u00A0 ]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .trim()
    }

    private fun flushLongBuffer(buffer: StringBuilder, output: MutableList<String>) {
        val splitAt = buffer.indexOfLast { it in SOFT_BREAKS }
            .takeIf { it >= MAX_SENTENCE_LENGTH / 2 }
            ?.plus(1)
            ?: MAX_SENTENCE_LENGTH
        val sentence = buffer.substring(0, splitAt).trim()
        if (sentence.isNotEmpty()) output += sentence
        buffer.delete(0, splitAt)
    }

    private fun flush(buffer: StringBuilder, output: MutableList<String>) {
        val sentence = buffer.toString().trim()
        if (sentence.isNotEmpty()) output += sentence
        buffer.clear()
    }
}
