package com.davemorrissey.labs.subscaleview

public interface DefaultOnImageEventListener : OnImageEventListener {

	override fun onReady(): Unit = Unit

	override fun onImageLoaded(): Unit = Unit

	override fun onPreviewLoadError(e: Throwable): Unit = Unit

	override fun onImageLoadError(e: Throwable): Unit = Unit

	override fun onTileLoadError(e: Throwable): Unit = Unit

	override fun onPreviewReleased(): Unit = Unit
}
