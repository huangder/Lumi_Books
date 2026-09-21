package com.davemorrissey.labs.subscaleview.internal

import android.graphics.PointF
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import com.davemorrissey.labs.subscaleview.AnimationBuilder
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlin.math.abs

internal class GestureListener(
	private val view: SubsamplingScaleImageView,
) : BridgeGestureListener() {

	private val interpolator = DecelerateInterpolator()

	override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
		val vTranslate = view.vTranslate
		if (view.isPanEnabled && view.isReadySent && vTranslate != null && e1 != null && e2 != null && (
			abs(e1.x - e2.x) > 50 || abs(e1.y - e2.y) > 50
			) && (abs(velocityX) > 500 || abs(velocityY) > 500) && !view.isZooming
		) {
			val vTranslateEnd =
				PointF(vTranslate.x + velocityX * 0.25f, vTranslate.y + velocityY * 0.25f)
			val sCenterXEnd = (view.width / 2f - vTranslateEnd.x) / view.scale
			val sCenterYEnd = (view.height / 2f - vTranslateEnd.y) / view.scale
			AnimationBuilder(
				view,
				sCenter = PointF(sCenterXEnd, sCenterYEnd),
			).withInterpolator(interpolator)
				.withPanLimited(false)
				.withOrigin(SubsamplingScaleImageView.ORIGIN_FLING).start()
			return true
		}
		return super.onFling(e1, e2, velocityX, velocityY)
	}

	override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
		view.performClick()
		return true
	}

	override fun onDoubleTap(e: MotionEvent): Boolean {
		val vTranslate = view.vTranslate
		if (view.isZoomEnabled && view.isReadySent && vTranslate != null) {
			// Hacky solution for #15 - after a double tap the GestureDetector gets in a state
			// where the next fling is ignored, so here we replace it with a new one.
			view.setGestureDetector(view.context)
			return if (view.isQuickScaleEnabled) {
				// Store quick scale params. This will become either a double tap zoom or a
				// quick scale depending on whether the user swipes.
				view.vCenterStart = PointF(e.x, e.y)
				view.vTranslateStart = vTranslate.copy()
				view.scaleStart = view.scale
				view.isQuickScaling = true
				view.isZooming = true
				view.quickScaleLastDistance = -1f
				view.quickScaleSCenter = view.viewToSourceCoord(view.vCenterStart!!)
				view.quickScaleVStart = PointF(e.x, e.y)
				view.quickScaleVLastPoint = view.quickScaleSCenter!!.copy()
				view.quickScaleMoved = false
				// We need to get events in onTouchEvent after this.
				false
			} else {
				// Start double tap zoom animation.
				view.doubleTapZoom(checkNotNull(view.viewToSourceCoord(PointF(e.x, e.y))), PointF(e.x, e.y))
				true
			}
		}
		return super.onDoubleTapEvent(e)
	}
}
