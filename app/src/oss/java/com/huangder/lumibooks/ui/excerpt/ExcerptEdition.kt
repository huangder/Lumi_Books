package com.huangder.lumibooks.ui.excerpt

import android.content.Context

internal object ExcerptEdition : ExcerptTemplate {
    val styles = ExcerptStyle.entries.take(6)
    fun normalize(selection: ExcerptSelection): ExcerptSelection = ExcerptSelection(
        style = selection.style.takeIf { it in styles } ?: ExcerptStyle.READER
    )
    override fun prepare(context: Context, request: ExcerptRequest, style: ExcerptStyle, options: ExcerptRenderOptions): ExcerptDocument? =
        if (style !in styles || options != ExcerptRenderOptions())
            ExcerptRenderer.prepare(context, request, style.takeIf { it in styles } ?: ExcerptStyle.READER) else null
}
