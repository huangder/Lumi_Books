package com.huangder.lumibooks.util.parser

/** One readable comic page: the ZIP entry that holds the image plus its chapter membership. */
internal data class CbzPage(
    val entryName: String,
    val chapterIndex: Int,
    val pageInChapter: Int
)

/**
 * One comic chapter. A chapter is an image subdirectory inside the archive; archives that keep
 * every page at the root collapse into a single chapter with a `null` (unnamed) directory.
 */
internal data class CbzChapter(
    /** Raw directory name, or null when the pages live at the archive root. */
    val directory: String?,
    val firstPageIndex: Int,
    val pageCount: Int
)

internal data class CbzIndex(
    val pages: List<CbzPage>,
    val chapters: List<CbzChapter>
) {
    companion object {
        val EMPTY = CbzIndex(emptyList(), emptyList())
    }
}

/**
 * Turns raw ZIP entry names into a page order.
 *
 * Images are grouped by their parent directory (a "chapter"), sorted with numeric awareness so
 * `page_2` precedes `page_10`, and macOS/Windows scratch entries are dropped. The result is a flat
 * global page order plus the chapter boundaries the table of contents needs.
 */
internal object CbzPageIndex {
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")
    private val SKIPPED_FILE_NAMES = setOf(".ds_store", "thumbs.db", "desktop.ini")
    private const val MACOS_METADATA_DIRECTORY = "__macosx"

    fun build(entryNames: List<String>): CbzIndex {
        val images = entryNames.asSequence()
            .map { it.replace('\\', '/').trimStart('/') }
            .filter(::isImageEntry)
            .distinct()
            .sortedWith { left, right ->
                // Chapters first (a natural order over directories), then pages inside a chapter.
                val chapterComparison = NaturalOrder.compare(
                    left.substringBeforeLast('/', ""),
                    right.substringBeforeLast('/', "")
                )
                if (chapterComparison != 0) chapterComparison else NaturalOrder.compare(left, right)
            }
            .toList()
        if (images.isEmpty()) return CbzIndex.EMPTY

        val pages = ArrayList<CbzPage>(images.size)
        val chapters = ArrayList<CbzChapter>()
        var currentDirectory: String? = null
        var currentChapterIndex = -1
        var currentPageCount = 0
        var currentChapterFirstPage = 0

        images.forEach { name ->
            val directory = name.substringBeforeLast('/', "").ifBlank { null }
            if (currentChapterIndex < 0 || directory != currentDirectory) {
                if (currentChapterIndex >= 0) {
                    chapters += CbzChapter(currentDirectory, currentChapterFirstPage, currentPageCount)
                }
                currentDirectory = directory
                currentChapterIndex = chapters.size
                currentPageCount = 0
                currentChapterFirstPage = pages.size
            }
            pages += CbzPage(
                entryName = name,
                chapterIndex = currentChapterIndex,
                pageInChapter = currentPageCount
            )
            currentPageCount++
        }
        chapters += CbzChapter(currentDirectory, currentChapterFirstPage, currentPageCount)
        return CbzIndex(pages = pages, chapters = chapters)
    }

    fun isImageEntry(rawName: String): Boolean {
        val name = rawName.replace('\\', '/')
        if (name.isEmpty() || name.endsWith('/')) return false
        val segments = name.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return false
        if (segments.first().lowercase() == MACOS_METADATA_DIRECTORY) return false
        val fileName = segments.last()
        if (fileName.startsWith("._")) return false
        if (fileName.lowercase() in SKIPPED_FILE_NAMES) return false
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in IMAGE_EXTENSIONS
    }

    /**
     * A last path segment is used as the chapter title when the directory name carries no useful
     * text; the parser falls back to a generated chapter label in that case. Names that failed
     * ZIP name decoding (mojibake) contain replacement characters and are rejected too.
     */
    fun chapterDirectoryLabel(directory: String?): String? {
        val segment = directory.orEmpty().substringAfterLast('/')
        return segment.takeIf { label ->
            label.isNotBlank() &&
                '\uFFFD' !in label &&
                label.none { it.isISOControl() } &&
                label.any(Char::isLetterOrDigit)
        }
    }
}

/** Case-insensitive natural ordering: digits compare by value, text by code point. */
internal object NaturalOrder : Comparator<String> {
    override fun compare(left: String, right: String): Int {
        var leftIndex = 0
        var rightIndex = 0
        while (leftIndex < left.length && rightIndex < right.length) {
            val leftChar = left[leftIndex]
            val rightChar = right[rightIndex]
            if (leftChar.isDigit() && rightChar.isDigit()) {
                val leftStart = leftIndex
                val rightStart = rightIndex
                while (leftIndex < left.length && left[leftIndex].isDigit()) leftIndex++
                while (rightIndex < right.length && right[rightIndex].isDigit()) rightIndex++
                val leftDigits = left.substring(leftStart, leftIndex).trimStart('0')
                val rightDigits = right.substring(rightStart, rightIndex).trimStart('0')
                if (leftDigits.length != rightDigits.length) {
                    return leftDigits.length - rightDigits.length
                }
                val digitComparison = leftDigits.compareTo(rightDigits)
                if (digitComparison != 0) return digitComparison
            } else {
                val leftLower = leftChar.lowercaseChar()
                val rightLower = rightChar.lowercaseChar()
                if (leftLower != rightLower) return leftLower - rightLower
                leftIndex++
                rightIndex++
            }
        }
        return (left.length - leftIndex) - (right.length - rightIndex)
    }
}
