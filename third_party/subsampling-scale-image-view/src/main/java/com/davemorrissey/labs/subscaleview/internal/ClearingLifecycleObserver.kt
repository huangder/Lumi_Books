package com.davemorrissey.labs.subscaleview.internal

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

internal class ClearingLifecycleObserver(
	private val ssiv: SubsamplingScaleImageView,
) : DefaultLifecycleObserver {

	override fun onDestroy(owner: LifecycleOwner) {
		super.onDestroy(owner)
		ssiv.cancelCoroutineScope()
		owner.lifecycle.removeObserver(this)
	}
}
