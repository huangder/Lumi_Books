package com.ebook.reader.util.parser

import android.content.Context
import com.ebook.reader.domain.model.BookFormat

object BookParserFactory {
    fun createParser(format: BookFormat, context: Context): BookParser {
        return when (format) {
            BookFormat.EPUB -> EpubParser(context)
            BookFormat.PDF -> PdfParser(context)
            BookFormat.TXT -> TxtParser()
        }
    }
}
