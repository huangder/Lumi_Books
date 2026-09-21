package com.davemorrissey.labs.subscaleview.decoder

import android.graphics.BitmapRegionDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/**
 * A simple pool of [BitmapRegionDecoder] instances, all loading from the same source.
 */
internal class DecoderPool {

	private val available = Semaphore(0, true)
	private val decoders = ConcurrentHashMap<BitmapRegionDecoder?, Boolean>()

	/**
	 * Returns false if there is at least one decoder in the pool.
	 */
	@get:Synchronized
	val isEmpty: Boolean
		get() = decoders.isEmpty()

	/**
	 * Returns number of encoders.
	 */
	@get:Synchronized
	val size: Int
		get() = decoders.size

	/**
	 * Acquire a decoder. Blocks until one is available.
	 */
	fun acquire(): BitmapRegionDecoder? {
		available.acquireUninterruptibly()
		return nextAvailable
	}

	/**
	 * Release a decoder back to the pool.
	 */
	fun release(decoder: BitmapRegionDecoder) {
		if (markAsUnused(decoder)) {
			available.release()
		}
	}

	/**
	 * Adds a newly created decoder to the pool, releasing an additional permit.
	 */
	@Synchronized
	fun add(decoder: BitmapRegionDecoder?) {
		decoders[decoder] = false
		available.release()
	}

	/**
	 * While there are decoders in the map, wait until each is available before acquiring,
	 * recycling and removing it. After this is called, any call to [.acquire] will
	 * block forever, so this call should happen within a write lock, and all calls to
	 * [.acquire] should be made within a read lock, so they cannot end up blocking on
	 * the semaphore when it has no permits.
	 */
	@Synchronized
	fun recycle() {
		while (decoders.isNotEmpty()) {
			val decoder = acquire() ?: continue
			decoder.recycle()
			decoders.remove(decoder)
		}
	}

	@get:Synchronized
	val nextAvailable: BitmapRegionDecoder?
		get() {
			for (entry in decoders.entries) {
				if (!entry.value) {
					entry.setValue(true)
					return entry.key
				}
			}
			return null
		}

	@Synchronized
	private fun markAsUnused(decoder: BitmapRegionDecoder): Boolean {
		for (entry in decoders.entries) {
			if (decoder == entry.key) {
				return if (entry.value) {
					entry.setValue(false)
					true
				} else {
					false
				}
			}
		}
		return false
	}
}
