package com.huangder.lumibooks.ui.reader

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EpubSnapshotPixelsTest {
    @Test fun paddedRowsKeepEveryPixelEvenWhenTheLastPaddingIsAbsent() {
        val source = ByteBuffer.wrap(byteArrayOf(
            99, 98, // Nonzero plane buffer position.
            1, 2, 3, 4, 5, 6, 7, 8, 90, 90, 90, 90,
            9, 10, 11, 12, 13, 14, 15, 16, 90, 90, 90, 90,
            17, 18, 19, 20, 21, 22, 23, 24
        )).apply { position(2) }
        val target = ByteBuffer.allocate(24)
        copyEpubSnapshotRows(source, target, width = 2, height = 3, rowStride = 12)
        assertArrayEquals(ByteArray(24) { (it + 1).toByte() }, target.array())
        assertEquals(0, target.position())
        assertEquals(24, target.remaining())
        assertEquals(2, source.position())
        assertEquals(34, source.limit())
    }

    @Test fun reusedPackingBufferDoesNotRetainPixelsFromThePreviousFrame() {
        val target = ByteBuffer.allocate(16)
        repeat(2) { frame ->
            val value = (frame + 1).toByte()
            val source = ByteBuffer.wrap(ByteArray(24) { value })
            copyEpubSnapshotRows(source, target, width = 2, height = 2, rowStride = 12)
            assertArrayEquals(ByteArray(16) { value }, target.array())
            // Match copyPixelsFromBuffer consuming the packed frame.
            target.position(target.limit())
        }
    }
}
