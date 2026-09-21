package com.huangder.lumibooks.service

import android.view.KeyEvent
import com.huangder.lumibooks.BuildConfig
import com.huangder.lumibooks.tts.TtsController
import com.huangder.lumibooks.util.diagnostics.DiagnosticLevel
import com.huangder.lumibooks.util.diagnostics.DiagnosticLoggerRegistry

internal fun logMediaButtonDelivery(
    source: String,
    event: KeyEvent,
    result: TtsMediaButtons.Result,
    controller: TtsController
) {
    if (!BuildConfig.DIAGNOSTIC_BUILD) return
    DiagnosticLoggerRegistry.logger?.log(
        category = "tts",
        event = "media_button_received",
        level = DiagnosticLevel.INFO,
        attributes = mapOf(
            "source" to source,
            "keyCode" to event.keyCode,
            "action" to event.action,
            "repeatCount" to event.repeatCount,
            "command" to (result.command?.name ?: "none"),
            "stateBefore" to result.stateBefore.name,
            "outcome" to result.outcome.name,
            "consumed" to result.consumed
        ),
        bookId = controller.activeBookId.value,
        result = result.outcome.name.lowercase()
    )
}

internal fun logTtsServiceEvent(
    event: String,
    controller: TtsController,
    attributes: Map<String, Any?> = emptyMap(),
    throwable: Throwable? = null,
    level: DiagnosticLevel = DiagnosticLevel.INFO,
    result: String? = null
) {
    if (!BuildConfig.DIAGNOSTIC_BUILD) return
    DiagnosticLoggerRegistry.logger?.log(
        category = "tts",
        event = event,
        level = level,
        attributes = attributes,
        throwable = throwable,
        bookId = controller.activeBookId.value,
        result = result
    )
}
