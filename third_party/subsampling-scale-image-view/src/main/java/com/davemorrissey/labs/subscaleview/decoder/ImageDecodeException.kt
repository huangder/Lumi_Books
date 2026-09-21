package com.davemorrissey.labs.subscaleview.decoder

import android.content.Context
import android.net.Uri
import org.jetbrains.annotations.Blocking
import java.io.IOException

public class ImageDecodeException @JvmOverloads public constructor(
	public val uri: String?,
	public val format: String?,
	message: String = MESSAGE.format(format),
) : IOException(message) {

	internal companion object {

		@Blocking
		fun create(context: Context?, uri: Uri?): ImageDecodeException = runCatching {
			val format = if (uri != null && context != null) {
				detectImageFormat(context, uri)
			} else {
				null
			}
			ImageDecodeException(uri?.toString(), format)
		}.getOrElse { e ->
			ImageDecodeException(uri?.toString(), null).apply { addSuppressed(e) }
		}

		private const val MESSAGE = "Cannot decode image - format %s may not be supported"
	}
}
