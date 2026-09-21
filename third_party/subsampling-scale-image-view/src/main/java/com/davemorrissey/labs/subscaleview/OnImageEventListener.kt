package com.davemorrissey.labs.subscaleview

import androidx.annotation.MainThread

public interface OnImageEventListener {
	/**
	 * Called when the dimensions of the image and view are known, and either a preview image,
	 * the full size image, or base layer tiles are loaded. This indicates the scale and translate
	 * are known and the next draw will display an image. This event can be used to hide a loading
	 * graphic, or inform a subclass that it is safe to draw overlays.
	 */
	@MainThread
	public fun onReady()

	/**
	 * Called when the full size image is ready. When using tiling, this means the lowest resolution
	 * base layer of tiles are loaded, and when tiling is disabled, the image bitmap is loaded.
	 * This event could be used as a trigger to enable gestures if you wanted interaction disabled
	 * while only a preview is displayed, otherwise for most cases [.onReady] is the best
	 * event to listen to.
	 */
	@MainThread
	public fun onImageLoaded()

	/**
	 * Called when a preview image could not be loaded. This method cannot be relied upon; certain
	 * encoding types of supported image formats can result in corrupt or blank images being loaded
	 * and displayed with no detectable error. The view will continue to load the full size image.
	 * @param e The exception thrown. This error is logged by the view.
	 */
	@MainThread
	public fun onPreviewLoadError(e: Throwable)

	/**
	 * Indicates an error initiliasing the decoder when using a tiling, or when loading the full
	 * size bitmap when tiling is disabled. This method cannot be relied upon; certain encoding
	 * types of supported image formats can result in corrupt or blank images being loaded and
	 * displayed with no detectable error.
	 * @param e The exception thrown. This error is also logged by the view.
	 */
	@MainThread
	public fun onImageLoadError(e: Throwable)

	/**
	 * Called when an image tile could not be loaded. This method cannot be relied upon; certain
	 * encoding types of supported image formats can result in corrupt or blank images being loaded
	 * and displayed with no detectable error. Most cases where an unsupported file is used will
	 * result in an error caught by [.onImageLoadError].
	 * @param e The exception thrown. This error is logged by the view.
	 */
	@MainThread
	public fun onTileLoadError(e: Throwable)

	/**
	 * Called when a bitmap set using ImageSource.cachedBitmap is no longer being used by the View.
	 * This is useful if you wish to manage the bitmap after the preview is shown
	 */
	@MainThread
	public fun onPreviewReleased()
}
