package com.huangder.lumibooks.ui.reader

internal enum class EpubCurlTurnDisposition { ACCEPT, DROP, PASS_BOUNDARY }

/** Fixed-layout Curl never queues input received while its visual handoff is busy. */
internal fun epubCurlTurnDisposition(
    idle: Boolean,
    targetExists: Boolean,
    targetReady: Boolean
): EpubCurlTurnDisposition = when {
    !idle -> EpubCurlTurnDisposition.DROP
    !targetExists -> EpubCurlTurnDisposition.PASS_BOUNDARY
    !targetReady -> EpubCurlTurnDisposition.DROP
    else -> EpubCurlTurnDisposition.ACCEPT
}
