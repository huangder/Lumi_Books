package com.huangder.lumibooks.util.parser

import android.content.Context
import com.huangder.lumibooks.domain.model.BookFormat

object BookParserFactory {
    fun createParser(format: BookFormat, context: Context): BookParser {
        return when (format) {
            BookFormat.EPUB -> EpubParser(context)
            BookFormat.PDF -> PdfParser(context)
            BookFormat.TXT -> TxtParser()
        }
    }
}
