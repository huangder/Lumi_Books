package com.huangder.lumibooks.tts

class TtsTextExtractor {
    companion object {
        private const val MAX_SENTENCE_LENGTH = 200
        private val TERMINATORS = setOf('。', '！', '？', '；', '.', '!', '?', ';')
        private val CLOSING_PUNCTUATION = setOf('”', '’', '"', '\'', '》', '】', '）', ')')
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
