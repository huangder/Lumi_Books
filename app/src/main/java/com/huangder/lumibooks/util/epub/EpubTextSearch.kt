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

    suspend fun search(
        filePath: String,
        epubPackage: EpubPackage,
        query: String,
        maxResults: Int = 200
    ): List<EpubSearchMatch> = withContext(Dispatchers.IO) {
        ZipFile(filePath).use { zipFile ->
            val entriesByLowercase = zipFile.entries().asSequence()
                .associateBy { it.name.lowercase() }
            BookTextSearch.collect(
                chapterCount = epubPackage.spine.size,
                query = query,
                maxResults = maxResults,
                chapterText = { chapterIndex ->
                    readChapterText(zipFile, entriesByLowercase, epubPackage, chapterIndex)
                },
                chapterHref = { chapterIndex ->
                    epubPackage.spine.getOrNull(chapterIndex)?.manifestItem?.fullPath.orEmpty()
                }
            )
        }
    }

    private suspend fun readChapterText(
        zipFile: ZipFile,
        entriesByLowercase: Map<String, java.util.zip.ZipEntry>,
        epubPackage: EpubPackage,
        chapterIndex: Int
    ): String? {
        val spineItem = epubPackage.spine.getOrNull(chapterIndex) ?: return null
        val path = spineItem.manifestItem.fullPath
        val entry = zipFile.getEntry(path) ?: entriesByLowercase[path.lowercase()] ?: return null
        if (entry.size > MAX_RESOURCE_BYTES) return null
        val resource = try {
            EpubResource(
                path = path,
                mediaType = spineItem.manifestItem.mediaType,
                bytes = zipFile.getInputStream(entry).use { input -> readBounded(input) }
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }
        return try {
            EpubDocumentTransformer.extractSearchText(resource)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

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
