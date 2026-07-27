package com.huangder.lumibooks.ui.reader

import android.content.Context
import com.huangder.lumibooks.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal suspend fun prepareEpubReaderFontPath(
    context: Context,
    fontType: String,
    customFontPath: String?
): String? = withContext(Dispatchers.IO) {
    when {
        fontType.startsWith("custom") -> customFontPath
            ?.let(::File)
            ?.takeIf { it.isFile && it.length() > 0L }
            ?.canonicalPath
        fontType == "fangsong" -> copyBundledReaderFont(context, R.font.fandol_fang, "fandol_fang_v1.ttf")
        fontType == "kaiti" -> copyBundledReaderFont(context, R.font.lxgw_wenkai, "lxgw_wenkai_v1.ttf")
        else -> null
    }
}

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
