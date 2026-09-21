package com.davemorrissey.labs.subscaleview

/**
 * Default implementation of {@link OnAnimationEventListener} for extension. This does nothing in any method.
 */
public interface DefaultOnAnimationEventListener : OnAnimationEventListener {

	/**
	 * @inherit
	 */
	public override fun onComplete(): Unit = Unit

	/**
	 * @inherit
	 */
	public override fun onInterruptedByUser(): Unit = Unit

	/**
	 * @inherit
	 */
	public override fun onInterruptedByNewAnim(): Unit = Unit
}
