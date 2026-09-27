package com.huangder.lumibooks.util.parser

import android.graphics.Bitmap

/** A lazy image exposes real pixels so filters never cache a failure placeholder. */
interface ReaderImagePixelSource {
    fun acquireReaderBitmap(): Bitmap?
}
