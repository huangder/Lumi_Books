package com.davemorrissey.labs.subscaleview

import android.content.ContentResolver.SCHEME_FILE
import android.graphics.Rect
import android.os.Parcelable
import androidx.annotation.CheckResult
import androidx.annotation.DrawableRes
import com.davemorrissey.labs.subscaleview.internal.URI_PATH_ASSET
import com.davemorrissey.labs.subscaleview.internal.URI_SCHEME_FILE
import com.davemorrissey.labs.subscaleview.internal.URI_SCHEME_ZIP
import kotlinx.parcelize.Parcelize
import java.io.File
import android.graphics.Bitmap as AndroidBitmap
import android.net.Uri as AndroidUri

public sealed interface ImageSource : Parcelable {

	public val region: Rect?

	public override fun equals(other: Any?): Boolean

	public override fun hashCode(): Int

	@CheckResult
	public fun region(region: Rect?): ImageSource

	@Parcelize
	public data class Uri internal constructor(
		public val uri: AndroidUri,
		public override val region: Rect?,
	) : ImageSource {

		@CheckResult
		override fun region(region: Rect?): ImageSource = copy(region = region)
	}

	@Parcelize
	public data class Resource internal constructor(
		@DrawableRes public val resourceId: Int,
		public override val region: Rect?,
	) : ImageSource {

		@CheckResult
		override fun region(region: Rect?): ImageSource = copy(region = region)
	}

	@Parcelize
	public data class Bitmap internal constructor(
		public val bitmap: AndroidBitmap,
		public val isCached: Boolean,
		public override val region: Rect?,
	) : ImageSource {

		@CheckResult
		override fun region(region: Rect?): ImageSource = copy(region = region)
	}

	public companion object {

		@JvmStatic
		public fun uri(uri: AndroidUri): Uri = Uri(uri, null)

		@JvmStatic
		public fun uri(uri: String): Uri {
			var uriString = uri
			if (!uriString.contains("://")) {
				if (uriString.startsWith("/")) {
					uriString = uriString.substring(1)
				}
				uriString = "$SCHEME_FILE:///$uriString"
			}
			return uri(AndroidUri.parse(uriString))
		}

		@JvmStatic
		public fun asset(assetName: String): Uri = uri(
			AndroidUri.fromParts(URI_SCHEME_FILE, URI_PATH_ASSET + assetName, null),
		)

		@JvmStatic
		public fun file(file: File): Uri = uri(AndroidUri.fromFile(file))

		@JvmStatic
		public fun zipEntry(zipFile: File, entryPath: String): Uri = uri(
			AndroidUri.fromParts(URI_SCHEME_ZIP, zipFile.absolutePath, entryPath),
		)

		@JvmStatic
		public fun resource(@DrawableRes drawableResId: Int): Resource = Resource(drawableResId, null)

		/**
		 * Create an [ImageSource] with a Bitmap that will be recycled if it is no longer used.
		 */
		@JvmStatic
		public fun bitmap(bitmap: AndroidBitmap): Bitmap = Bitmap(bitmap, false, null)

		/**
		 * Create an [ImageSource] with a Bitmap that will **not** be recycled if it is no longer used.
		 */
		@JvmStatic
		public fun cachedBitmap(bitmap: AndroidBitmap): Bitmap = Bitmap(bitmap, true, null)
	}
}
