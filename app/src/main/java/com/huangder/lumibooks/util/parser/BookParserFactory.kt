package com.huangder.lumibooks.util.parser

import android.content.Context
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.util.diagnostics.DiagnosticLevel
import com.huangder.lumibooks.util.diagnostics.DiagnosticLoggerRegistry

object BookParserFactory {
    fun createParser(format: BookFormat, context: Context): BookParser {
        DiagnosticLoggerRegistry.logger?.log(
            category = "import",
            event = "parser_created",
            level = DiagnosticLevel.DEBUG,
            bookFormat = format.name
        )
        return when (format) {
            BookFormat.EPUB -> EpubParser(context)
            BookFormat.PDF -> PdfParser(context)
            BookFormat.TXT -> TxtParser(context)
            BookFormat.MOBI -> MobiParser(context)
        }
    }
}
