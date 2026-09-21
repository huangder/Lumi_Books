package com.davemorrissey.labs.subscaleview.internal

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlin.math.abs

private const val MESSAGE_LONG_CLICK = 1

internal class TouchEventDelegate(
	private val view: SubsamplingScaleImageView,
) : Handler.Callback {

	private val density = view.context.resources.displayMetrics.density
	private val quickScaleThreshold: Float =
		TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, view.context.resources.displayMetrics)

	// Max touches used in current gesture
	private var maxTouchCount: Int = 0

	// Long click listener
	var onLongClickListener: View.OnLongClickListener? = null

	// Long click handler
	private val handler = Handler(Looper.getMainLooper(), this)

	override fun handleMessage(msg: Message): Boolean {
		if (msg.what == MESSAGE_LONG_CLICK &&
			onLongClickListener != null && view.isLongClickable
		) {
			maxTouchCount = 0
			view.setOnLongClickListener(onLongClickListener)
			view.performLongClick()
			view.setOnLongClickListener(null)
		}
		return true
	}

	fun dispatchTouchEvent(event: MotionEvent): Boolean {
		val touchCount = event.pointerCount
		val width = view.width
		val height = view.height
		var scale = view.scale
		val vTranslate = view.vTranslate ?: return false
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
				view.anim = null
				view.requestDisallowInterceptTouchEvent(true)
				maxTouchCount = maxTouchCount.coerceAtLeast(touchCount)
				if (touchCount >= 2) {
					if (view.isZoomEnabled) {
						// Start pinch to zoom. Calculate distance between touch points and center point of the pinch.
						val distance: Float = distance(
							event.getX(0),
							event.getX(1),
							event.getY(0),
							event.getY(1),
						)
						view.scaleStart = scale
						view.vDistStart = distance
						view.vTranslateStart!![vTranslate.x] = vTranslate.y
						view.vCenterStart!![(event.getX(0) + event.getX(1)) / 2] = (event.getY(0) + event.getY(1)) / 2
					} else {
						// Abort all gestures on second touch
						maxTouchCount = 0
					}
					// Cancel long click timer
					handler.removeMessages(MESSAGE_LONG_CLICK)
				} else if (!view.isQuickScaling) {
					// Start one-finger pan
					view.vTranslateStart!![vTranslate.x] = vTranslate.y
					view.vCenterStart!![event.x] = event.y

					// Start long click timer
					handler.sendEmptyMessageDelayed(MESSAGE_LONG_CLICK, 600)
				}
				return true
			}
			MotionEvent.ACTION_MOVE -> {
				var consumed = false
				if (maxTouchCount > 0) {
					if (touchCount >= 2) {
						// Calculate new distance between touch points, to scale and pan relative to start values.
						val vDistEnd: Float = distance(
							event.getX(0),
							event.getX(1),
							event.getY(0),
							event.getY(1),
						)
						val vCenterEndX = (event.getX(0) + event.getX(1)) / 2
						val vCenterEndY = (event.getY(0) + event.getY(1)) / 2
						if (view.isZoomEnabled && (
							distance(
									view.vCenterStart!!.x,
									vCenterEndX,
									view.vCenterStart!!.y,
									vCenterEndY,
								) > 5 || abs(vDistEnd - view.vDistStart) > 5 || view.isPanning
							)
						) {
							view.isZooming = true
							view.isPanning = true
							consumed = true
							val previousScale = scale.toDouble()
							scale = (vDistEnd / view.vDistStart * view.scaleStart).coerceAtMost(view.maxScale)
							view.scale = scale
							if (scale <= view.minScale()) {
								// Minimum scale reached so don't pan. Adjust start settings so any expand will zoom in.
								view.vDistStart = vDistEnd
								view.scaleStart = view.minScale()
								view.vCenterStart!![vCenterEndX] = vCenterEndY
								view.vTranslateStart!!.set(vTranslate)
							} else if (view.isPanEnabled) {
								// Translate to place the source image coordinate that was at the center of the pinch at the start
								// at the center of the pinch now, to give simultaneous pan + zoom.
								val vLeftStart = view.vCenterStart!!.x - view.vTranslateStart!!.x
								val vTopStart = view.vCenterStart!!.y - view.vTranslateStart!!.y
								val vLeftNow = vLeftStart * (scale / view.scaleStart)
								val vTopNow = vTopStart * (scale / view.scaleStart)
								vTranslate.x = vCenterEndX - vLeftNow
								vTranslate.y = vCenterEndY - vTopNow
								if ((
									previousScale * view.sHeight() < view.height &&
										scale * view.sHeight() >= view.height || previousScale * view.sWidth() < width
									) &&
									scale * view.sWidth() >= width
								) {
									view.fitToBounds(true)
									view.vCenterStart!![vCenterEndX] = vCenterEndY
									view.vTranslateStart!!.set(vTranslate)
									view.scaleStart = scale
									view.vDistStart = vDistEnd
								}
							} else if (view.sRequestedCenter != null) {
								// With a center specified from code, zoom around that point.
								vTranslate.x = width / 2f - scale * view.sRequestedCenter!!.x
								vTranslate.y = height / 2f - scale * view.sRequestedCenter!!.y
							} else {
								// With no requested center, scale around the image center.
								vTranslate.x = width / 2f - scale * (view.sWidth() / 2f)
								vTranslate.y = height / 2f - scale * (view.sHeight() / 2f)
							}
							view.fitToBounds(true)
							view.refreshRequiredTiles(view.isEagerLoadingEnabled)
						}
					} else if (view.isQuickScaling) {
						// One finger zoom
						// Stole Google's Magical Formula™ to make sure it feels the exact same
						var dist = abs(view.quickScaleVStart!!.y - event.y) * 2 + quickScaleThreshold
						if (view.quickScaleLastDistance == -1f) {
							view.quickScaleLastDistance = dist
						}
						val isUpwards = event.y > view.quickScaleVLastPoint!!.y
						view.quickScaleVLastPoint!![0f] = event.y
						val spanDiff = abs(1 - dist / view.quickScaleLastDistance) * 0.5f
						if (spanDiff > 0.03f || view.quickScaleMoved) {
							view.quickScaleMoved = true
							var multiplier = 1f
							if (view.quickScaleLastDistance > 0) {
								multiplier = if (isUpwards) 1 + spanDiff else 1 - spanDiff
							}
							val previousScale = scale.toDouble()
							scale = (scale * multiplier)
								.coerceAtLeast(view.minScale())
								.coerceAtMost(view.maxScale)
							view.scale = scale
							if (view.isPanEnabled) {
								val vLeftStart = view.vCenterStart!!.x - view.vTranslateStart!!.x
								val vTopStart = view.vCenterStart!!.y - view.vTranslateStart!!.y
								val vLeftNow = vLeftStart * (scale / view.scaleStart)
								val vTopNow = vTopStart * (scale / view.scaleStart)
								vTranslate.x = view.vCenterStart!!.x - vLeftNow
								vTranslate.y = view.vCenterStart!!.y - vTopNow
								if (previousScale * view.sHeight() < height && scale * view.sHeight() >= height ||
									previousScale * view.sWidth() < width && scale * view.sWidth() >= width
								) {
									view.fitToBounds(true)
									view.vCenterStart!!.set(view.sourceToViewCoord(view.quickScaleSCenter!!)!!)
									view.vTranslateStart!!.set(vTranslate)
									view.scaleStart = scale
									dist = 0f
								}
							} else if (view.sRequestedCenter != null) {
								// With a center specified from code, zoom around that point.
								vTranslate.x = width / 2f - scale * view.sRequestedCenter!!.x
								vTranslate.y = height / 2f - scale * view.sRequestedCenter!!.y
							} else {
								// With no requested center, scale around the image center.
								vTranslate.x = width / 2f - scale * (view.sWidth() / 2f)
								vTranslate.y = height / 2f - scale * (view.sHeight() / 2f)
							}
						}
						view.quickScaleLastDistance = dist
						view.fitToBounds(true)
						view.refreshRequiredTiles(view.isEagerLoadingEnabled)
						consumed = true
					} else if (!view.isZooming) {
						// One finger pan - translate the image. We do this calculation even with pan disabled so click
						// and long click behaviour is preserved.
						val dx = abs(event.x - view.vCenterStart!!.x)
						val dy = abs(event.y - view.vCenterStart!!.y)

						// On the Samsung S6 long click event does not work, because the dx > 5 usually true
						val offset = density * 5
						if (dx > offset || dy > offset || view.isPanning) {
							consumed = true
							vTranslate.x = view.vTranslateStart!!.x + (event.x - view.vCenterStart!!.x)
							vTranslate.y = view.vTranslateStart!!.y + (event.y - view.vCenterStart!!.y)
							val lastX = vTranslate.x
							val lastY = vTranslate.y
							view.fitToBounds(true)
							val atXEdge = lastX != vTranslate.x
							val atYEdge = lastY != vTranslate.y
							val edgeXSwipe = atXEdge && dx > dy && !view.isPanning
							val edgeYSwipe = atYEdge && dy > dx && !view.isPanning
							val yPan = lastY == vTranslate.y && dy > offset * 3
							if (!edgeXSwipe && !edgeYSwipe && (!atXEdge || !atYEdge || yPan || view.isPanning)) {
								view.isPanning = true
							} else if (dx > offset || dy > offset) {
								// Haven't panned the image, and we're at the left or right edge. Switch to page swipe.
								maxTouchCount = 0
								handler.removeMessages(MESSAGE_LONG_CLICK)
								view.requestDisallowInterceptTouchEvent(false)
							}
							if (!view.isPanEnabled) {
								vTranslate.x = view.vTranslateStart!!.x
								vTranslate.y = view.vTranslateStart!!.y
								view.requestDisallowInterceptTouchEvent(false)
							}
							view.refreshRequiredTiles(view.isEagerLoadingEnabled)
						}
					}
				}
				if (consumed) {
					handler.removeMessages(MESSAGE_LONG_CLICK)
					view.invalidate()
					return true
				}
			}
			MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
				handler.removeMessages(MESSAGE_LONG_CLICK)
				if (view.isQuickScaling) {
					view.isQuickScaling = false
					if (!view.quickScaleMoved) {
						view.doubleTapZoom(view.quickScaleSCenter!!, view.vCenterStart!!)
					}
				}
				if (maxTouchCount > 0 && (view.isZooming || view.isPanning)) {
					if (view.isZooming && touchCount == 2) {
						// Convert from zoom to pan with remaining touch
						view.isPanning = true
						view.vTranslateStart!![vTranslate.x] = vTranslate.y
						if (event.actionIndex == 1) {
							view.vCenterStart!![event.getX(0)] = event.getY(0)
						} else {
							view.vCenterStart!![event.getX(1)] = event.getY(1)
						}
					}
					if (touchCount < 3) {
						// End zooming when only one touch point
						view.isZooming = false
					}
					if (touchCount < 2) {
						// End panning when no touch points
						view.isPanning = false
						maxTouchCount = 0
					}
					// Trigger load of tiles now required
					view.refreshRequiredTiles(true)
					return true
				}
				if (touchCount == 1) {
					view.isZooming = false
					view.isPanning = false
					maxTouchCount = 0
				}
				return true
			}
			MotionEvent.ACTION_CANCEL -> {
				handler.removeMessages(MESSAGE_LONG_CLICK)
				view.isQuickScaling = false
				view.isZooming = false
				view.isPanning = false
				maxTouchCount = 0
				return true
			}
		}
		return false
	}

	fun reset() {
		maxTouchCount = 0
	}
}
