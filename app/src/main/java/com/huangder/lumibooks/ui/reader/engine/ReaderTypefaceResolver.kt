package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import com.huangder.lumibooks.R
import com.huangder.lumibooks.util.DownloadedFonts
import java.io.File

/** Typeface and weight used by every reader renderer. */
internal data class ResolvedReaderTypeface(
    val typeface: Typeface,
    val fakeBold: Boolean
)

internal fun resolveReaderTypeface(
    context: Context,
    fontType: String,
    customFontPath: String?,
    weight: Int
): ResolvedReaderTypeface {
    val normalizedWeight = weight.coerceIn(100, 900)
    val isCustomFamily = fontType == "fangsong" || fontType == "kaiti" ||
        fontType.startsWith("custom")
    val base = when {
        fontType == "serif" -> Typeface.SERIF
        fontType == "sans_serif" -> Typeface.SANS_SERIF
        fontType == "monospace" -> Typeface.MONOSPACE
        fontType == "fangsong" -> DownloadedFonts.typeface(context, "fangsong")
            ?: Typeface.DEFAULT
        fontType == "kaiti" -> runCatching {
            ResourcesCompat.getFont(context, R.font.lxgw_wenkai)
        }.getOrNull() ?: Typeface.DEFAULT
        fontType.startsWith("custom") -> customFontPath
            ?.let { path -> runCatching { Typeface.createFromFile(File(path)) }.getOrNull() }
            ?: Typeface.DEFAULT
        else -> Typeface.DEFAULT
    }

    // Android's weight factory is reliable for platform families, but can lose
    // the family metadata of file-backed fonts and make them fall back.
    val typeface = if (!isCustomFamily && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(base, normalizedWeight, false)
    } else {
        base
    }
    val fakeBold = isCustomFamily && normalizedWeight >= 600
    return ResolvedReaderTypeface(typeface, fakeBold)
}
