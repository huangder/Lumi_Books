package com.davemorrissey.labs.subscaleview.decoder

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.WorkerThread
import com.davemorrissey.labs.subscaleview.ImageSource
import org.jetbrains.annotations.Blocking

/**
 * Interface for image decoding classes, allowing the default [android.graphics.BitmapFactory]
 * based on the Skia library to be replaced with a custom class.
 */
public interface ImageDecoder {

	/**
	 * Decode an image. The URI can be in one of the following formats:
	 * <br></br>
	 * File: `file:///scard/picture.jpg`
	 * <br></br>
	 * Asset: `file:///android_asset/picture.png`
	 * <br></br>
	 * Resource: `android.resource://com.example.app/drawable/picture`
	 *
	 * @param context Application context
	 * @param uri URI of the image
	 * @return the decoded bitmap
	 * @throws Exception if decoding fails.
	 */
	@Throws(Exception::class)
	@WorkerThread
	@Blocking
	public fun decode(context: Context, uri: Uri, sampleSize: Int): Bitmap

	@Throws(Exception::class)
	@WorkerThread
	@Blocking
	public fun decode(context: Context, source: ImageSource, sampleSize: Int): Bitmap =
		decode(context, source.toUri(context), sampleSize)
}
