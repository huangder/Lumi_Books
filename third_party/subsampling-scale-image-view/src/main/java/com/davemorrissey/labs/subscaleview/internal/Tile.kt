package com.davemorrissey.labs.subscaleview.internal

import android.graphics.Bitmap
import android.graphics.Rect

internal class Tile {
	@JvmField
	var sampleSize = 0
	var bitmap: Bitmap? = null
		set(value) {
			field?.recycle()
			field = value
			isValid = true
		}

	@JvmField
	var isLoading = false

	@JvmField
	var isVisible = false

	@JvmField
	var isValid = false

	// Volatile fields instantiated once then updated before use to reduce GC.
	@JvmField
	var sRect: Rect = Rect()

	@JvmField
	val vRect = Rect()

	@JvmField
	val fileSRect = Rect()

	fun recycle() {
		isVisible = false
		bitmap = null
	}
}
