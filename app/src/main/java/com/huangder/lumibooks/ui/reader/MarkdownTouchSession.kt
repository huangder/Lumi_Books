package com.huangder.lumibooks.ui.reader

import android.view.MotionEvent
import kotlin.math.abs

/** Observe before RecyclerView intercepts the child or stops a fling on DOWN. */
internal class MarkdownTouchSession(private val touchSlop: Int, private val longPressTimeout: Int) {
    private var downTime = -1L
    private var downX = 0f
    private var downY = 0f
    private var upTime: Long? = null
    private var moved = false
    private var cancelled = false
    private var startedDuringScroll = false
    var selectionAllowed = false
    var menuAllowed = true

    fun record(event: MotionEvent, scrolling: Boolean) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = event.downTime
                downX = event.rawX; downY = event.rawY
                moved = false; cancelled = false; upTime = null
                startedDuringScroll = scrolling
                selectionAllowed = false; menuAllowed = true
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                if (event.downTime != downTime) return
                if (event.actionMasked == MotionEvent.ACTION_UP) upTime = event.eventTime
                for (i in 0 until event.historySize) {
                    if (abs(event.getHistoricalX(i) - event.x + event.rawX - downX) > touchSlop ||
                        abs(event.getHistoricalY(i) - event.y + event.rawY - downY) > touchSlop) moved = true
                }
                if (abs(event.rawX - downX) > touchSlop || abs(event.rawY - downY) > touchSlop) moved = true
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> cancelled = true
        }
    }

    fun onScrollStarted() { moved = true }

    fun isTap(event: MotionEvent): Boolean = downTime >= 0 && event.actionMasked == MotionEvent.ACTION_UP &&
        event.downTime == downTime && !moved && !cancelled && !startedDuringScroll &&
        event.eventTime - downTime < longPressTimeout

    fun isSelectionCandidate(expectedDownTime: Long = downTime): Boolean = downTime >= 0 &&
        expectedDownTime == downTime && selectionAllowed && !moved && !cancelled &&
        !startedDuringScroll

    fun canStartSelection(now: Long, expectedDownTime: Long = downTime): Boolean =
        isSelectionCandidate(expectedDownTime) && (upTime ?: now) - downTime >= longPressTimeout
}
