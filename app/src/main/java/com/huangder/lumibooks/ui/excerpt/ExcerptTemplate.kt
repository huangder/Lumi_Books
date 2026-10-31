package com.huangder.lumibooks.ui.excerpt

import android.content.Context

/** Edition templates may handle a request or leave it to the six shared styles. */
internal interface ExcerptTemplate {
    fun prepare(context: Context, request: ExcerptRequest, style: ExcerptStyle,
                options: ExcerptRenderOptions): ExcerptDocument?
}
