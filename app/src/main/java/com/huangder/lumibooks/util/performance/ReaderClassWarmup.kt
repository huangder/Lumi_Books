package com.huangder.lumibooks.util.performance

import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The first book opened in a process used to verify the reader's largest classes on the main
 * thread (about 0.3 s on an emulator debug build), and since the reader now loads during the
 * hero, that would stall its frames. Initializing them early on a background thread moves the
 * verification there. Their static initializers only hold constants, colors and regexes.
 */
object ReaderClassWarmup {
    private val started = AtomicBoolean(false)

    // Names rather than class literals: Kotlin file facades have no literal. A name that R8
    // renamed is simply skipped.
    private val classNames = listOf(
        "com.huangder.lumibooks.ui.reader.ReaderViewModel",
        "com.huangder.lumibooks.ui.reader.ReaderScreenKt",
        "com.huangder.lumibooks.ui.reader.ThemeSettingsSheetKt",
        "com.huangder.lumibooks.ui.reader.HighlightRulesSheetKt",
        "com.huangder.lumibooks.ui.reader.LocalDictionarySheetKt",
        "com.huangder.lumibooks.ui.reader.engine.ReadView",
        "com.huangder.lumibooks.ui.reader.engine.PageContentView",
        "com.huangder.lumibooks.ui.reader.engine.ReaderTextPainter",
        "com.huangder.lumibooks.ui.reader.engine.PageLayoutEngine",
        "com.huangder.lumibooks.ui.reader.engine.PageSlotManager",
        "com.huangder.lumibooks.util.parser.EpubParser"
    )

    fun start(loader: ClassLoader) {
        if (!started.compareAndSet(false, true)) return
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            classNames.forEach { name ->
                runCatching { Class.forName(name, true, loader) }
            }
        }, "reader-class-warmup").start()
    }
}
