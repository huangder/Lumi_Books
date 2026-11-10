package com.huangder.lumibooks.util.parser

import android.graphics.Bitmap

/** A lazy image exposes real pixels so filters never cache a failure placeholder. */
interface ReaderImagePixelSource {
    /** The decoded pixels, decoding them first if needed. May block; call off the main thread. */
    fun acquireReaderBitmap(): Bitmap?

    /**
     * The pixels only if they are already decoded, without decoding or waiting on shared locks,
     * so a draw on the main thread never stalls on file reads.
     */
    fun peekReaderBitmap(): Bitmap? = acquireReaderBitmap()
}
