package com.davemorrissey.labs.subscaleview.decoder

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.graphics.*
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import androidx.annotation.Keep
import androidx.annotation.WorkerThread
import com.davemorrissey.labs.subscaleview.internal.URI_PATH_ASSET
import com.davemorrissey.labs.subscaleview.internal.URI_SCHEME_CONTENT
import com.davemorrissey.labs.subscaleview.internal.URI_SCHEME_FILE
import com.davemorrissey.labs.subscaleview.internal.URI_SCHEME_RES
import com.davemorrissey.labs.subscaleview.internal.URI_SCHEME_ZIP
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.zip.ZipFile
import kotlin.concurrent.thread

/**
 *
 *
 * An implementation of [ImageRegionDecoder] using a pool of [BitmapRegionDecoder]s,
 * to provide true parallel loading of tiles. This is only effective if parallel loading has been
 * enabled in the view by changing [com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.backgroundDispatcher]
 * with a multi-threaded [CoroutineDispatcher] instance.
 *
 *
 * One decoder is initialised when the class is initialised. This is enough to decode base layer tiles.
 * Additional decoders are initialised when a subregion of the image is first requested, which indicates
 * interaction with the view. Creation of additional encoders stops when [.allowAdditionalDecoder]
 * returns false. The default implementation takes into account the file size, number of CPU cores,
 * low memory status and a hard limit of 4. Extend this class to customise this.
 *
 *
 * **WARNING:** This class is highly experimental and not proven to be stable on a wide range of
 * devices. You are advised to test it thoroughly on all available devices, and code your app to use
 * [SkiaImageRegionDecoder] on old or low powered devices you could not test.
 *
 */
