package com.davemorrissey.labs.subscaleview.internal

import com.davemorrissey.labs.subscaleview.OnImageEventListener

internal class CompositeImageEventListener : OnImageEventListener {

	private var listeners: MutableList<OnImageEventListener>? = null

	override fun onReady() {
		listeners?.forEach { it.onReady() }
	}

	override fun onImageLoaded() {
		listeners?.forEach { it.onImageLoaded() }
	}

	override fun onPreviewLoadError(e: Throwable) {
		listeners?.forEach { it.onPreviewLoadError(e) }
	}

	override fun onImageLoadError(e: Throwable) {
		listeners?.forEach { it.onImageLoadError(e) }
	}

	override fun onTileLoadError(e: Throwable) {
		listeners?.forEach { it.onTileLoadError(e) }
	}

	override fun onPreviewReleased() {
		listeners?.forEach { it.onPreviewReleased() }
	}

	fun addListener(listener: OnImageEventListener) {
		listeners?.add(listener) ?: run {
			listeners = arrayListOf(listener)
		}
	}

	fun removeListener(listener: OnImageEventListener) {
		listeners?.remove(listener)
	}

	fun clearListeners() {
		listeners = null
	}
}
