package com.davemorrissey.labs.subscaleview

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.annotation.AnyThread
import androidx.annotation.AttrRes
import androidx.annotation.CallSuper
import androidx.annotation.CheckResult
import androidx.annotation.ColorInt
import androidx.core.view.ViewConfigurationCompat
import androidx.lifecycle.LifecycleOwner
import com.davemorrissey.labs.subscaleview.decoder.DecoderFactory
import com.davemorrissey.labs.subscaleview.decoder.ImageDecoder
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageDecoder
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageRegionDecoder
import com.davemorrissey.labs.subscaleview.decoder.toUri
import com.davemorrissey.labs.subscaleview.internal.Anim
import com.davemorrissey.labs.subscaleview.internal.ClearingLifecycleObserver
import com.davemorrissey.labs.subscaleview.internal.CompositeImageEventListener
import com.davemorrissey.labs.subscaleview.internal.GestureListener
import com.davemorrissey.labs.subscaleview.internal.InternalErrorHandler
import com.davemorrissey.labs.subscaleview.internal.ScaleAndTranslate
import com.davemorrissey.labs.subscaleview.internal.Tile
import com.davemorrissey.labs.subscaleview.internal.TileMap
import com.davemorrissey.labs.subscaleview.internal.TouchEventDelegate
import com.davemorrissey.labs.subscaleview.internal.getExifOrientation
import com.davemorrissey.labs.subscaleview.internal.isTilingEnabled
import com.davemorrissey.labs.subscaleview.internal.panBy
import com.davemorrissey.labs.subscaleview.internal.sHeight
import com.davemorrissey.labs.subscaleview.internal.sWidth
import com.davemorrissey.labs.subscaleview.internal.scaleBy
import com.davemorrissey.labs.subscaleview.internal.setMatrixArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.util.Locale
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.math.min
import kotlin.math.roundToInt