public open class SkiaPooledImageRegionDecoder @JvmOverloads constructor(
	private val bitmapConfig: Bitmap.Config = Bitmap.Config.RGB_565,
) : ImageRegionDecoder {

	private var decoderPool: DecoderPool? = DecoderPool()
	private val decoderLock: ReadWriteLock = ReentrantReadWriteLock(true)
	private var context: Context? = null
	private var uri: Uri? = null
	private var fileLength = Long.MAX_VALUE
	private val imageDimensions = Point(0, 0)
	private val isLazyInited = AtomicBoolean(false)

	/**
	 * Initialises the decoder pool. This method creates one decoder on the current thread and uses
	 * it to decode the bounds, then spawns an independent thread to populate the pool with an
	 * additional three decoders. The thread will abort if [.recycle] is called.
	 */
	@Throws(Exception::class)
	override fun init(context: Context, uri: Uri): Point {
		this.context = context
		this.uri = uri
		initialiseDecoder()
		return imageDimensions
	}

	/**
	 * Initialises extra decoders for as long as [.allowAdditionalDecoder] returns
	 * true and the pool has not been recycled.
	 */
	private fun lazyInit() {
		if (isLazyInited.compareAndSet(false, true) && fileLength < Long.MAX_VALUE) {
			debug("Starting lazy init of additional decoders")
			thread(start = true) {
				while (decoderPool != null && allowAdditionalDecoder(
						decoderPool!!.size,
						fileLength,
					)
				) {
					// New decoders can be created while reading tiles but this read lock prevents
					// them being initialised while the pool is being recycled.
					try {
						if (decoderPool != null) {
							val start = System.currentTimeMillis()
							debug("Starting decoder")
							initialiseDecoder()
							val end = System.currentTimeMillis()
							debug("Started decoder, took " + (end - start) + "ms")
						}
					} catch (e: Exception) {
						// A decoder has already been successfully created so we can ignore this
						debug("Failed to start decoder: " + e.message)
					}
				}
			}
		}
	}

	/**
	 * Initialises a new [BitmapRegionDecoder] and adds it to the pool, unless the pool has
	 * been recycled while it was created.
	 */
	@SuppressLint("DiscouragedApi")
	@Throws(Exception::class)
	@WorkerThread
	private fun initialiseDecoder() {
		val uri = checkNotNull(uri)
		val context = checkNotNull(context)
		var fileLength = Long.MAX_VALUE
		val decoder: BitmapRegionDecoder = when (uri.scheme) {
			URI_SCHEME_RES -> {
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
				try {
					val descriptor = context.resources.openRawResourceFd(id)
					fileLength = descriptor.length
				} catch (e: Exception) {
					// Pooling disabled
				}
				res.openRawResource(id).use { BitmapRegionDecoder(it, context, uri) }
			}

			URI_SCHEME_ZIP -> ZipFile(uri.schemeSpecificPart).use { file ->
				val entry = requireNotNull(file.getEntry(uri.fragment)) {
					"Entry ${uri.fragment} not found in the zip"
				}
				file.getInputStream(entry).use { input ->
					BitmapRegionDecoder(input, context, uri)
				}
			}

			URI_SCHEME_FILE -> {
				val path = uri.schemeSpecificPart
				if (path.startsWith(URI_PATH_ASSET, ignoreCase = true)) {
					val assetName = path.substring(URI_PATH_ASSET.length)
					try {
						val descriptor = context.assets.openFd(assetName)
						fileLength = descriptor.length
					} catch (e: Exception) {
						// Pooling disabled
					}
					context.assets.open(assetName, AssetManager.ACCESS_RANDOM)
						.use { BitmapRegionDecoder(it, context, uri) }
				} else {
					val d = BitmapRegionDecoder(path, context, uri)
					try {
						val file = File(path)
						if (file.exists()) {
							fileLength = file.length()
						}
					} catch (e: Exception) {
						// Pooling disabled
					}
					d
				}
			}

			URI_SCHEME_CONTENT -> {
				val contentResolver = context.contentResolver
				val d = contentResolver.openInputStream(uri)?.use { BitmapRegionDecoder(it, context, uri) }
				try {
					contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
						fileLength = descriptor.length
					}
				} catch (e: Exception) {
					// Stick with MAX_LENGTH
				}
				d
			}

			else -> throw UnsupportedUriException(uri.toString())
		} ?: throw ImageDecodeException.create(context, uri)
		this.fileLength = fileLength
		imageDimensions[decoder.width] = decoder.height
		decoderLock.writeLock().lock()
		try {
			decoderPool?.add(decoder)
		} finally {
			decoderLock.writeLock().unlock()
		}
	}

	/**
	 * Acquire a read lock to prevent decoding overlapping with recycling, then check the pool still
	 * exists and acquire a decoder to load the requested region. There is no check whether the pool
	 * currently has decoders, because it's guaranteed to have one decoder after [.init]
	 * is called and be null once [.recycle] is called. In practice the view can't call this
	 * method until after [.init], so there will be no blocking on an empty pool.
	 */
	@WorkerThread
	override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
		debug("Decode region " + sRect + " on thread " + Thread.currentThread().name)
		if (sRect.width() < imageDimensions.x || sRect.height() < imageDimensions.y) {
			lazyInit()
		}
		decoderLock.readLock().lock()
		try {
			if (decoderPool != null) {
				val decoder = decoderPool!!.acquire()
				try {
					// Decoder can't be null or recycled in practice
					if (decoder != null && !decoder.isRecycled) {
						val options = BitmapFactory.Options()
						options.inSampleSize = sampleSize
						options.inPreferredConfig = bitmapConfig
						return decoder.decodeRegion(sRect, options) ?: throw ImageDecodeException.create(context, uri)
					}
				} finally {
					if (decoder != null) {
						decoderPool?.release(decoder)
					}
				}
			}
			throw IllegalStateException("Cannot decode region after decoder has been recycled")
		} finally {
			decoderLock.readLock().unlock()
		}
	}

	/**
	 * Holding a read lock to avoid returning true while the pool is being recycled, this returns
	 * true if the pool has at least one decoder available.
	 */
	@get:Synchronized
	override val isReady: Boolean
		get() = decoderPool?.isEmpty == false

	/**
	 * Wait until all read locks held by [.decodeRegion] are released, then recycle
	 * and destroy the pool. Elsewhere, when a read lock is acquired, we must check the pool is not null.
	 */
	@Synchronized
	override fun recycle() {
		decoderLock.writeLock().lock()
		try {
			decoderPool?.recycle()
			decoderPool = null
			context = null
			uri = null
		} finally {
			decoderLock.writeLock().unlock()
		}
	}

	/**
	 * Called before creating a new decoder. Based on number of CPU cores, available memory, and the
	 * size of the image file, determines whether another decoder can be created. Subclasses can
	 * override and customise this.
	 * @param numberOfDecoders the number of decoders that have been created so far
	 * @param fileLength the size of the image file in bytes. Creating another decoder will use approximately this much native memory.
	 * @return true if another decoder can be created.
	 */
	protected open fun allowAdditionalDecoder(numberOfDecoders: Int, fileLength: Long): Boolean {
		if (numberOfDecoders >= 4) {
			debug("No additional decoders allowed, reached hard limit (4)")
			return false
		} else if (numberOfDecoders * fileLength > 20 * 1024 * 1024) {
			debug("No additional encoders allowed, reached hard memory limit (20Mb)")
			return false
		} else if (numberOfDecoders >= numberOfCores) {
			debug("No additional encoders allowed, limited by CPU cores ($numberOfCores)")
			return false
		} else if (context?.isLowMemory() != false) {
			debug("No additional encoders allowed, memory is low")
			return false
		}
		debug(
			"Additional decoder allowed, current count is " + numberOfDecoders + ", estimated native memory " + fileLength * numberOfDecoders / (1024 * 1024) + "Mb",
		)
		return true
	}

	private val numberOfCores: Int
		get() = Runtime.getRuntime().availableProcessors()

	private fun debug(message: String) {
		if (isDebug) {
			Log.d(TAG, message)
		}
	}

	public class Factory @JvmOverloads constructor(
		override val bitmapConfig: Bitmap.Config = Bitmap.Config.RGB_565,
	) : DecoderFactory<SkiaPooledImageRegionDecoder> {

		override fun make(): SkiaPooledImageRegionDecoder {
			return SkiaPooledImageRegionDecoder(bitmapConfig)
		}
	}

	public companion object {

		private const val TAG = "SkiaPooledDecoder"

		/**
		 * Controls logging of debug messages. All instances are affected.
		 * @param debug true to enable debug logging, false to disable.
		 */
		@Keep
		@set:JvmName("setDebug")
		public var isDebug: Boolean = false
	}
}
