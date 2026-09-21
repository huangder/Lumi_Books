package com.davemorrissey.labs.subscaleview

import android.graphics.PointF

/**
 * Default implementation of {@link OnStateChangedListener}. This does nothing in any method.
 */
public interface DefaultOnStateChangedListener : OnStateChangedListener {

	/**
	 * @inherit
	 */
	@Deprecated(
		"Use onScaleChanged with view parameter instead",
		replaceWith = ReplaceWith("onScaleChanged(view, newCenter, origin)"),
	)
	override fun onScaleChanged(newScale: Float, origin: Int): Unit = Unit

	/**
	 * @inherit
	 */
	@Deprecated(
		"Use onCenterChanged with view parameter instead",
		replaceWith = ReplaceWith("onCenterChanged(view, newCenter, origin)"),
	)
	override fun onCenterChanged(newCenter: PointF, origin: Int): Unit = Unit

	/**
	 * @inherit
	 */
	override fun onScaleChanged(view: SubsamplingScaleImageView, newScale: Float, origin: Int): Unit = Unit

	/**
	 * @inherit
	 */
	override fun onCenterChanged(view: SubsamplingScaleImageView, newCenter: PointF, origin: Int): Unit = Unit
}
