package com.huangder.lumibooks.ui.reader

internal enum class EpubCurlTurnDisposition { ACCEPT, QUEUE, PASS_BOUNDARY }

/**
 * 卷曲模式下等待"下一页准备好"时，迟迟等不到目标就退化为直接翻页。
 *
 * 卷曲动画需要预先准备好目标页快照；一旦预加载链路失败（文档没跑起阅读器脚本、
 * 加载被打断等），排队中的翻页会一直等下去，用户看到的就是"翻不了页"。
 */
internal fun shouldFallBackToDirectCurlTurn(
    turnPending: Boolean,
    idle: Boolean,
    waitingForTarget: Boolean,
    targetReady: Boolean,
    potentialTurn: Boolean
): Boolean = turnPending && idle && !waitingForTarget && !targetReady && potentialTurn

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
