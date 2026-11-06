package com.huangder.lumibooks.ui.bookshelf.spine

/** Text shaping for printed spines: shortened titles and authors, and vertical setting. */
internal object SpineText {
    private val leadingGroup = Regex("^\\s*[（(【\\[〔][^）)】\\]〕]*[）)】\\]〕]\\s*")
    private val anyGroup = Regex("[（(【\\[〔][^）)】\\]〕]*[）)】\\]〕]")
    private val subtitleMarks = charArrayOf('：', ':', '（', '(', '【', '[', '〔', '—', '–', '|', '｜', '／')
    private val authorSplits = Regex("\\s*(,|，|、|/|／|&|;|；|\\band\\b|\\s+著|\\s+编)\\s*")

    /** Main title only: no edition prefix, no subtitle, no surrounding 《》. */
    fun mainTitle(raw: String): String {
        var title = raw.trim().replace(leadingGroup, "").trim()
        title = title.removePrefix("《").removeSuffix("》").trim()
        val dash = title.indexOf(" - ").takeIf { it >= 2 }
        val mark = title.indexOfFirst { it in subtitleMarks }.takeIf { it >= 2 }
        val cut = listOfNotNull(dash, mark).minOrNull()
        if (cut != null) title = title.substring(0, cut)
        title = title.trim().trimEnd('—', '–', '-', '·', ' ', '，', ',')
        return title.ifBlank { raw.trim() }
    }

    /** A short author line for the foot of the spine: a surname, or a few CJK characters. */
    fun shortAuthor(raw: String): String {
        val first = raw.replace(anyGroup, " ").split(authorSplits).firstOrNull { it.isNotBlank() }?.trim()
            ?: return ""
        if (isMostlyCjk(first)) {
            val name = first.substringAfterLast('·').substringAfterLast('•').trim()
            return name.replace(" ", "").take(4)
        }
        return first.split(Regex("\\s+")).lastOrNull { it.isNotBlank() }.orEmpty().take(14)
    }

    /**
     * One line per character for vertical setting. Short runs of Latin letters or digits stay
     * together on one line (as in "第3卷"), longer ones are set one character per line.
     */
    fun verticalUnits(text: String): List<String> {
        val units = ArrayList<String>()
        var index = 0
        while (index < text.length) {
            if (text[index].isAsciiAlphanumeric()) {
                var end = index
                while (end < text.length && text[end].isAsciiAlphanumeric()) end++
                val run = text.substring(index, end)
                if (run.length <= 2) units += run else run.forEach { units += it.toString() }
                index = end
                continue
            }
            val point = text.codePointAt(index)
            val char = String(Character.toChars(point))
            index += Character.charCount(point)
            if (char.isNotBlank()) units += char
        }
        return units
    }

    private fun Char.isAsciiAlphanumeric() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    /**
     * Largest font size (dp) between [minSize] and [maxSize] that sets [count] lines in [length];
     * when even [minSize] is too big, the number of lines that fit at [minSize].
     */
    fun fitVertical(count: Int, length: Float, maxSize: Float, minSize: Float, lineHeight: Float): Pair<Float, Int> {
        if (count <= 0) return maxSize to 0
        val size = (length / (count * lineHeight)).coerceAtMost(maxSize)
        if (size >= minSize) return size to count
        val fits = (length / (minSize * lineHeight)).toInt().coerceIn(1, count)
        return minSize to fits
    }
}
