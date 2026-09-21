package com.davemorrissey.labs.subscaleview.decoder

import android.graphics.Bitmap
import java.lang.reflect.InvocationTargetException

/**
 * Compatibility factory to instantiate decoders with empty public constructors.
 * @param <T> The base type of the decoder this factory will produce.
</T> */
@Deprecated("This class uses reflection. Avoid using it")
public class CompatDecoderFactory<T> @JvmOverloads constructor(
	private val clazz: Class<out T>,
	override val bitmapConfig: Bitmap.Config? = null,
) : DecoderFactory<T> {

	@Throws(
		IllegalAccessException::class,
		InstantiationException::class,
		NoSuchMethodException::class,
		InvocationTargetException::class,
	)
	override fun make(): T {
		return if (bitmapConfig == null) {
			clazz.getDeclaredConstructor().newInstance()
		} else {
			clazz.getConstructor(Bitmap.Config::class.java).newInstance(bitmapConfig)
		}
	}
}
