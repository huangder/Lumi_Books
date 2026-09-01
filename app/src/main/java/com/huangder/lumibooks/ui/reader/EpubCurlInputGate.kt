package com.huangder.lumibooks.ui.reader

internal enum class EpubCurlTurnDisposition { ACCEPT, QUEUE, PASS_BOUNDARY }

/** Curl queues input while animation, handoff, or target preparation is busy. */
internal fun epubCurlTurnDisposition(
    idle: Boolean,
    targetExists: Boolean,
    targetReady: Boolean
): EpubCurlTurnDisposition = when {
    !idle -> EpubCurlTurnDisposition.QUEUE
    !targetExists -> EpubCurlTurnDisposition.PASS_BOUNDARY
    !targetReady -> EpubCurlTurnDisposition.QUEUE
    else -> EpubCurlTurnDisposition.ACCEPT
}