@Suppress("MemberVisibilityCanBePrivate")
public open class SubsamplingScaleImageView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	@AttrRes defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

	// Bitmap (preview or full image)
	private var bitmap: Bitmap? = null

	// Whether the bitmap is a preview image
	private var bitmapIsPreview: Boolean = false

	// Specifies if a cache handler is also referencing the bitmap. Do not recycle if so.
	private var bitmapIsCached: Boolean = false

	// Uri of full size image
	private var uri: Uri? = null

	// Sample size used to display the whole image when fully zoomed out
	private var fullImageSampleSize: Int = 0

	// Map of zoom level to tile grid
	private var tileMap: TileMap? = null

	private var _downSampling = 1
	public var downSampling: Int = _downSampling
		set(value) {
			require(value > 0 && value.countOneBits() == 1) {
				"Downsampling value must be a positive power of 2"
			}
			if (field != value) {
				field = value
				invalidateTiles()
			}
		}

	// Image orientation setting
	public var orientation: Int = ORIENTATION_0
		set(value) {
			require(value in VALID_ORIENTATIONS)
			field = value
			reset(false)
			invalidate()
			requestLayout()
		}

	// Max scale allowed (prevent infinite zoom)
	public var maxScale: Float = 2F

	// Min scale allowed (prevent infinite zoom)
	private var _minScale: Float = minScale()

	public var minScale: Float
		get() = minScale()
		set(value) {
			_minScale = value
		}

	// Density to reach before loading higher resolution tiles
	private var minimumTileDpi: Int = -1
	/**
	 * Scale applied by a parent renderer after this view is drawn. It affects tile selection only,
	 * allowing Compose to own gestures while this view still loads tiles dense enough for zoom.
	 */
	public var externalScale: Float = 1f
		set(value) {
			val next = value.coerceAtLeast(1f)
			if (field == next) return
			field = next
			if (isReady) refreshRequiredTiles(load = true)
		}

	// Pan limiting style
	public var panLimit: Int = PAN_LIMIT_INSIDE
		set(value) {
			require(value in VALID_PAN_LIMITS) { "Invalid pan limit: $value" }
			field = value
			if (isReady) {
				fitToBounds(true)
				invalidate()
			}
		}

	@get:ColorInt
	public var tileBackgroundColor: Int
		get() = tileBgPaint?.color ?: Color.TRANSPARENT
		/**
		 * Set a solid color to render behind tiles, useful for displaying transparent PNGs.
		 * @param value Background color for tiles.
		 */
		set(@ColorInt value) {
			tileBgPaint = if (Color.alpha(value) == 0) {
				null
			} else {
				Paint().apply {
					style = Paint.Style.FILL
					color = value
				}
			}
			invalidate()
		}

	// Minimum scale type
	public var minimumScaleType: Int = SCALE_TYPE_CENTER_INSIDE
		set(value) {
			require(value in VALID_SCALE_TYPES) { "Invalid scale type: $value" }
			field = value
			if (isReady) {
				fitToBounds(true)
				invalidate()
			}
		}

	// overrides for the dimensions of the generated tiles
	private var maxTileWidth: Int = TILE_SIZE_AUTO
	private var maxTileHeight: Int = TILE_SIZE_AUTO

	public var backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default

	// Whether tiles should be loaded while gestures and animations are still in progress
	public var isEagerLoadingEnabled: Boolean = true

	// Gesture detection settings
	public var isPanEnabled: Boolean = true
		set(value) {
			field = value
			if (!value) {
				vTranslate?.set(
					width / 2f - scale * (sWidth() / 2f),
					height / 2f - scale * (sHeight() / 2f),
				)
				if (isReady) {
					refreshRequiredTiles(load = true)
					invalidate()
				}
			}
		}
	public var isZoomEnabled: Boolean = true
	public var isQuickScaleEnabled: Boolean = true

	// Double tap zoom behaviour
	public var doubleTapZoomScale: Float = 1F
	public var doubleTapZoomStyle: Int = ZOOM_FOCUS_FIXED
		set(value) {
			require(value in VALID_ZOOM_STYLES)
			field = value
		}
	private var doubleTapZoomDuration: Int = 500

	// Current scale and scale at start of zoom
	public var scale: Float = 0f
		@JvmSynthetic
		internal set

	@JvmField
	@JvmSynthetic
	internal var scaleStart: Float = 0f

	// Screen coordinate of top-left corner of source image
	@JvmField
	@JvmSynthetic
	internal var vTranslate: PointF? = null

	@JvmField
	@JvmSynthetic
	internal var vTranslateStart: PointF? = null
	private var vTranslateBefore: PointF? = null

	// Source coordinate to center on, used when new position is set externally before view is ready
	private var pendingScale: Float? = null
	private var sPendingCenter: PointF? = null

	@JvmField
	@JvmSynthetic
	internal var sRequestedCenter: PointF? = null

	// Source image dimensions and orientation - dimensions relate to the unrotated image
	public var sWidth: Int = 0
		private set
	public var sHeight: Int = 0
		private set
	private var sOrientation: Int = 0
	private var sRegion: Rect? = null
	private var pRegion: Rect? = null

	// Is two-finger zooming in progress
	@JvmField
	@JvmSynthetic
	internal var isZooming: Boolean = false

	// Is one-finger panning in progress
	@JvmField
	@JvmSynthetic
	internal var isPanning: Boolean = false

	// Is quick-scale gesture in progress
	@JvmField
	@JvmSynthetic
	internal var isQuickScaling: Boolean = false

	// Fling detector
	private var detector: GestureDetector? = null
	private var singleDetector: GestureDetector? = null

	// Tile and image decoding
	private var decoder: ImageRegionDecoder? = null
	private val decoderLock = ReentrantReadWriteLock(true)
	public var bitmapDecoderFactory: DecoderFactory<out ImageDecoder> = SkiaImageDecoder.Factory()
	public var regionDecoderFactory: DecoderFactory<out ImageRegionDecoder> = SkiaImageRegionDecoder.Factory()

	// Debug values
	protected val isDebugDrawingEnabled: Boolean
		get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			isShowingLayoutBounds
		} else {
			false
		}

	@JvmField
	@JvmSynthetic
	internal var vCenterStart: PointF? = null

	@JvmField
	@JvmSynthetic
	internal var vDistStart: Float = 0f

	@JvmField
	@JvmSynthetic
	internal var quickScaleLastDistance: Float = 0f

	@JvmField
	@JvmSynthetic
	internal var quickScaleMoved: Boolean = false

	@JvmField
	@JvmSynthetic
	internal var quickScaleVLastPoint: PointF? = null

	@JvmField
	@JvmSynthetic
	internal var quickScaleSCenter: PointF? = null

	@JvmField
	@JvmSynthetic
	internal var quickScaleVStart: PointF? = null

	// Scale and center animation tracking
	@JvmField
	@JvmSynthetic
	internal var anim: Anim? = null

	// Whether a ready notification has been sent to subclasses
	@JvmField
	@JvmSynthetic
	internal var isReadySent: Boolean = false

	// Whether a base layer loaded notification has been sent to subclasses
	private var imageLoadedSent: Boolean = false

	// Event listener
	private val onImageEventListeners = CompositeImageEventListener()

	protected val onImageEventListener: OnImageEventListener
		get() = onImageEventListeners

	// Scale and center listener
	public var onStateChangedListener: OnStateChangedListener? = null

	@Suppress("LeakingThis")
	private val touchEventDelegate = TouchEventDelegate(this)

	// Paint objects created once and reused for efficiency
	private var bitmapPaint: Paint? = null
	private var debugTextPaint: Paint? = null
	private var debugLinePaint: Paint? = null
	private var tileBgPaint: Paint? = null

	public var colorFilter: ColorFilter? = null
		set(value) {
			field = value
			bitmapPaint?.colorFilter = value
		}

	private var pendingState: ImageViewState? = null

	private var stateRestoreStrategy: Int = RESTORE_STRATEGY_NONE

	// Volatile fields used to reduce object creation
	private var satTemp: ScaleAndTranslate? = null
	private var matrix2: Matrix? = null
	private var sRect: RectF? = null
	private val srcArray = FloatArray(8)
	private val dstArray = FloatArray(8)

	/**
	 * Call to find whether the view is initialised, has dimensions, and will display an image on
	 * the next draw. If a preview has been provided, it may be the preview that will be displayed
	 * and the full size image may still be loading. If no preview was provided, this is called once
	 * the base layer tiles of the full size image are loaded.
	 * @return true if the view is ready to display an image and accept touch gestures.
	 */
	public val isReady: Boolean
		get() = isReadySent

	// The logical density of the display
	private val density = context.resources.displayMetrics.density
	private val viewConfig = ViewConfiguration.get(context)
	protected val coroutineScope: CoroutineScope = CoroutineScope(
		Dispatchers.Main.immediate + InternalErrorHandler() + SupervisorJob(),
	)

	init {
		setMinimumDpi(160)
		setDoubleTapZoomDpi(160)
		setMinimumTileDpi(320)
		setGestureDetector(context)
		val ta = context.obtainStyledAttributes(attrs, R.styleable.SubsamplingScaleImageView, defStyleAttr, 0)
		downSampling = ta.getInt(R.styleable.SubsamplingScaleImageView_downSampling, downSampling)
		isPanEnabled = ta.getBoolean(R.styleable.SubsamplingScaleImageView_panEnabled, isPanEnabled)
		isZoomEnabled = ta.getBoolean(R.styleable.SubsamplingScaleImageView_zoomEnabled, isZoomEnabled)
		doubleTapZoomStyle = ta.getInt(R.styleable.SubsamplingScaleImageView_doubleTapZoomStyle, doubleTapZoomStyle)
		isQuickScaleEnabled =
			ta.getBoolean(R.styleable.SubsamplingScaleImageView_quickScaleEnabled, isQuickScaleEnabled)
		panLimit = ta.getInt(R.styleable.SubsamplingScaleImageView_panLimit, panLimit)
		stateRestoreStrategy = ta.getInt(R.styleable.SubsamplingScaleImageView_restoreStrategy, stateRestoreStrategy)
		tileBackgroundColor = ta.getColor(
			R.styleable.SubsamplingScaleImageView_tileBackgroundColor,
			Color.TRANSPARENT,
		)
		val assetName = ta.getString(R.styleable.SubsamplingScaleImageView_assetName)
		if (!assetName.isNullOrBlank()) {
			setImage(ImageSource.asset(assetName))
		} else {
			val resId = ta.getResourceId(R.styleable.SubsamplingScaleImageView_src, 0)
			if (resId != 0) {
				setImage(ImageSource.resource(resId))
			}
		}
		ta.recycle()
	}

	@JvmOverloads
	public fun setImage(imageSource: ImageSource, previewSource: ImageSource? = null, state: ImageViewState? = null) {
		reset(true)
		state?.let { restoreState(it) }
		pendingState?.let { restoreState(it) }

		if (previewSource != null) {
			require(imageSource !is ImageSource.Bitmap) {
				"Preview image cannot be used when a bitmap is provided for the main image"
			}
			require(imageSource.sWidth > 0 && imageSource.sHeight > 0) {
				"Preview image cannot be used unless dimensions are provided for the main image"
			}
			this.sWidth = imageSource.sWidth
			this.sHeight = imageSource.sHeight
			this.pRegion = previewSource.region
			when (previewSource) {
				is ImageSource.Bitmap -> {
					this.bitmapIsCached = previewSource.isCached
					onPreviewLoaded(previewSource.bitmap)
				}

				else -> {
					val uri = (previewSource as? ImageSource.Uri)?.uri ?: Uri.parse(
						ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.packageName + "/" +
							(previewSource as ImageSource.Resource).resourceId,
					)
					loadBitmap(uri, true)
				}
			}
		}

		when (imageSource) {
			is ImageSource.Bitmap -> {
				val region = imageSource.region
				if (region != null) {
					onImageLoaded(
						Bitmap.createBitmap(
							imageSource.bitmap,
							region.left,
							region.right,
							region.width(),
							region.height(),
						),
						ORIENTATION_0,
						false,
					)
				} else {
					onImageLoaded(imageSource.bitmap, ORIENTATION_0, imageSource.isCached)
				}
			}

			else -> {
				sRegion = imageSource.region
				uri = imageSource.toUri(context).also { uri ->
					if (imageSource.isTilingEnabled || sRegion != null) {
						// Load the bitmap using tile decoding.
						initTiles(regionDecoderFactory, uri)
					} else {
						// Load the bitmap as a single image.
						loadBitmap(uri, false)
					}
				}
			}
		}
	}

	/**
	 * Releases all resources the view is using and resets the state, nulling any fields that use significant memory.
	 * After you have called this method, the view can be re-used by setting a new image. Settings are remembered
	 * but state (scale and center) is forgotten. You can restore these yourself if required.
	 */
	@CallSuper
	public open fun recycle() {
		reset(true)
		bitmapPaint = null
		debugTextPaint = null
		debugLinePaint = null
		tileBgPaint = null
	}

	@CheckResult
	public fun snapshot(config: Bitmap.Config? = null): Bitmap? = bitmap?.let {
		if (!it.isRecycled) {
			it.copy(config ?: it.config ?: Bitmap.Config.ARGB_8888, false)
		} else {
			null
		}
	}

	/**
	 * This is a screen density aware alternative to {@link #setMaxScale(float)}; it allows you to express the maximum
	 * allowed scale in terms of the minimum pixel density. This avoids the problem of 1:1 scale still being
	 * too small on a high density screen. A sensible starting point is 160 - the default used by this view.
	 * @param dpi Source image pixel density at maximum zoom.
	 */
	public fun setMinimumDpi(dpi: Int) {
		val metrics = resources.displayMetrics
		val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
		maxScale = averageDpi / dpi
	}

	/**
	 * This is a screen density aware alternative to {@link #setMinScale(float)}; it allows you to express the minimum
	 * allowed scale in terms of the maximum pixel density.
	 * @param dpi Source image pixel density at minimum zoom.
	 */
	public fun setMaximumDpi(dpi: Int) {
		val metrics = resources.displayMetrics
		val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
		_minScale = averageDpi / dpi
	}

	public fun setMinimumTileDpi(minimumTileDpi: Int) {
		val metrics = resources.displayMetrics
		val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
		this.minimumTileDpi = minOf(averageDpi, minimumTileDpi.toFloat()).toInt()
		if (isReady) {
			reset(false)
			invalidate()
		}
	}

	/**
	 * A density aware alternative to [.setDoubleTapZoomScale]; this allows you to express the scale the
	 * image will zoom in to when double tapped in terms of the image pixel density. Values lower than the max scale will
	 * be ignored. A sensible starting point is 160 - the default used by this view.
	 * @param dpi New value for double tap gesture zoom scale.
	 */
	public fun setDoubleTapZoomDpi(dpi: Int) {
		val metrics = resources.displayMetrics
		val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
		doubleTapZoomScale = averageDpi / dpi
	}

	@JvmSynthetic
	internal fun setGestureDetector(context: Context) {
		detector = GestureDetector(context, GestureListener(this))
		singleDetector = GestureDetector(
			context,
			object : SimpleOnGestureListener() {
				override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
					performClick()
					return true
				}
			},
		)
	}

	override fun setOnLongClickListener(listener: OnLongClickListener?) {
		if (!isLongClickable) {
			isLongClickable = true
		}
		touchEventDelegate.onLongClickListener = listener
	}

	@Deprecated("Use addOnImageEventListener() instead")
	public fun setOnImageEventListener(listener: OnImageEventListener?) {
		onImageEventListeners.clearListeners()
		if (listener != null) {
			onImageEventListeners.addListener(listener)
		}
	}

	public fun addOnImageEventListener(listener: OnImageEventListener) {
		onImageEventListeners.addListener(listener)
	}

	public fun removeOnImageEventListener(listener: OnImageEventListener) {
		onImageEventListeners.removeListener(listener)
	}

	/**
	 * On resize, preserve center and scale. Various behaviours are possible, override this method to use another.
	 */
	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		val sCenter = getCenter()
		if (isReadySent && sCenter != null) {
			anim = null
			pendingScale = scale
			sPendingCenter = sCenter
		}
	}

	/**
	 * Measures the width and height of the view, preserving the aspect ratio of the image displayed if wrap_content is
	 * used. The image will scale within this box, not resizing the view as it is zoomed.
	 */
	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val widthSpecMode = MeasureSpec.getMode(widthMeasureSpec)
		val heightSpecMode = MeasureSpec.getMode(heightMeasureSpec)
		val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
		val parentHeight = MeasureSpec.getSize(heightMeasureSpec)
		val resizeWidth = widthSpecMode != MeasureSpec.EXACTLY
		val resizeHeight = heightSpecMode != MeasureSpec.EXACTLY
		var width = parentWidth
		var height = parentHeight
		if (sWidth > 0 && sHeight > 0) {
			if (resizeWidth && resizeHeight) {
				width = sWidth()
				height = sHeight()
			} else if (resizeHeight) {
				height = (sHeight().toDouble() / sWidth().toDouble() * width).toInt()
			} else if (resizeWidth) {
				width = (sWidth().toDouble() / sHeight().toDouble() * height).toInt()
			}
		}
		width = width.coerceAtLeast(suggestedMinimumWidth)
		height = height.coerceAtLeast(suggestedMinimumHeight)
		setMeasuredDimension(width, height)
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		createPaints()

		// If image or view dimensions are not known yet, abort.
		if ((sWidth == 0) || (sHeight == 0) || (width == 0) || (height == 0)) {
			return
		}

		// When using tiles, on first render with no tile map ready, initialise it and kick off async base image loading.
		if (tileMap == null && decoder != null) {
			initialiseBaseLayer(getMaxBitmapDimensions(canvas))
		}

		// If image has been loaded or supplied as a bitmap, onDraw may be the first time the view has
		// dimensions and therefore the first opportunity to set scale and translate. If this call returns
		// false there is nothing to be drawn so return immediately.
		if (!checkReady()) {
			return
		}

		// Set scale and translate before draw.
		preDraw()
		processAnimation()

		val tiles = tileMap
		if (tiles != null && isBaseLayerReady()) {
			// Optimum sample size for current scale
			val sampleSize = calculateInSampleSize(scale).coerceAtMost(fullImageSampleSize)

			// First check for missing tiles - if there are any we need the base layer underneath to avoid gaps
			val hasMissingTiles = tiles.hasMissingTiles(sampleSize)


			// Render all loaded tiles. LinkedHashMap used for bottom up rendering - lower res tiles underneath.
			for ((key, value) in tiles) {
				if (key == sampleSize || hasMissingTiles) {
					for (tile in value) {
						sourceToViewRect(tile.sRect, tile.vRect)
						if (tile.bitmap != null) {
							tileBgPaint?.let {
								canvas.drawRect(tile.vRect, it)
							}
							if (matrix2 == null) {
								matrix2 = Matrix()
							}
							matrix2!!.reset()
							setMatrixArray(
								srcArray,
								0f,
								0f,
								tile.bitmap!!.width.toFloat(),
								0f,
								tile.bitmap!!.width.toFloat(),
								tile.bitmap!!.height.toFloat(),
								0f,
								tile.bitmap!!.height.toFloat(),
							)
							if (getRequiredRotation() == ORIENTATION_0) {
								setMatrixArray(
									dstArray,
									tile.vRect.left.toFloat(),
									tile.vRect.top.toFloat(),
									tile.vRect.right.toFloat(),
									tile.vRect.top.toFloat(),
									tile.vRect.right.toFloat(),
									tile.vRect.bottom.toFloat(),
									tile.vRect.left.toFloat(),
									tile.vRect.bottom.toFloat(),
								)
							} else if (getRequiredRotation() == ORIENTATION_90) {
								setMatrixArray(
									dstArray,
									tile.vRect.right.toFloat(),
									tile.vRect.top.toFloat(),
									tile.vRect.right.toFloat(),
									tile.vRect.bottom.toFloat(),
									tile.vRect.left.toFloat(),
									tile.vRect.bottom.toFloat(),
									tile.vRect.left.toFloat(),
									tile.vRect.top.toFloat(),
								)
							} else if (getRequiredRotation() == ORIENTATION_180) {
								setMatrixArray(
									dstArray,
									tile.vRect.right.toFloat(),
									tile.vRect.bottom.toFloat(),
									tile.vRect.left.toFloat(),
									tile.vRect.bottom.toFloat(),
									tile.vRect.left.toFloat(),
									tile.vRect.top.toFloat(),
									tile.vRect.right.toFloat(),
									tile.vRect.top.toFloat(),
								)
							} else if (getRequiredRotation() == ORIENTATION_270) {
								setMatrixArray(
									dstArray,
									tile.vRect.left.toFloat(),
									tile.vRect.bottom.toFloat(),
									tile.vRect.left.toFloat(),
									tile.vRect.top.toFloat(),
									tile.vRect.right.toFloat(),
									tile.vRect.top.toFloat(),
									tile.vRect.right.toFloat(),
									tile.vRect.bottom.toFloat(),
								)
							}
							matrix2!!.setPolyToPoly(srcArray, 0, dstArray, 0, 4)
							canvas.drawBitmap(tile.bitmap!!, matrix2!!, bitmapPaint)
							if (isDebugDrawingEnabled) {
								canvas.drawRect(tile.vRect, debugLinePaint!!)
							}
						} else if (tile.isLoading && isDebugDrawingEnabled) {
							canvas.drawText(
								"LOADING",
								(tile.vRect.left + px(5)).toFloat(),
								(tile.vRect.top + px(35)).toFloat(),
								debugTextPaint!!,
							)
						}
						if (tile.isVisible && isDebugDrawingEnabled) {
							canvas.drawText(
								"ISS ${tile.sampleSize} RECT ${tile.sRect.top},${tile.sRect.left}," +
									"${tile.sRect.bottom},${tile.sRect.right}",
								(tile.vRect.left + px(5)).toFloat(),
								(tile.vRect.top + px(15)).toFloat(),
								debugTextPaint!!,
							)
						}
					}
				}
			}
		} else if (bitmap != null && !bitmap!!.isRecycled) {
			var xScale = scale
			var yScale = scale
			if (bitmapIsPreview) {
				xScale = scale * (sWidth.toFloat() / bitmap!!.width)
				yScale = scale * (sHeight.toFloat() / bitmap!!.height)
			} else if (_downSampling != 1) {
				xScale *= _downSampling
				yScale *= _downSampling
			}
			if (matrix2 == null) {
				matrix2 = Matrix()
			}
			matrix2!!.reset()
			matrix2!!.postScale(xScale, yScale)
			matrix2!!.postRotate(getRequiredRotation().toFloat())
			matrix2!!.postTranslate(vTranslate!!.x, vTranslate!!.y)
			if (getRequiredRotation() == ORIENTATION_180) {
				matrix2!!.postTranslate(scale * sWidth, scale * sHeight)
			} else if (getRequiredRotation() == ORIENTATION_90) {
				matrix2!!.postTranslate(scale * sHeight, 0f)
			} else if (getRequiredRotation() == ORIENTATION_270) {
				matrix2!!.postTranslate(0f, scale * sWidth)
			}
			if (tileBgPaint != null) {
				if (sRect == null) {
					sRect = RectF()
				}
				sRect!!.set(
					0f,
					0f,
					(if (bitmapIsPreview) bitmap!!.width else sWidth).toFloat(),
					(if (bitmapIsPreview) bitmap!!.height else sHeight).toFloat(),
				)
				matrix2!!.mapRect(sRect)
				canvas.drawRect(sRect!!, tileBgPaint!!)
			}
			canvas.drawBitmap(bitmap!!, matrix2!!, bitmapPaint)
		}
		if (isDebugDrawingEnabled) {
			canvas.drawText(
				"Scale: " + String.format(Locale.ENGLISH, "%.2f", scale) + " (" + String.format(
					Locale.ENGLISH,
					"%.2f",
					minScale(),
				) + " - " + String.format(
					Locale.ENGLISH,
					"%.2f",
					maxScale,
				) + ")",
				px(5).toFloat(),
				px(15).toFloat(),
				debugTextPaint!!,
			)
			canvas.drawText(
				"Translate: " + String.format(Locale.ENGLISH, "%.2f", vTranslate!!.x) + ":" + String.format(
					Locale.ENGLISH,
					"%.2f",
					vTranslate!!.y,
				),
				px(5).toFloat(),
				px(30).toFloat(),
				debugTextPaint!!,
			)
			val center = getCenter()
			canvas.drawText(
				"Source center: " + String.format(Locale.ENGLISH, "%.2f", center!!.x) + ":" + String.format(
					Locale.ENGLISH,
					"%.2f",
					center.y,
				),
				px(5).toFloat(),
				px(45).toFloat(),
				debugTextPaint!!,
			)
			if (anim != null) {
				val vCenterStart = sourceToViewCoord(anim!!.sCenterStart)
				val vCenterEndRequested = sourceToViewCoord(anim!!.sCenterEndRequested)
				val vCenterEnd = sourceToViewCoord(anim!!.sCenterEnd)
				canvas.drawCircle(vCenterStart!!.x, vCenterStart.y, px(10).toFloat(), debugLinePaint!!)
				debugLinePaint!!.color = Color.RED
				canvas.drawCircle(vCenterEndRequested!!.x, vCenterEndRequested.y, px(20).toFloat(), debugLinePaint!!)
				debugLinePaint!!.color = Color.BLUE
				canvas.drawCircle(vCenterEnd!!.x, vCenterEnd.y, px(25).toFloat(), debugLinePaint!!)
				debugLinePaint!!.color = Color.CYAN
				canvas.drawCircle(width / 2f, height / 2f, px(30).toFloat(), debugLinePaint!!)
			}
			if (vCenterStart != null) {
				debugLinePaint!!.color = Color.RED
				canvas.drawCircle(vCenterStart!!.x, vCenterStart!!.y, px(20).toFloat(), debugLinePaint!!)
			}
			if (quickScaleSCenter != null) {
				debugLinePaint!!.color = Color.BLUE
				canvas.drawCircle(
					sourceToViewX(quickScaleSCenter!!.x),
					sourceToViewY(quickScaleSCenter!!.y),
					px(35).toFloat(),
					debugLinePaint!!,
				)
			}
			if (quickScaleVStart != null && isQuickScaling) {
				debugLinePaint!!.color = Color.CYAN
				canvas.drawCircle(quickScaleVStart!!.x, quickScaleVStart!!.y, px(30).toFloat(), debugLinePaint!!)
			}
			debugLinePaint!!.color = Color.MAGENTA
		}
	}

	/**
	 * Use canvas max bitmap width and height instead of the default 2048, to avoid redundant tiling.
	 */
	private fun getMaxBitmapDimensions(canvas: Canvas) = Point(
		minOf(canvas.maximumBitmapWidth, maxTileWidth),
		minOf(canvas.maximumBitmapHeight, maxTileHeight),
	)

	/**
	 * Called on first draw when the view has dimensions. Calculates the initial sample size and starts async loading of
	 * the base layer image - the whole source subsampled as necessary.
	 */
	@Synchronized
	private fun initialiseBaseLayer(maxTileDimensions: Point) {
		satTemp = ScaleAndTranslate(0f, PointF(0f, 0f))
		fitToBounds(true, satTemp!!)

		// Load double resolution - next level will be split into four tiles and at the center all four are required,
		// so don't bother with tiling until the next level 16 tiles are needed.
		fullImageSampleSize = calculateInSampleSize(satTemp!!.scale)
		if (fullImageSampleSize > 1) {
			fullImageSampleSize /= 2
		}
		if (fullImageSampleSize == 1 && sRegion == null && sWidth() < maxTileDimensions.x && sHeight() < maxTileDimensions.y) {
			// Whole image is required at native resolution, and is smaller than the canvas max bitmap size.
			// Use BitmapDecoder for better image support.
			decoder?.recycle()
			decoder = null
			loadBitmap(uri!!, false)
		} else {
			initialiseTileMap(maxTileDimensions)
			val baseGrid: List<Tile>? = tileMap!![fullImageSampleSize]
			if (baseGrid != null) {
				for (baseTile in baseGrid) {
					loadTile(decoder!!, baseTile)
				}
			}
			refreshRequiredTiles(load = true)
		}
	}

	/**
	 * Handle touch events. One finger pans, and two finger pinch and zoom plus panning.
	 */
	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(event: MotionEvent): Boolean {
		// During non-interruptible anims, ignore all touch events
		anim = if (anim != null && !anim!!.interruptible) {
			requestDisallowInterceptTouchEvent(true)
			return true
		} else {
			try {
				anim?.listener?.onInterruptedByUser()
			} catch (e: Exception) {
				Log.w(TAG, "Error thrown by animation listener", e)
			}
			null
		}

		// Abort if not ready
		if (vTranslate == null) {
			singleDetector?.onTouchEvent(event)
			return true
		}
		// Detect flings, taps and double taps
		if (!isQuickScaling && detector?.onTouchEvent(event) != false) {
			isZooming = false
			isPanning = false
			touchEventDelegate.reset()
			return true
		}
		if (vTranslateStart == null) {
			vTranslateStart = PointF(0f, 0f)
		}
		if (vTranslateBefore == null) {
			vTranslateBefore = PointF(0f, 0f)
		}
		if (vCenterStart == null) {
			vCenterStart = PointF(0f, 0f)
		}

		// Store current values, so we can send an event if they change
		val scaleBefore = scale
		vTranslateBefore!!.set(vTranslate!!)
		val handled = touchEventDelegate.dispatchTouchEvent(event)
		sendStateChanged(scaleBefore, checkNotNull(vTranslateBefore), ORIGIN_TOUCH)
		return handled || super.onTouchEvent(event)
	}

	@JvmSynthetic
	internal fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
		parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
	}

	@JvmOverloads
	@CheckResult
	public fun getCenter(outPoint: PointF = PointF()): PointF? {
		val mX = width / 2
		val mY = height / 2
		return viewToSourceCoord(mX.toFloat(), mY.toFloat(), outPoint)
	}

	public fun viewToSourceCoord(vxy: PointF): PointF? {
		return viewToSourceCoord(vxy.x, vxy.y, PointF())
	}

	public fun viewToSourceCoord(vx: Float, vy: Float): PointF? {
		return viewToSourceCoord(vx, vy, PointF())
	}

	/**
	 * Once source image and view dimensions are known, creates a map of sample size to tile grid.
	 */
	private fun initialiseTileMap(maxTileDimensions: Point) {
		tileMap?.recycleAll()
		tileMap = TileMap()
		var sampleSize = fullImageSampleSize
		var xTiles = 1
		var yTiles = 1
		while (true) {
			var sTileWidth = sWidth() / xTiles
			var sTileHeight = sHeight() / yTiles
			var subTileWidth = sTileWidth / sampleSize
			var subTileHeight = sTileHeight / sampleSize
			while (subTileWidth + xTiles + 1 > maxTileDimensions.x ||
				subTileWidth > width * 1.25 && sampleSize < fullImageSampleSize
			) {
				xTiles += 1
				sTileWidth = sWidth() / xTiles
				subTileWidth = sTileWidth / sampleSize
			}
			while (subTileHeight + yTiles + 1 > maxTileDimensions.y ||
				subTileHeight > height * 1.25 && sampleSize < fullImageSampleSize
			) {
				yTiles += 1
				sTileHeight = sHeight() / yTiles
				subTileHeight = sTileHeight / sampleSize
			}
			val tileGrid = ArrayList<Tile>(xTiles * yTiles)
			for (x in 0 until xTiles) {
				for (y in 0 until yTiles) {
					val tile = Tile()
					tile.sampleSize = sampleSize
					tile.isVisible = sampleSize == fullImageSampleSize
					tile.sRect.set(
						x * sTileWidth,
						y * sTileHeight,
						if (x == xTiles - 1) sWidth() else (x + 1) * sTileWidth,
						if (y == yTiles - 1) sHeight() else (y + 1) * sTileHeight,
					)
					tile.vRect.set(0, 0, 0, 0)
					tile.fileSRect.set(tile.sRect)
					tileGrid.add(tile)
				}
			}
			checkNotNull(tileMap)[sampleSize] = tileGrid
			sampleSize /= if (sampleSize == 1) {
				break
			} else {
				2
			}
		}
	}

	/**
	 * Convert screen coordinate to source coordinate.
	 * @param vx view X coordinate.
	 * @param vy view Y coordinate.
	 * @param sTarget target object for result. The same instance is also returned.
	 * @return source coordinates. This is the same instance passed to the sTarget param.
	 */
	public fun viewToSourceCoord(vx: Float, vy: Float, sTarget: PointF): PointF? {
		if (vTranslate == null) {
			return null
		}
		sTarget[viewToSourceX(vx)] = viewToSourceY(vy)
		return sTarget
	}

	/**
	 * Convert source to view x coordinate.
	 */
	private fun sourceToViewX(sx: Float): Float {
		return sx * scale + (vTranslate ?: return Float.NaN).x
	}

	/**
	 * Convert source to view y coordinate.
	 */
	private fun sourceToViewY(sy: Float): Float {
		return sy * scale + (vTranslate ?: return Float.NaN).y
	}

	/**
	 * Convert source coordinate to view coordinate.
	 * @param sxy source coordinates to convert.
	 * @return view coordinates.
	 */
	public fun sourceToViewCoord(sxy: PointF): PointF? {
		return sourceToViewCoord(sxy.x, sxy.y, PointF())
	}

	/**
	 * Convert source coordinate to view coordinate.
	 * @param sx source X coordinate.
	 * @param sy source Y coordinate.
	 * @return view coordinates.
	 */
	public fun sourceToViewCoord(sx: Float, sy: Float): PointF? {
		return sourceToViewCoord(sx, sy, PointF())
	}

	/**
	 * Convert source coordinate to view coordinate.
	 * @param sxy source coordinates to convert.
	 * @param vTarget target object for result. The same instance is also returned.
	 * @return view coordinates. This is the same instance passed to the vTarget param.
	 */
	@CheckResult
	public fun sourceToViewCoord(sxy: PointF, vTarget: PointF): PointF? {
		return sourceToViewCoord(sxy.x, sxy.y, vTarget)
	}

	/**
	 * Convert source coordinate to view coordinate.
	 * @param sx source X coordinate.
	 * @param sy source Y coordinate.
	 * @param vTarget target object for result. The same instance is also returned.
	 * @return view coordinates. This is the same instance passed to the vTarget param.
	 */
	@CheckResult
	public fun sourceToViewCoord(sx: Float, sy: Float, vTarget: PointF): PointF? {
		if (vTranslate == null) {
			return null
		}
		vTarget[sourceToViewX(sx)] = sourceToViewY(sy)
		return vTarget
	}

	/**
	 * Convert source rect to screen rect, integer values.
	 */
	private fun sourceToViewRect(sRect: Rect, vTarget: Rect) {
		vTarget[
			sourceToViewX(sRect.left.toFloat()).toInt(), sourceToViewY(sRect.top.toFloat()).toInt(),
			sourceToViewX(
				sRect.right.toFloat(),
			).toInt(),
		] = sourceToViewY(sRect.bottom.toFloat()).toInt()
	}

	/**
	 * Convert screen to source x coordinate.
	 */
	private fun viewToSourceX(vx: Float): Float {
		return (vx - (vTranslate ?: return Float.NaN).x) / scale
	}

	/**
	 * Convert screen to source y coordinate.
	 */
	private fun viewToSourceY(vy: Float): Float {
		return (vy - (vTranslate ?: return Float.NaN).y) / scale
	}

	/**
	 * Cancel all loading tasks when [owner] is destroyed.
	 * Warning: after the [owner] is destroyed [SubsamplingScaleImageView] becomes unusable
	 */
	public fun bindToLifecycle(owner: LifecycleOwner) {
		owner.lifecycle.addObserver(ClearingLifecycleObserver(this))
	}

	internal fun cancelCoroutineScope() {
		coroutineScope.cancel()
	}

	private fun reset(isNewImage: Boolean) {
		scale = 0f
		scaleStart = 0f
		vTranslate = null
		vTranslateStart = null
		vTranslateBefore = null
		pendingScale = 0f
		sPendingCenter = null
		sRequestedCenter = null
		isZooming = false
		isPanning = false
		isQuickScaling = false
		touchEventDelegate.reset()
		fullImageSampleSize = 0
		vCenterStart = null
		vDistStart = 0f
		quickScaleLastDistance = 0f
		quickScaleMoved = false
		quickScaleSCenter = null
		quickScaleVLastPoint = null
		quickScaleVStart = null
		anim = null
		satTemp = null
		matrix2 = null
		sRect = null
		if (isNewImage) {
			coroutineScope.coroutineContext[Job]?.cancelChildren()
			uri = null
			decoderLock.writeLock().lock()
			try {
				decoder?.recycle()
				decoder = null
			} finally {
				decoderLock.writeLock().unlock()
			}
			bitmap?.let {
				if (!bitmapIsCached) {
					it.recycle()
				} else {
					onImageEventListeners.onPreviewReleased()
				}
			}
			sWidth = 0
			sHeight = 0
			sOrientation = 0
			sRegion = null
			pRegion = null
			isReadySent = false
			imageLoadedSent = false
			bitmap = null
			bitmapIsPreview = false
			bitmapIsCached = false
		}
		tileMap?.recycleAll()
		tileMap = null
		setGestureDetector(context)
	}

	private fun restoreState(state: ImageViewState) {
		if (state.orientation in VALID_ORIENTATIONS) {
			pendingState = null
			orientation = state.orientation
			pendingScale = state.scale
			sPendingCenter = state.center
			invalidate()
		}
	}

	/**
	 * Loads the optimum tiles for display at the current scale and translate, so the screen can be filled with tiles
	 * that are at least as high resolution as the screen. Frees up bitmaps that are now off the screen.
	 * @param load Whether to load the new tiles needed. Use false while scrolling/panning for performance.
	 */
	@JvmSynthetic
	internal fun refreshRequiredTiles(load: Boolean) {
		if (decoder == null) {
			return
		}
		val tiles = tileMap?.values ?: return
		val sampleSize = minOf(fullImageSampleSize, calculateInSampleSize(scale))

		// Load tiles of the correct sample size that are on screen. Discard tiles off-screen, and those that are higher
		// resolution than required, or lower res than required but not the base layer, so the base layer is always present.
		for (value in tiles) {
			for (tile in value) {
				val isTileOutdated = !tile.isValid
				if (tile.sampleSize < sampleSize || tile.sampleSize > sampleSize && tile.sampleSize != fullImageSampleSize) {
					tile.recycle()
				}
				if (tile.sampleSize == sampleSize) {
					if (tileVisible(tile)) {
						tile.isVisible = true
						if (!tile.isLoading && (isTileOutdated || tile.bitmap == null) && load) {
							loadTile(decoder!!, tile)
						}
					} else if (tile.sampleSize != fullImageSampleSize) {
						tile.recycle()
					}
				} else if (tile.sampleSize == fullImageSampleSize) {
					tile.isVisible = true
				}
			}
		}
	}

	private fun invalidateTiles() {
		tileMap?.invalidateAll()
		decoder?.let { _ ->
			_downSampling = downSampling
			refreshRequiredTiles(load = true)
			onDownSamplingChanged()
		} ?: uri?.let {
			loadBitmap(it, preview = false)
		} ?: run {
			_downSampling = downSampling
			onDownSamplingChanged()
		}
	}

	/**
	 * Determine whether tile is visible.
	 */
	private fun tileVisible(tile: Tile): Boolean {
		val sVisLeft = viewToSourceX(0f)
		val sVisRight = viewToSourceX(width.toFloat())
		val sVisTop = viewToSourceY(0f)
		val sVisBottom = viewToSourceY(height.toFloat())
		val sRect = tile.sRect
		return !(sVisLeft > sRect.right || sRect.left > sVisRight || sVisTop > sRect.bottom || sRect.top > sVisBottom)
	}

	/**
	 * Calculates sample size to fit the source image in given bounds.
	 */
	private fun calculateInSampleSize(scale: Float): Int {
		@Suppress("NAME_SHADOWING")
		var scale = scale * externalScale
		if (minimumTileDpi > 0) {
			val metrics = resources.displayMetrics
			val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
			scale *= minimumTileDpi / averageDpi
		}
		val reqWidth = (sWidth() * scale).toInt()
		val reqHeight = (sHeight() * scale).toInt()

		// Raw height and width of image
		var inSampleSize = 1
		if (reqWidth == 0 || reqHeight == 0) {
			return 32
		}
		if (sHeight() > reqHeight || sWidth() > reqWidth) {
			// Calculate ratios of height and width to requested height and width
			val heightRatio = (sHeight().toFloat() / reqHeight.toFloat()).roundToInt()
			val widthRatio = (sWidth().toFloat() / reqWidth.toFloat()).roundToInt()

			// Choose the smallest ratio as inSampleSize value, this will guarantee
			// a final image with both dimensions larger than or equal to the
			// requested height and width.
			inSampleSize = min(heightRatio, widthRatio)
		}

		// We want the actual sample size that will be used, so round down to the nearest power of 2.
		var power = 1
		while (power * 2 < inSampleSize) {
			power *= 2
		}
		return power
	}

	@JvmSynthetic
	internal fun minScale(): Float {
		val vPadding = paddingBottom + paddingTop
		val hPadding = paddingLeft + paddingRight
		return when {
			minimumScaleType == SCALE_TYPE_CENTER_CROP || minimumScaleType == SCALE_TYPE_START -> {
				maxOf((width - hPadding) / sWidth().toFloat(), (height - vPadding) / sHeight().toFloat())
			}

			minimumScaleType == SCALE_TYPE_CUSTOM && _minScale > 0 -> {
				_minScale
			}

			else -> {
				minOf((width - hPadding) / sWidth().toFloat(), (height - vPadding) / sHeight().toFloat())
			}
		}
	}

	@JvmSynthetic
	internal fun sWidth(): Int {
		val rotation: Int = getRequiredRotation()
		return if (rotation == 90 || rotation == 270) {
			sHeight
		} else {
			sWidth
		}
	}

	/**
	 * Get source height taking rotation into account.
	 */
	@JvmSynthetic
	internal fun sHeight(): Int {
		val rotation: Int = getRequiredRotation()
		return if (rotation == 90 || rotation == 270) {
			sWidth
		} else {
			sHeight
		}
	}

	@AnyThread
	private fun getRequiredRotation(): Int {
		return if (orientation == ORIENTATION_USE_EXIF) {
			sOrientation
		} else {
			orientation
		}
	}

	/**
	 * Called by worker task when a tile has loaded. Redraws the view.
	 */
	@Synchronized
	private fun onTileLoaded() {
		checkReady()
		checkImageLoaded()
		if (isBaseLayerReady() && bitmap != null) {
			if (!bitmapIsCached) {
				bitmap?.recycle()
			}
			bitmap = null
			if (bitmapIsCached) {
				onImageEventListeners.onPreviewReleased()
			}
			bitmapIsPreview = false
			bitmapIsCached = false
		}
		invalidate()
	}

	private fun loadBitmap(source: Uri, preview: Boolean) {
		coroutineScope.launch {
			try {
				val bitmap = async {
					runInterruptible(backgroundDispatcher) {
						bitmapDecoderFactory.make().decode(context, source, downSampling)
					}
				}
				val orientation = async {
					runInterruptible(backgroundDispatcher) {
						getExifOrientation(context, source)
					}
				}
				if (preview) {
					onPreviewLoaded(bitmap.await())
				} else {
					onImageLoaded(bitmap.await(), orientation.await(), false)
				}
			} catch (e: CancellationException) {
				throw e
			} catch (error: Throwable) {
				Log.e(TAG, "Failed to load bitmap", error)
				if (preview) {
					onImageEventListeners.onPreviewLoadError(error)
				} else {
					onImageEventListeners.onImageLoadError(error)
				}
			}
		}
	}

	private fun initTiles(decoderFactory: DecoderFactory<out ImageRegionDecoder>, source: Uri) {
		coroutineScope.launch {
			try {
				val exifOrientation = async {
					runInterruptible(backgroundDispatcher) {
						getExifOrientation(context, source)
					}
				}
				val (w, h) = runInterruptible(backgroundDispatcher) {
					decoder = decoderFactory.make()
					val dimensions = checkNotNull(decoder).init(context, source)
					var sWidth = dimensions.x
					var sHeight = dimensions.y
					sRegion?.also {
						it.left = it.left.coerceAtLeast(0)
						it.top = it.top.coerceAtLeast(0)
						it.right = it.right.coerceAtMost(sWidth)
						it.bottom = it.bottom.coerceAtMost(sHeight)
						sWidth = it.width()
						sHeight = it.height()
					}
					sWidth to sHeight
				}
				onTilesInited(checkNotNull(decoder), w, h, exifOrientation.await())
			} catch (e: CancellationException) {
				throw e
			} catch (error: Throwable) {
				onImageEventListeners.onImageLoadError(error)
			}
		}
	}

	private fun loadTile(decoder: ImageRegionDecoder, tile: Tile) {
		tile.isLoading = true
		coroutineScope.launch {
			try {
				val bitmap = if (decoder.isReady && tile.isVisible) {
					runInterruptible(backgroundDispatcher) {
						decoderLock.readLock().lock()
						try {
							if (decoder.isReady) {
								// Update tile's file sRect according to rotation
								fileSRect(tile.sRect, tile.fileSRect)
								sRegion?.let {
									tile.fileSRect.offset(it.left, it.top)
								}
								decoder.decodeRegion(tile.fileSRect, tile.sampleSize * downSampling)
							} else {
								tile.isLoading = false
								null
							}
						} finally {
							decoderLock.readLock().unlock()
						}
					}
				} else {
					tile.isLoading = false
					null
				}
				tile.bitmap = bitmap
				tile.isLoading = false
				onTileLoaded()
			} catch (e: CancellationException) {
				throw e
			} catch (error: Throwable) {
				onImageEventListeners.onTileLoadError(error)
			}
		}
	}

	/**
	 * Called by worker task when decoder is ready and image size and EXIF orientation is known.
	 */
	@Synchronized
	private fun onTilesInited(decoder: ImageRegionDecoder, sWidth: Int, sHeight: Int, sOrientation: Int) {
		// If actual dimensions don't match the declared size, reset everything.
		if ((sWidth > 0) && (this.sHeight > 0) && (this.sWidth != sWidth || this.sHeight != sHeight)) {
			reset(false)
			if (!bitmapIsCached) {
				bitmap?.recycle()
			}
			bitmap = null
			if (bitmapIsCached) {
				onImageEventListeners.onPreviewReleased()
			}
			bitmapIsPreview = false
			bitmapIsCached = false
		}
		this.decoder = decoder
		this.sWidth = sWidth
		this.sHeight = sHeight
		this.sOrientation = sOrientation
		checkReady()
		if (!checkImageLoaded() && maxTileWidth > 0 && maxTileWidth != TILE_SIZE_AUTO &&
			maxTileHeight > 0 && maxTileHeight != TILE_SIZE_AUTO && width > 0 && height > 0
		) {
			initialiseBaseLayer(Point(maxTileWidth, maxTileHeight))
		}
		invalidate()
		requestLayout()
	}

	/**
	 * Called by worker task when preview image is loaded.
	 */
	@Synchronized
	private fun onPreviewLoaded(previewBitmap: Bitmap) {
		if (bitmap != null || imageLoadedSent) {
			previewBitmap.recycle()
			return
		}
		bitmap = pRegion?.let {
			Bitmap.createBitmap(previewBitmap, it.left, it.top, it.width(), it.height())
		} ?: previewBitmap
		bitmapIsPreview = true
		if (checkReady()) {
			invalidate()
			requestLayout()
		}
	}

	/**
	 * Checks whether the base layer of tiles or full size bitmap is ready.
	 */
	private fun isBaseLayerReady(): Boolean {
		if (bitmap != null && !bitmapIsPreview) {
			return true
		} else if (tileMap != null) {
			tileMap?.get(fullImageSampleSize)?.forEach { tile ->
				if (tile.bitmap == null) {
					return false
				}
			} ?: return false
			return true
		}
		return false
	}

	/**
	 * Converts source rectangle from tile, which treats the image file as if it were in the correct orientation already,
	 * to the rectangle of the image that needs to be loaded.
	 */
	@AnyThread
	private fun fileSRect(sRect: Rect, target: Rect) {
		when (getRequiredRotation()) {
			ORIENTATION_0 -> target.set(sRect)
			ORIENTATION_90 -> target[sRect.top, sHeight - sRect.right, sRect.bottom] = sHeight - sRect.left
			ORIENTATION_180 -> target[sWidth - sRect.right, sHeight - sRect.bottom, sWidth - sRect.left] =
				sHeight - sRect.top

			else -> target[sWidth - sRect.bottom, sRect.left, sWidth - sRect.top] = sRect.right
		}
	}

	/**
	 * Called by worker task when full size image bitmap is ready (tiling is disabled).
	 */
	@Synchronized
	private fun onImageLoaded(bitmap: Bitmap, sOrientation: Int, bitmapIsCached: Boolean) {
		// If actual dimensions don't match the declared size, reset everything.
		if (sWidth > 0 && sHeight > 0 && (sWidth != bitmap.width * downSampling || sHeight != bitmap.height * downSampling)) {
			reset(false)
		}
		this.bitmap?.let { oldBitmap ->
			if (this.bitmapIsCached) {
				onImageEventListeners.onPreviewReleased()
			} else {
				oldBitmap.recycle()
			}
		}
		bitmapIsPreview = false
		this.bitmapIsCached = bitmapIsCached
		this.bitmap = bitmap
		val isDownsamplingChanged = _downSampling != downSampling
		_downSampling = downSampling
		sWidth = bitmap.fullWidth()
		sHeight = bitmap.fullHeight()
		this.sOrientation = sOrientation
		if (isDownsamplingChanged) {
			onDownSamplingChanged()
		}
		val ready = checkReady()
		val imageLoaded = checkImageLoaded()
		if (ready || imageLoaded) {
			invalidate()
			requestLayout()
		}
	}

	/**
	 * Check whether view and image dimensions are known and either a preview, full size image or
	 * base layer tiles are loaded. First time, send ready event to listener. The next draw will
	 * display an image.
	 */
	private fun checkReady(): Boolean {
		val ready = width > 0 && height > 0 && sWidth > 0 && sHeight > 0 && (bitmap != null || isBaseLayerReady())
		if (!isReadySent && ready) {
			preDraw()
			isReadySent = true
			onReady()
			onImageEventListeners.onReady()
		}
		return ready
	}

	/**
	 * Check whether either the full size bitmap or base layer tiles are loaded. First time, send image
	 * loaded event to listener.
	 */
	private fun checkImageLoaded(): Boolean {
		val imageLoaded: Boolean = isBaseLayerReady()
		if (!imageLoadedSent && imageLoaded) {
			preDraw()
			imageLoadedSent = true
			onImageLoaded()
			onImageEventListeners.onImageLoaded()
		}
		return imageLoaded
	}

	private fun sendStateChanged(oldScale: Float, oldVTranslate: PointF, origin: Int) {
		onStateChangedListener?.run {
			if (scale != oldScale) {
				@Suppress("DEPRECATION")
				onScaleChanged(scale, origin)
				onScaleChanged(this@SubsamplingScaleImageView, scale, origin)
			}
			if (vTranslate != oldVTranslate) {
				val center = getCenter()
				if (center != null) {
					@Suppress("DEPRECATION")
					onCenterChanged(center, origin)
					onCenterChanged(this@SubsamplingScaleImageView, center, origin)
				}
			}
		}
	}

	/**
	 * Adjusts hypothetical future scale and translate values to keep scale within the allowed range and the image on screen. Minimum scale
	 * is set so one dimension fills the view and the image is centered on the other dimension. Used to calculate what the target of an
	 * animation should be.
	 * @param center Whether the image should be centered in the dimension it's too small to fill. While animating this can be false to avoid changes in direction as bounds are reached.
	 * @param sat The scale we want and the translation we're aiming for. The values are adjusted to be valid.
	 */
	@JvmSynthetic
	internal fun fitToBounds(center: Boolean, sat: ScaleAndTranslate) {
		@Suppress("NAME_SHADOWING")
		var center = center
		if (panLimit == PAN_LIMIT_OUTSIDE && isReady) {
			center = false
		}
		val vTranslate = sat.vTranslate
		val scale: Float = limitedScale(sat.scale)
		val scaleWidth = scale * sWidth()
		val scaleHeight = scale * sHeight()
		when {
			panLimit == PAN_LIMIT_CENTER && isReady -> {
				vTranslate.x = vTranslate.x.coerceAtLeast(width / 2f - scaleWidth)
				vTranslate.y = vTranslate.y.coerceAtLeast(height / 2f - scaleHeight)
			}

			center -> {
				vTranslate.x = vTranslate.x.coerceAtLeast(width - scaleWidth)
				vTranslate.y = vTranslate.y.coerceAtLeast(height - scaleHeight)
			}

			else -> {
				vTranslate.x = vTranslate.x.coerceAtLeast(-scaleWidth)
				vTranslate.y = vTranslate.y.coerceAtLeast(-scaleHeight)
			}
		}

		// Asymmetric padding adjustments
		val xPaddingRatio =
			if (paddingLeft > 0 || paddingRight > 0) paddingLeft / (paddingLeft + paddingRight).toFloat() else 0.5f
		val yPaddingRatio =
			if (paddingTop > 0 || paddingBottom > 0) paddingTop / (paddingTop + paddingBottom).toFloat() else 0.5f
		val maxTx: Float
		val maxTy: Float
		when {
			panLimit == PAN_LIMIT_CENTER && isReady -> {
				maxTx = (width / 2f).coerceAtLeast(0f)
				maxTy = (height / 2f).coerceAtLeast(0f)
			}

			center -> {
				maxTx = ((width - scaleWidth) * xPaddingRatio).coerceAtLeast(0f)
				maxTy = ((height - scaleHeight) * yPaddingRatio).coerceAtLeast(0f)
			}

			else -> {
				maxTx = width.toFloat().coerceAtLeast(0f)
				maxTy = height.toFloat().coerceAtLeast(0f)
			}
		}
		vTranslate.x = vTranslate.x.coerceAtMost(maxTx)
		vTranslate.y = vTranslate.y.coerceAtMost(maxTy)
		sat.scale = scale
	}

	/**
	 * Adjust a requested scale to be within the allowed limits.
	 */
	@JvmSynthetic
	internal fun limitedScale(targetScale: Float): Float {
		return minOf(maxScale, maxOf(minScale(), targetScale))
	}

	/**
	 * Double tap zoom handler triggered from gesture detector or on touch, depending on whether
	 * quick scale is enabled.
	 */
	@JvmSynthetic
	internal fun doubleTapZoom(sCenter: PointF, vFocus: PointF) {
		if (!isPanEnabled) {
			sRequestedCenter?.let {
				// With a center specified from code, zoom around that point.
				sCenter.x = it.x
				sCenter.y = it.y
			} ?: run {
				// With no requested center, scale around the image center.
				sCenter.x = sWidth() / 2f
				sCenter.y = sHeight() / 2f
			}
		}
		val doubleTapZoomScale = doubleTapZoomScale.coerceAtMost(maxScale)
		val zoomIn = scale <= doubleTapZoomScale * 0.9 || scale == _minScale
		val targetScale = if (zoomIn) doubleTapZoomScale else minScale()
		when {
			doubleTapZoomStyle == ZOOM_FOCUS_CENTER_IMMEDIATE -> {
				setScaleAndCenter(targetScale, sCenter)
			}

			doubleTapZoomStyle == ZOOM_FOCUS_CENTER || !zoomIn || !isPanEnabled -> {
				AnimationBuilder(this, targetScale, sCenter).withInterruptible(false)
					.withDuration(doubleTapZoomDuration.toLong())
					.withOrigin(ORIGIN_DOUBLE_TAP_ZOOM).start()
			}

			doubleTapZoomStyle == ZOOM_FOCUS_FIXED -> {
				AnimationBuilder(this, targetScale, sCenter, vFocus).withInterruptible(false)
					.withDuration(doubleTapZoomDuration.toLong())
					.withOrigin(ORIGIN_DOUBLE_TAP_ZOOM).start()
			}
		}
		invalidate()
	}

	/**
	 * Get the translation required to place a given source coordinate at the center of the screen, with the center
	 * adjusted for asymmetric padding. Accepts the desired scale as an argument, so this is independent of current
	 * translate and scale. The result is fitted to bounds, putting the image point as near to the screen center as permitted.
	 */
	private fun vTranslateForSCenter(sCenterX: Float, sCenterY: Float, scale: Float): PointF {
		val vxCenter = paddingLeft + (width - paddingRight - paddingLeft) / 2
		val vyCenter = paddingTop + (height - paddingBottom - paddingTop) / 2
		if (satTemp == null) {
			satTemp = ScaleAndTranslate(0f, PointF(0f, 0f))
		}
		satTemp!!.scale = scale
		satTemp!!.vTranslate[vxCenter - sCenterX * scale] = vyCenter - sCenterY * scale
		fitToBounds(true, satTemp!!)
		return satTemp!!.vTranslate
	}

	/**
	 * Given a requested source center and scale, calculate what the actual center will have to be to keep the image in
	 * pan limits, keeping the requested center as near to the middle of the screen as allowed.
	 */
	@JvmSynthetic
	internal fun limitedSCenter(sCenterX: Float, sCenterY: Float, scale: Float, sTarget: PointF) =
		sTarget.also { target ->
			val vTranslate: PointF = vTranslateForSCenter(sCenterX, sCenterY, scale)
			val vxCenter = paddingLeft + (width - paddingRight - paddingLeft) / 2
			val vyCenter = paddingTop + (height - paddingBottom - paddingTop) / 2
			val sx = (vxCenter - vTranslate.x) / scale
			val sy = (vyCenter - vTranslate.y) / scale
			target.set(sx, sy)
		}

	/**
	 * Adjusts current scale and translate values to keep scale within the allowed range and the image on screen. Minimum scale
	 * is set so one dimension fills the view and the image is centered on the other dimension.
	 * @param center Whether the image should be centered in the dimension it's too small to fill. While animating this can be false to avoid changes in direction as bounds are reached.
	 */
	@JvmSynthetic
	internal fun fitToBounds(center: Boolean) {
		var init = false
		if (vTranslate == null) {
			init = true
			vTranslate = PointF(0f, 0f)
		}
		if (satTemp == null) {
			satTemp = ScaleAndTranslate(0f, PointF(0f, 0f))
		}
		satTemp!!.scale = scale
		satTemp!!.vTranslate.set(vTranslate!!)
		fitToBounds(center, satTemp!!)
		scale = satTemp!!.scale
		vTranslate!!.set(satTemp!!.vTranslate)
		if (init && minimumScaleType != SCALE_TYPE_START) {
			vTranslate!!.set(vTranslateForSCenter(sWidth() / 2f, sHeight() / 2f, scale))
		}
	}

	/**
	 * Sets scale and translate ready for the next draw.
	 */
	private fun preDraw() {
		if (width == 0 || height == 0 || sWidth <= 0 || sHeight <= 0) {
			return
		}

		// If waiting to translate to new center position, set translate now
		val pendingCenter = sPendingCenter
		if (pendingCenter != null && pendingScale != null) {
			scale = pendingScale ?: 1f
			if (vTranslate == null) {
				vTranslate = PointF()
			}
			checkNotNull(vTranslate).set(
				(width / 2f) - (scale * pendingCenter.x),
				(height / 2f) - (scale * pendingCenter.y),
			)
			sPendingCenter = null
			pendingScale = null
			fitToBounds(true)
			refreshRequiredTiles(load = true)
		}

		// On first display of base image set up position, and in other cases make sure scale is correct.
		fitToBounds(false)
	}

	/**
	 * If animating scale, calculate current scale and center with easing equations
	 */
	private fun processAnimation() {
		val animation = anim
		if (animation?.vFocusStart == null) {
			return
		}
		// Store current values, so we can send an event if they change
		val scaleBefore = scale
		if (vTranslateBefore == null) {
			vTranslateBefore = PointF(0f, 0f)
		}
		vTranslateBefore!!.set(vTranslate!!)
		var scaleElapsed = System.currentTimeMillis() - animation.time
		val finished = scaleElapsed > animation.duration
		scaleElapsed = min(scaleElapsed, animation.duration)
		scale = animation.interpolate(
			scaleElapsed,
			animation.scaleStart,
			animation.scaleEnd - animation.scaleStart,
		)

		// Apply required animation to the focal point
		val vFocusNowX: Float = animation.interpolate(
			scaleElapsed,
			animation.vFocusStart.x,
			animation.vFocusEnd.x - animation.vFocusStart.x,
		)
		val vFocusNowY: Float = animation.interpolate(
			scaleElapsed,
			animation.vFocusStart.y,
			animation.vFocusEnd.y - animation.vFocusStart.y,
		)
		// Find out where the focal point is at this scale and adjust its position to follow the animation path
		vTranslate?.run {
			x -= sourceToViewX(animation.sCenterEnd.x) - vFocusNowX
			y -= sourceToViewY(animation.sCenterEnd.y) - vFocusNowY
		}

		// For translate anims, showing the image non-centered is never allowed, for scaling anims it is during the animation.
		fitToBounds(finished || animation.scaleStart == animation.scaleEnd)
		sendStateChanged(scaleBefore, vTranslateBefore!!, animation.origin)
		refreshRequiredTiles(load = finished)
		if (finished) {
			try {
				animation.listener?.onComplete()
			} catch (e: Exception) {
				Log.w(TAG, "Error thrown by animation listener", e)
			}
			anim = null
		}
		invalidate()
	}

	/**
	 * Get the current state of the view (scale, center, orientation) for restoration after rotate. Will return null if
	 * the view is not ready.
	 * @return an [ImageViewState] instance representing the current position of the image. null if the view isn't ready.
	 */
	public fun getState(): ImageViewState? {
		return if (vTranslate != null && sWidth > 0 && sHeight > 0) {
			val center = getCenter() ?: return null
			ImageViewState(scale, center.x, center.y, orientation)
		} else null
	}

	/**
	 * Externally change the scale and translation of the source image. This may be used with getCenter() and getScale()
	 * to restore the scale and zoom after a screen rotate.
	 * @param scale New scale to set.
	 * @param sCenter New source image coordinate to center on the screen, subject to boundaries.
	 */
	public fun setScaleAndCenter(scale: Float, sCenter: PointF) {
		anim = null
		pendingScale = scale
		sPendingCenter = sCenter
		sRequestedCenter = sCenter
		invalidate()
	}

	/**
	 * Creates a scale animation builder, that when started will animate a zoom in or out. If this would move the image
	 * beyond the panning limits, the image is automatically panned during the animation.
	 * @param scale Target scale.
	 * @param sCenter Target source center.
	 * @return [AnimationBuilder] instance. Call [AnimationBuilder.start] to start the anim.
	 */
	public fun animateScaleAndCenter(scale: Float, sCenter: PointF): AnimationBuilder? {
		return if (isReady) {
			AnimationBuilder(this, scale, sCenter)
		} else {
			null
		}
	}

	/**
	 * Fully zoom out and return the image to the middle of the screen. This might be useful if you have a view pager
	 * and want images to be reset when the user has moved to another page.
	 */
	public fun resetScaleAndCenter() {
		anim = null
		pendingScale = limitedScale(0f)
		sPendingCenter = if (isReady) {
			PointF(sWidth() / 2f, sHeight() / 2f)
		} else {
			PointF(0f, 0f)
		}
		invalidate()
	}

	/**
	 * Creates Paint objects once when first needed.
	 */
	private fun createPaints() {
		if (bitmapPaint == null) {
			bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).also {
				if (colorFilter != null) {
					it.colorFilter = colorFilter
				}
			}
		}
		if ((debugTextPaint == null || debugLinePaint == null) && isDebugDrawingEnabled) {
			debugTextPaint = Paint().apply {
				textSize = px(12).toFloat()
				color = Color.MAGENTA
				style = Paint.Style.FILL
			}
			debugLinePaint = Paint().apply {
				color = Color.MAGENTA
				style = Paint.Style.STROKE
				strokeWidth = px(1).toFloat()
			}
		}
	}

	override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
		val pan = density * 64f
		return when (keyCode) {
			KeyEvent.KEYCODE_ZOOM_IN,
			KeyEvent.KEYCODE_NUMPAD_ADD,
			KeyEvent.KEYCODE_PLUS -> isZoomEnabled && scaleBy(1.4f)

			KeyEvent.KEYCODE_ZOOM_OUT,
			KeyEvent.KEYCODE_NUMPAD_SUBTRACT,
			KeyEvent.KEYCODE_MINUS -> isZoomEnabled && scaleBy(0.6f)

			KeyEvent.KEYCODE_DPAD_UP -> isPanEnabled && isScaled() && panBy(0f, -pan)
			KeyEvent.KEYCODE_DPAD_UP_RIGHT -> isPanEnabled && isScaled() && panBy(pan, -pan)
			KeyEvent.KEYCODE_DPAD_RIGHT -> isPanEnabled && isScaled() && panBy(pan, 0f)
			KeyEvent.KEYCODE_DPAD_DOWN_RIGHT -> isPanEnabled && isScaled() && panBy(pan, pan)
			KeyEvent.KEYCODE_DPAD_DOWN -> isPanEnabled && isScaled() && panBy(0f, pan)
			KeyEvent.KEYCODE_DPAD_DOWN_LEFT -> isPanEnabled && isScaled() && panBy(-pan, pan)
			KeyEvent.KEYCODE_DPAD_LEFT -> isPanEnabled && isScaled() && panBy(-pan, 0f)
			KeyEvent.KEYCODE_DPAD_UP_LEFT -> isPanEnabled && isScaled() && panBy(-pan, -pan)
			KeyEvent.KEYCODE_ESCAPE,
			KeyEvent.KEYCODE_DPAD_CENTER -> isZoomEnabled && isScaled() && scaleBy(0f)

			else -> false
		} || super.onKeyDown(keyCode, event)
	}

	override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
		return when (keyCode) {
			KeyEvent.KEYCODE_ZOOM_OUT,
			KeyEvent.KEYCODE_ZOOM_IN,
			KeyEvent.KEYCODE_NUMPAD_ADD,
			KeyEvent.KEYCODE_PLUS,
			KeyEvent.KEYCODE_NUMPAD_SUBTRACT,
			KeyEvent.KEYCODE_MINUS -> isZoomEnabled

			KeyEvent.KEYCODE_DPAD_UP,
			KeyEvent.KEYCODE_DPAD_UP_RIGHT,
			KeyEvent.KEYCODE_DPAD_RIGHT,
			KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
			KeyEvent.KEYCODE_DPAD_DOWN,
			KeyEvent.KEYCODE_DPAD_DOWN_LEFT,
			KeyEvent.KEYCODE_DPAD_LEFT,
			KeyEvent.KEYCODE_DPAD_UP_LEFT -> isPanEnabled && isScaled()

			KeyEvent.KEYCODE_ESCAPE,
			KeyEvent.KEYCODE_DPAD_CENTER -> isZoomEnabled && isScaled()

			else -> super.onKeyDown(keyCode, event)
		}
	}

	override fun onGenericMotionEvent(event: MotionEvent): Boolean {
		if (event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
			if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
				val withCtrl = event.metaState and KeyEvent.META_CTRL_MASK != 0
				if (withCtrl) {
					if (!isZoomEnabled) {
						return super.onGenericMotionEvent(event)
					}
					val center = PointF(event.x, event.y)
					val d = event.getAxisValue(MotionEvent.AXIS_VSCROLL) *
						ViewConfigurationCompat.getScaledVerticalScrollFactor(viewConfig, context)
					(animateScaleAndCenter(scale + d, center) ?: return false)
						.withInterpolator(DecelerateInterpolator())
						.withDuration(resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
						.start()
					return true
				} else if (scale > minScale) {
					if (!isPanEnabled) {
						return super.onGenericMotionEvent(event)
					}
					return panBy(
						dx = event.getAxisValue(MotionEvent.AXIS_HSCROLL) *
							ViewConfigurationCompat.getScaledHorizontalScrollFactor(viewConfig, context),
						dy = -event.getAxisValue(MotionEvent.AXIS_VSCROLL) *
							ViewConfigurationCompat.getScaledVerticalScrollFactor(viewConfig, context),
					)
				}
			}
		}
		return super.onGenericMotionEvent(event)
	}

	/**
	 * For debug overlays. Scale pixel value according to screen density.
	 */
	private fun px(px: Int): Int {
		return (density * px).toInt()
	}

	private fun isScaled() = scale > minScale

	private fun Bitmap.fullWidth() = width * _downSampling

	private fun Bitmap.fullHeight() = height * _downSampling

	/**
	 * Called once when the view is initialised, has dimensions, and will display an image on the
	 * next draw. This is triggered at the same time as
	{ @link OnImageEventListener#onReady() } but
	 * allows a subclass to receive this event without using a listener.
	 */
	protected open fun onReady(): Unit = Unit

	protected open fun onDownSamplingChanged(): Unit = Unit

	/**
	 * Called once when the full size image or its base layer tiles have been loaded.
	 */
	protected open fun onImageLoaded() {}

	public companion object {

		public const val TILE_SIZE_AUTO: Int = Integer.MAX_VALUE
		internal const val TAG = "SSIV"

		/** Attempt to use EXIF information on the image to rotate it. Works for external files only.  */
		public const val ORIENTATION_USE_EXIF: Int = -1

		/** Display the image file in its native orientation.  */
		public const val ORIENTATION_0: Int = 0

		/** Rotate the image 90 degrees clockwise.  */
		public const val ORIENTATION_90: Int = 90

		/** Rotate the image 180 degrees.  */
		public const val ORIENTATION_180: Int = 180

		/** Rotate the image 270 degrees clockwise.  */
		public const val ORIENTATION_270: Int = 270

		internal val VALID_ORIENTATIONS = intArrayOf(
			ORIENTATION_0,
			ORIENTATION_90,
			ORIENTATION_180,
			ORIENTATION_270,
			ORIENTATION_USE_EXIF,
		)

		/** During zoom animation, keep the point of the image that was tapped in the same place, and scale the image around it.  */
		public const val ZOOM_FOCUS_FIXED: Int = 1

		/** During zoom animation, move the point of the image that was tapped to the center of the screen.  */
		public const val ZOOM_FOCUS_CENTER: Int = 2

		/** Zoom in to and center the tapped point immediately without animating.  */
		public const val ZOOM_FOCUS_CENTER_IMMEDIATE: Int = 3

		private val VALID_ZOOM_STYLES = ZOOM_FOCUS_FIXED..ZOOM_FOCUS_CENTER_IMMEDIATE

		/** Quadratic ease out. Not recommended for scale animation, but good for panning.  */
		@Deprecated("Use interpolator api instead")
		public const val EASE_OUT_QUAD: Int = 1

		/** Quadratic ease in and out.  */
		@Deprecated("Use interpolator api instead")
		public const val EASE_IN_OUT_QUAD: Int = 2

		/** Don't allow the image to be panned off screen. As much of the image as possible is always displayed, centered in the view when it is smaller. This is the best option for galleries.  */
		public const val PAN_LIMIT_INSIDE: Int = 1

		/** Allows the image to be panned until it is just off screen, but no further. The edge of the image will stop when it is flush with the screen edge.  */
		public const val PAN_LIMIT_OUTSIDE: Int = 2

		/** Allows the image to be panned until a corner reaches the center of the screen but no further. Useful when you want to pan any spot on the image to the exact center of the screen.  */
		public const val PAN_LIMIT_CENTER: Int = 3

		private val VALID_PAN_LIMITS = PAN_LIMIT_INSIDE..PAN_LIMIT_CENTER

		/** Scale the image so that both dimensions of the image will be equal to or less than the corresponding dimension of the view. The image is then centered in the view. This is the default behaviour and best for galleries.  */
		public const val SCALE_TYPE_CENTER_INSIDE: Int = 1

		/** Scale the image uniformly so that both dimensions of the image will be equal to or larger than the corresponding dimension of the view. The image is then centered in the view.  */
		public const val SCALE_TYPE_CENTER_CROP: Int = 2

		/** Scale the image so that both dimensions of the image will be equal to or less than the maxScale and equal to or larger than minScale. The image is then centered in the view.  */
		public const val SCALE_TYPE_CUSTOM: Int = 3

		/** Scale the image so that both dimensions of the image will be equal to or larger than the corresponding dimension of the view. The top left is shown.  */
		public const val SCALE_TYPE_START: Int = 4

		private val VALID_SCALE_TYPES = SCALE_TYPE_CENTER_INSIDE..SCALE_TYPE_START

		/** State change originated from animation.  */
		public const val ORIGIN_ANIM: Int = 1

		/** State change originated from touch gesture.  */
		public const val ORIGIN_TOUCH: Int = 2

		/** State change originated from a fling momentum anim.  */
		public const val ORIGIN_FLING: Int = 3

		/** State change originated from a double tap zoom anim.  */
		public const val ORIGIN_DOUBLE_TAP_ZOOM: Int = 4

		public const val RESTORE_STRATEGY_NONE: Int = 0
		public const val RESTORE_STRATEGY_IMMEDIATE: Int = 1
		public const val RESTORE_STRATEGY_DEFERRED: Int = 2

		// A global preference for bitmap format, available to decoder classes that respect it
		@JvmStatic
		@Deprecated("This should be managed in decoder", level = DeprecationLevel.ERROR)
		public var preferredBitmapConfig: Bitmap.Config? = null
	}
}
