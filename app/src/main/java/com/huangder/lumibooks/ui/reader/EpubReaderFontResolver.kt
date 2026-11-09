package com.huangder.lumibooks.ui.reader

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.CustomFontPreset
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.highlight.RuleStyleJson
import com.huangder.lumibooks.util.DownloadedFonts
import com.huangder.lumibooks.util.epub.BookRenderSession
import com.huangder.lumibooks.ui.reader.engine.readerFileFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal data class PreparedEpubReaderFont(
    val fontType: String,
    val customFontPath: String?,
    val resolvedPath: String?
)

/** Static faces must stay regular so Chromium can synthesize a requested bold weight. */
internal fun epubReaderFontWeightDescriptor(path: String?): String {
    val range = path?.let { readerFileFont(File(it)).weightRange } ?: return "400"
    return "${range.start} ${range.endInclusive}"
}

/** Rule fonts must also be available when the body keeps the publisher's font. */
@Composable
internal fun rememberEpubRuleFontUrls(
    session: BookRenderSession,
    notes: List<Note>,
    customFonts: List<CustomFontPreset>
): Map<String, String> {
    val context = LocalContext.current
    val fontTypes = remember(notes) {
        notes.asSequence().filterNot { it.isStale }
            .mapNotNull { RuleStyleJson.decode(it.styleSnapshotJson)?.fontType }
            .filter { it.isNotBlank() }.toSet()
    }
    val urls by produceState(emptyMap<String, String>(), session, fontTypes, customFonts) {
        val paths = fontTypes.mapNotNull { type ->
            prepareEpubReaderFontPath(context, type, customFonts.firstOrNull { it.fontTypeKey == type }?.path)
                ?.let { type to it }
        }
        value = paths.mapNotNull { (type, path) -> session.readerFontUrl(path)?.let { type to it } }.toMap()
    }
    return urls
}

internal suspend fun prepareEpubReaderFontPath(
    context: Context,
    fontType: String,
    customFontPath: String?
): String? = withContext(Dispatchers.IO) {
    when {
        fontType == "serif" -> {
            copyBundledReaderFont(context, R.font.source_serif4_italic, "source_serif4_italic_v1.ttf")
            copyBundledReaderFont(context, R.font.source_serif4_regular, "source_serif4_regular_v1.ttf")
        }
        fontType.startsWith("custom") -> customFontPath
            ?.let(::File)
            ?.takeIf { it.isFile && it.length() > 0L }
            ?.canonicalPath
        fontType == "fangsong" -> DownloadedFonts.file(context, "fangsong")
            ?.let { copyReaderFontFile(context, it, "fandol_fang_v1.ttf") }
        fontType == "kaiti" -> copyBundledReaderFont(context, R.font.lxgw_wenkai, "lxgw_wenkai_v1.ttf")
        else -> null
    }
}

// Body and rule font preparation can run concurrently against the same cache file.
@Synchronized
private fun copyBundledReaderFont(context: Context, resourceId: Int, fileName: String): String? {
    val directory = File(context.cacheDir, "epub_reader_fonts").apply { mkdirs() }
    val target = File(directory, fileName)
    if (target.isFile && target.length() > 0L) return target.canonicalPath

    val temporary = File(directory, "$fileName.tmp")
    return runCatching {
        context.resources.openRawResource(resourceId).use { input ->
            temporary.outputStream().buffered().use { output -> input.copyTo(output) }
        }
        if (target.exists()) target.delete()
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        target.takeIf { it.isFile && it.length() > 0L }?.canonicalPath
    }.getOrNull().also {
        if (temporary.exists()) temporary.delete()
    }
}

/** 将已下载的字体源文件原子写入 EPUB 渲染缓存目录（.tmp + rename）。 */
@Synchronized
private fun copyReaderFontFile(context: Context, source: File, fileName: String): String? {
    val directory = File(context.cacheDir, "epub_reader_fonts").apply { mkdirs() }
    val target = File(directory, fileName)
    if (target.isFile && target.length() > 0L) return target.canonicalPath

    val temporary = File(directory, "$fileName.tmp")
    return runCatching {
        source.inputStream().use { input ->
            temporary.outputStream().buffered().use { output -> input.copyTo(output) }
        }
        if (target.exists()) target.delete()
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        target.takeIf { it.isFile && it.length() > 0L }?.canonicalPath
    }.getOrNull().also {
        if (temporary.exists()) temporary.delete()
    }
}
