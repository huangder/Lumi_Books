package com.davemorrissey.labs.subscaleview.internal

import android.content.Context
import android.graphics.PointF
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.animation.DecelerateInterpolator
import androidx.annotation.CheckResult
import androidx.annotation.WorkerThread
import androidx.exifinterface.media.ExifInterface
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.Companion.VALID_ORIENTATIONS
import java.util.zip.ZipFile
import kotlin.math.hypot

/**
 * Helper method for setting the values of a tile matrix array.
 */
internal fun setMatrixArray(
	array: FloatArray,
	f0: Float,
	f1: Float,
	f2: Float,
	f3: Float,
	f4: Float,
	f5: Float,
	f6: Float,
	f7: Float,
) {
	array[0] = f0
	array[1] = f1
	array[2] = f2
	array[3] = f3
	array[4] = f4
	array[5] = f5
	array[6] = f6
	array[7] = f7
}

/**
 * Pythagoras distance between two points.
 */
internal fun distance(x0: Float, x1: Float, y0: Float, y1: Float): Float {
	return hypot(x0 - x1, y0 - y1)
}

@WorkerThread
internal fun getExifOrientation(context: Context, sourceUri: Uri): Int = runCatching {
	var exifOrientation = SubsamplingScaleImageView.ORIENTATION_0
	when (sourceUri.scheme) {
		URI_SCHEME_CONTENT -> {
			val columns = arrayOf(MediaStore.Images.Media.ORIENTATION)
			context.contentResolver.query(sourceUri, columns, null, null, null)?.use { cursor ->
				if (cursor.moveToFirst()) {
					val orientation = cursor.getInt(0)
					if (orientation in VALID_ORIENTATIONS && orientation != SubsamplingScaleImageView.ORIENTATION_USE_EXIF) {
						exifOrientation = orientation
					} else {
						Log.w(SubsamplingScaleImageView.TAG, "Unsupported orientation: $orientation")
					}
				}
			}
		}

		URI_SCHEME_FILE -> {
			val path = sourceUri.schemeSpecificPart
			if (path.startsWith(URI_PATH_ASSET)) {
				context.assets.openFd(path.substring(URI_PATH_ASSET.length)).use {
					exifOrientation = ExifInterface(it.fileDescriptor).getSsivOrientation(exifOrientation)
				}
			} else {
				exifOrientation = ExifInterface(sourceUri.schemeSpecificPart).getSsivOrientation(exifOrientation)
			}
		}

		URI_SCHEME_ZIP -> {
			ZipFile(sourceUri.schemeSpecificPart).use { file ->
				file.getEntry(sourceUri.fragment)?.let { entry ->
					exifOrientation = file.getInputStream(entry).use {
						ExifInterface(it).getSsivOrientation(exifOrientation)
					}
				}
			}
		}
	}
	exifOrientation
}.onFailure { e ->
	Log.w(SubsamplingScaleImageView.TAG, "Could not get EXIF orientation of image: $e")
}.getOrDefault(SubsamplingScaleImageView.ORIENTATION_0)

@CheckResult
private fun ExifInterface.getSsivOrientation(fallback: Int) = when (
	val exifAttr = getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
) {
	ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> SubsamplingScaleImageView.ORIENTATION_0

	ExifInterface.ORIENTATION_ROTATE_90 -> SubsamplingScaleImageView.ORIENTATION_90
	ExifInterface.ORIENTATION_ROTATE_180 -> SubsamplingScaleImageView.ORIENTATION_180
	ExifInterface.ORIENTATION_ROTATE_270 -> SubsamplingScaleImageView.ORIENTATION_270
	else -> {
		Log.w(SubsamplingScaleImageView.TAG, "Unsupported EXIF orientation: $exifAttr")
		fallback
	}
}

internal fun SubsamplingScaleImageView.scaleBy(factor: Float): Boolean {
	val center = getCenter() ?: return false
	val newScale = scale * factor
	return animateScaleAndCenter(newScale, center)?.apply {
		withDuration(resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
		withInterpolator(DecelerateInterpolator())
		start()
	} != null
}

internal fun SubsamplingScaleImageView.panBy(dx: Float, dy: Float): Boolean {
	val newCenter = getCenter() ?: return false
	newCenter.offset(dx, dy)
	return animateScaleAndCenter(scale, newCenter)?.apply {
		withDuration(resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
		withInterpolator(DecelerateInterpolator())
		start()
	} != null
}

internal fun PointF.copy() = PointF(x, y)

internal val ImageSource.isTilingEnabled: Boolean
	get() = this !is ImageSource.Bitmap

internal val ImageSource.sWidth: Int
	get() = region?.width() ?: (this as? ImageSource.Bitmap)?.bitmap?.width ?: 0

internal val ImageSource.sHeight: Int
	get() = region?.height() ?: (this as? ImageSource.Bitmap)?.bitmap?.height ?: 0
