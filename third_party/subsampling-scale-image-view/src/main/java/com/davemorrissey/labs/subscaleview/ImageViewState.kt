package com.davemorrissey.labs.subscaleview

import android.graphics.PointF
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
public class ImageViewState(
	public val scale: Float,
	public val centerX: Float,
	public val centerY: Float,
	public val orientation: Int,
) : Parcelable {

	public val center: PointF
		get() = PointF(centerX, centerY)
}
