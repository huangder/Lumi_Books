package com.huangder.lumibooks.edition

/** Starts edition-specific widget observers once for the application lifetime. */
interface EditionWidgetRefresh {
    /** Implementations must tolerate repeated calls without adding duplicate observers. */
    fun start()
}
