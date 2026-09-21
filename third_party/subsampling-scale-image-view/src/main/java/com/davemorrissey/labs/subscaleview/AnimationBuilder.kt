package com.davemorrissey.labs.subscaleview

import android.graphics.PointF
import android.provider.Settings
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import androidx.annotation.ReturnThis
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.Companion.EASE_IN_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.Companion.EASE_OUT_QUAD
import com.davemorrissey.labs.subscaleview.internal.Anim
import com.davemorrissey.labs.subscaleview.internal.ScaleAndTranslate
import kotlin.math.roundToLong

/**
 * Builder class used to set additional options for a scale animation. Create an instance using [.animateScale],
 * then set your options and call [.start].
 */
public class AnimationBuilder internal constructor(
	private val view: SubsamplingScaleImageView,
	scale: Float = view.scale,
	sCenter: PointF = checkNotNull(view.getCenter()),
	private val vFocus: PointF? = null,
) {

	private val durationScale = Settings.Global.getFloat(
		view.context.contentResolver,
		Settings.Global.ANIMATOR_DURATION_SCALE,
		1f,
	)
	private val targetScale: Float = scale
	private val targetSCenter: PointF = sCenter
	private var duration: Long = view.resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
	private var interpolator: Interpolator = AccelerateDecelerateInterpolator()
	private var origin = SubsamplingScaleImageView.ORIGIN_ANIM
	private var isInterruptible = true
	private var isPanLimited = true
	private var listener: OnAnimationEventListener? = null

	/**
	 * Desired duration of the anim in milliseconds. Default is 500.
	 * @param duration duration in milliseconds.
	 * @return this builder for method chaining.
	 */
	@ReturnThis
	public fun withDuration(duration: Long): AnimationBuilder {
		this.duration = duration
		return this
	}

	/**
	 * Whether the animation can be interrupted with a touch. Default is true.
	 * @param interruptible interruptible flag.
	 * @return this builder for method chaining.
	 */
	@ReturnThis
	public fun withInterruptible(interruptible: Boolean): AnimationBuilder {
		this.isInterruptible = interruptible
		return this
	}

	/**
	 * Set the easing style. See static fields. [.EASE_IN_OUT_QUAD] is recommended, and the default.
	 * @param easing easing style.
	 * @return this builder for method chaining.
	 */
	@ReturnThis
	@Deprecated("Use withInterpolator instead")
	public fun withEasing(easing: Int): AnimationBuilder {
		this.interpolator = when (easing) {
			EASE_IN_OUT_QUAD -> AccelerateDecelerateInterpolator()
			EASE_OUT_QUAD -> DecelerateInterpolator()
			else -> throw IllegalArgumentException("Unknown easing type: $easing")
		}
		return this
	}

	/**
	 * Set the animation interpolator.
	 * @param interpolator interpolator for animation.
	 * @return this builder for method chaining.
	 */
	@ReturnThis
	public fun withInterpolator(interpolator: Interpolator): AnimationBuilder {
		this.interpolator = interpolator
		return this
	}

	/**
	 * Add an animation event listener.
	 * @param listener The listener.
	 * @return this builder for method chaining.
	 */
	@ReturnThis
	public fun withOnAnimationEventListener(listener: OnAnimationEventListener?): AnimationBuilder {
		this.listener = listener
		return this
	}

	/**
	 * Only for internal use. When set to true, the animation proceeds towards the actual end point - the nearest
	 * point to the center allowed by pan limits. When false, animation is in the direction of the requested end
	 * point and is stopped when the limit for each axis is reached. The latter behaviour is used for flings but
	 * nothing else.
	 */
	@ReturnThis
	public fun withPanLimited(panLimited: Boolean): AnimationBuilder {
		this.isPanLimited = panLimited
		return this
	}

	/**
	 * Only for internal use. Indicates what caused the animation.
	 */
	@ReturnThis
	public fun withOrigin(origin: Int): AnimationBuilder {
		this.origin = origin
		return this
	}

	/**
	 * Starts the animation.
	 */
	public fun start() {
		try {
			view.anim?.listener?.onInterruptedByNewAnim()
		} catch (e: Exception) {
			Log.w(SubsamplingScaleImageView.TAG, "Error thrown by animation listener", e)
		}
		val vxCenter = view.paddingLeft + (view.width - view.paddingRight - view.paddingLeft) / 2f
		val vyCenter = view.paddingTop + (view.height - view.paddingBottom - view.paddingTop) / 2f
		val targetScale: Float = view.limitedScale(targetScale)
		val targetSCenter = if (isPanLimited) view.limitedSCenter(
			this.targetSCenter.x,
			this.targetSCenter.y,
			targetScale,
			PointF(),
		) else targetSCenter
		if (durationScale <= 0f) { // animation is disabled system-wide
			view.setScaleAndCenter(targetScale, targetSCenter)
			return
		}
		val currentCenter = checkNotNull(view.getCenter())
		view.anim = Anim(
			scaleStart = view.scale,
			scaleEnd = targetScale,
			sCenterEndRequested = targetSCenter,
			sCenterStart = currentCenter,
			sCenterEnd = targetSCenter,
			vFocusStart = view.sourceToViewCoord(targetSCenter),
			duration = (duration * durationScale).roundToLong(),
			interruptible = isInterruptible,
			interpolator = interpolator,
			origin = origin,
			time = System.currentTimeMillis(),
			listener = listener,
			vFocusEnd = if (vFocus != null) {
				// Calculate where translation will be at the end of the anim
				val vTranslateXEnd: Float = vFocus.x - targetScale * currentCenter.x
				val vTranslateYEnd: Float = vFocus.y - targetScale * currentCenter.y
				val satEnd = ScaleAndTranslate(targetScale, PointF(vTranslateXEnd, vTranslateYEnd))
				// Fit the end translation into bounds
				view.fitToBounds(true, satEnd)
				// Adjust the position of the focus point at end so image will be in bounds
				PointF(
					vFocus.x + (satEnd.vTranslate.x - vTranslateXEnd),
					vFocus.y + (satEnd.vTranslate.y - vTranslateYEnd),
				)
			} else {
				PointF(vxCenter, vyCenter)
			},
		)
		view.invalidate()
	}
}
