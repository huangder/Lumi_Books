package com.huangder.lumibooks.tts

import android.media.AudioFormat
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SystemTtsAudioDecoderTest {
    @Test
    fun decodesPcm16WavWithOddSizedMetadataChunk() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val wav = wavFile(
            formatChunk = fmtChunk(sampleRate = 16_000, channels = 1, bits = 16),
            extraChunk = chunk("JUNK", byteArrayOf(9)),
            data = pcm
        )

        val decoded = SystemTtsAudioDecoder.decode(wav, 0, 0, 0)

        assertEquals(16_000, decoded.sampleRate)
        assertEquals(AudioFormat.ENCODING_PCM_16BIT, decoded.encoding)
        assertEquals(1, decoded.channels)
        assertArrayEquals(pcm, decoded.pcm)
    }

    @Test
    fun acceptsRawPcmWhenSynthesisMetadataIsAvailable() {
        val pcm = byteArrayOf(1, 2, 3, 4)

        val decoded = SystemTtsAudioDecoder.decode(
            bytes = pcm,
            fallbackSampleRate = 24_000,
            fallbackEncoding = AudioFormat.ENCODING_PCM_16BIT,
            fallbackChannels = 1
        )

        assertEquals(24_000, decoded.sampleRate)
        assertEquals(AudioFormat.ENCODING_PCM_16BIT, decoded.encoding)
        assertEquals(1, decoded.channels)
        assertArrayEquals(pcm, decoded.pcm)
    }

    @Test
    fun rejectsInvalidFileAndMisalignedRawPcm() {
        assertThrows(IOException::class.java) {
            SystemTtsAudioDecoder.decode(
                bytes = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0),
                fallbackSampleRate = 24_000,
                fallbackEncoding = AudioFormat.ENCODING_PCM_16BIT,
                fallbackChannels = 1
            )
        }
        assertThrows(IOException::class.java) {
            SystemTtsAudioDecoder.decode(
                bytes = byteArrayOf(1),
                fallbackSampleRate = 24_000,
                fallbackEncoding = AudioFormat.ENCODING_PCM_16BIT,
                fallbackChannels = 1
            )
        }
        assertThrows(IOException::class.java) {
            SystemTtsAudioDecoder.decode(
                bytes = byteArrayOf(1, 2, 3),
                fallbackSampleRate = 24_000,
                fallbackEncoding = AudioFormat.ENCODING_PCM_16BIT,
                fallbackChannels = 1
            )
        }
    }

    private fun fmtChunk(sampleRate: Int, channels: Int, bits: Int): ByteArray {
        val payload = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(1)
            .putShort(channels.toShort())
            .putInt(sampleRate)
            .putInt(sampleRate * channels * bits / 8)
            .putShort((channels * bits / 8).toShort())
            .putShort(bits.toShort())
            .array()
        return chunk("fmt ", payload)
    }

    private fun wavFile(formatChunk: ByteArray, extraChunk: ByteArray, data: ByteArray): ByteArray {
        val body = formatChunk + extraChunk + chunk("data", data)
        return ByteArrayOutputStream().apply {
            write("RIFF".toByteArray())
            write(leInt(body.size + 4))
            write("WAVE".toByteArray())
            write(body)
        }.toByteArray()
    }

    private fun chunk(tag: String, payload: ByteArray): ByteArray {
        val padded = if (payload.size % 2 == 0) payload else payload + byteArrayOf(0)
        return tag.toByteArray() + leInt(payload.size) + padded
    }

    private fun leInt(value: Int): ByteArray = ByteBuffer.allocate(4)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(value)
        .array()
}
