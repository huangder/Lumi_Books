package com.davemorrissey.labs.subscaleview.decoder

import android.graphics.Bitmap

/**
 * Interface for [ImageDecoder] and [ImageRegionDecoder] factories.
 * @param <T> the class of decoder that will be produced.
</T> */
public fun interface DecoderFactory<T> {

	public val bitmapConfig: Bitmap.Config?
		get() = null

	/**
	 * Produce a new instance of a decoder with type [T].
	 * @return a new instance of your decoder.
	 */
	public fun make(): T
}
