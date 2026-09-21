package com.davemorrissey.labs.subscaleview.decoder

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.webkit.MimeTypeMap
import androidx.annotation.WorkerThread
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.internal.ERROR_FORMAT_NOT_SUPPORTED
import com.davemorrissey.labs.subscaleview.internal.URI_PATH_ASSET
import com.davemorrissey.labs.subscaleview.internal.URI_SCHEME_CONTENT
import com.davemorrissey.labs.subscaleview.internal.URI_SCHEME_FILE
import com.davemorrissey.labs.subscaleview.internal.URI_SCHEME_RES
import com.davemorrissey.labs.subscaleview.internal.URI_SCHEME_ZIP
import org.jetbrains.annotations.Blocking
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.io.path.Path

@Blocking
internal fun BitmapRegionDecoder(pathName: String, context: Context?, uri: Uri?): BitmapRegionDecoder = try {
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
		BitmapRegionDecoder.newInstance(pathName)
	} else {
		@Suppress("DEPRECATION")
		BitmapRegionDecoder.newInstance(pathName, false)
	}
} catch (e: IOException) {
	if (e.message == ERROR_FORMAT_NOT_SUPPORTED) {
		throw ImageDecodeException.create(context, uri)
	} else {
		throw e
	}
}

@Blocking
internal fun BitmapRegionDecoder(inputStream: InputStream, context: Context?, uri: Uri?): BitmapRegionDecoder = try {
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
		BitmapRegionDecoder.newInstance(inputStream)
	} else {
		@Suppress("DEPRECATION")
		BitmapRegionDecoder.newInstance(inputStream, false)
	} ?: throw RuntimeException("Cannot instantiate BitmapRegionDecoder")
} catch (e: IOException) {
	if (e.message == ERROR_FORMAT_NOT_SUPPORTED) {
		throw ImageDecodeException.create(context, uri)
	} else {
		throw e
	}
}

internal fun Context.isLowMemory(): Boolean {
	val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
	return if (activityManager != null) {
		val memoryInfo = ActivityManager.MemoryInfo()
		activityManager.getMemoryInfo(memoryInfo)
		memoryInfo.lowMemory
	} else {
		true
	}
}

@WorkerThread
@Blocking
@SuppressLint("DiscouragedApi")
internal fun decodeResource(context: Context, uri: Uri, options: BitmapFactory.Options): Bitmap {
	val packageName = uri.authority
	val res = if (packageName == null || context.packageName == packageName) {
		context.resources
	} else {
		context.packageManager.getResourcesForApplication(packageName)
	}
	var id = 0
	val segments = uri.pathSegments
	val size = segments.size
	if (size == 2 && segments[0] == "drawable") {
		val resName = segments[1]
		id = res.getIdentifier(resName, "drawable", packageName)
	} else if (size == 1 && TextUtils.isDigitsOnly(segments[0])) {
		try {
			id = segments[0].toInt()
		} catch (ignored: NumberFormatException) {
		}
	}
	return BitmapFactory.decodeResource(res, id, options)
}

@WorkerThread
@Blocking
internal fun ContentResolver.decodeBitmap(uri: Uri, options: BitmapFactory.Options): Bitmap? {
	return openInputStream(uri)?.use { inputStream ->
		BitmapFactory.decodeStream(inputStream, null, options)
	}
}

@WorkerThread
@Blocking
internal fun AssetManager.decodeBitmap(name: String, options: BitmapFactory.Options): Bitmap? {
	return open(name).use { inputStream ->
		BitmapFactory.decodeStream(inputStream, null, options)
	}
}

internal fun ImageSource.toUri(context: Context): Uri = when (this) {
	is ImageSource.Resource -> Uri.parse(
		ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.packageName + "/" + resourceId,
	)

	is ImageSource.Uri -> uri
	is ImageSource.Bitmap -> throw IllegalArgumentException("Bitmap source cannot be represented as Uri")
}

@Blocking
@Throws(Exception::class)
internal fun detectImageFormat(context: Context, uri: Uri): String? = when (uri.scheme) {
	URI_SCHEME_RES -> "resource"
	URI_SCHEME_ZIP -> uri.fragment?.let { entryName ->
		MimeTypeMap.getSingleton().getMimeTypeFromExtension(entryName.substringAfterLast('.', ""))
			?: ZipFile(uri.schemeSpecificPart).use { file ->
				val entry: ZipEntry? = file.getEntry(entryName)
				if (entry != null) {
					file.getInputStream(entry).use { input ->
						HttpURLConnection.guessContentTypeFromStream(input)
					}
				} else {
					null
				}
			}
	}

	URI_SCHEME_FILE -> {
		val path = uri.schemeSpecificPart
		if (path.startsWith(URI_PATH_ASSET, ignoreCase = true)) {
			val assetName = path.substring(URI_PATH_ASSET.length)
			MimeTypeMap.getSingleton().getMimeTypeFromExtension(assetName.substringAfterLast('.', ""))
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			Files.probeContentType(Path(path))
		} else {
			val options = BitmapFactory.Options()
			options.inJustDecodeBounds = true
			BitmapFactory.decodeFile(path)
			options.outMimeType
		}
	}

	URI_SCHEME_CONTENT -> context.contentResolver.getType(uri)

	else -> null
}
