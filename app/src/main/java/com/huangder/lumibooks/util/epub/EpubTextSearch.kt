package com.huangder.lumibooks.util.epub

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal object EpubTextSearch {
    private const val MAX_RESOURCE_BYTES = 64 * 1024 * 1024
    private const val MAX_SEARCH_RESULTS = 200
    private const val PREFIX_LENGTH = 32
    private const val SUFFIX_LENGTH = 32

    suspend fun search(
        filePath: String,
        epubPackage: EpubPackage,
        query: String,
        maxResults: Int = 200
    ): List<EpubSearchMatch> = withContext(Dispatchers.IO) {
        val normalizedQuery = normalizeQuery(query)
        val resultLimit = maxResults.coerceIn(0, MAX_SEARCH_RESULTS)
        if (normalizedQuery.isEmpty() || resultLimit == 0) return@withContext emptyList()

        val results = ArrayList<EpubSearchMatch>(minOf(resultLimit, 32))
        ZipFile(filePath).use { zipFile ->
            val entriesByLowercase = zipFile.entries().asSequence()
                .associateBy { it.name.lowercase() }
            for ((chapterIndex, spineItem) in epubPackage.spine.withIndex()) {
                currentCoroutineContext().ensureActive()
                val path = spineItem.manifestItem.fullPath
                val entry = zipFile.getEntry(path) ?: entriesByLowercase[path.lowercase()] ?: continue
                if (entry.size > MAX_RESOURCE_BYTES) continue
                val resource = try {
                    EpubResource(
                        path = path,
                        mediaType = spineItem.manifestItem.mediaType,
                        bytes = zipFile.getInputStream(entry).use { input -> readBounded(input) }
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    continue
                }
                val chapterText = try {
                    EpubDocumentTransformer.extractSearchText(resource)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    continue
                }
                collectMatches(
                    chapterIndex = chapterIndex,
                    href = path,
                    chapterText = chapterText,
                    normalizedQuery = normalizedQuery,
                    maxResults = resultLimit,
                    destination = results
                )
                if (results.size >= resultLimit) break
            }
        }
        results
    }

    private fun collectMatches(
        chapterIndex: Int,
        href: String,
        chapterText: String,
        normalizedQuery: String,
        maxResults: Int,
        destination: MutableList<EpubSearchMatch>
    ) {
        if (chapterText.isEmpty()) return
        val normalized = normalizeWithOffsets(chapterText)
        if (normalized.text.isEmpty()) return

        var searchFrom = 0
        while (searchFrom <= normalized.text.length - normalizedQuery.length && destination.size < maxResults) {
            val found = normalized.text.indexOf(normalizedQuery, searchFrom, ignoreCase = true)
            if (found < 0) break
            val rawStart = normalized.sourceOffsets[found]
            val rawEnd = normalized.sourceOffsets[found + normalizedQuery.length - 1] + 1
            val exact = chapterText.substring(rawStart, rawEnd)
            val contextStart = (rawStart - 12).coerceAtLeast(0)
            val contextEnd = (rawEnd + 20).coerceAtMost(chapterText.length)
            destination += EpubSearchMatch(
                chapterIndex = chapterIndex,
                charOffset = rawStart,
                matchLength = rawEnd - rawStart,
                context = chapterText.substring(contextStart, contextEnd).toSearchContext(),
                locator = EpubLocator(
                    version = 2,
                    href = href,
                    textPosition = rawStart,
                    textLength = chapterText.length,
                    exact = exact,
                    prefix = chapterText.substring((rawStart - PREFIX_LENGTH).coerceAtLeast(0), rawStart),
                    suffix = chapterText.substring(rawEnd, (rawEnd + SUFFIX_LENGTH).coerceAtMost(chapterText.length)),
                    progression = rawStart.toFloat() / chapterText.length.coerceAtLeast(1)
                )
            )
            searchFrom = found + normalizedQuery.length.coerceAtLeast(1)
        }
    }

    private fun normalizeQuery(value: String): String = buildString(value.length) {
        value.forEach { character ->
            if (!character.isSearchWhitespace()) append(character)
        }
    }

    private fun normalizeWithOffsets(value: String): NormalizedText {
        val text = StringBuilder(value.length)
        val offsets = ArrayList<Int>(value.length)
        value.forEachIndexed { index, character ->
            if (!character.isSearchWhitespace()) {
                text.append(character)
                offsets += index
            }
        }
        return NormalizedText(text.toString(), offsets.toIntArray())
    }

    private fun Char.isSearchWhitespace(): Boolean =
        isWhitespace() || this == '\u00A0' || this in '\u200B'..'\u200D' ||
            this == '\u2060' || this == '\uFEFF'

    private fun String.toSearchContext(): String =
        replace(Regex("[\\s\\u00A0\\u200B-\\u200D\\u2060\\uFEFF]+"), " ").trim()

    private suspend fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_RESOURCE_BYTES) { "EPUB resource exceeds the search safety limit" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private data class NormalizedText(
        val text: String,
        val sourceOffsets: IntArray
    )
}
