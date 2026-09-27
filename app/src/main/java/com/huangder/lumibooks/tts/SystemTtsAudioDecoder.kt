package com.huangder.lumibooks.tts

import android.media.AudioFormat
import java.io.IOException

/** Decodes the file form used by engines that do not stream onAudioAvailable callbacks. */
internal data class SystemTtsPcmData(
    val sampleRate: Int,
    val encoding: Int,
    val channels: Int,
    val pcm: ByteArray
)

internal object SystemTtsAudioDecoder {
    fun decode(
        bytes: ByteArray,
        fallbackSampleRate: Int,
        fallbackEncoding: Int,
        fallbackChannels: Int
    ): SystemTtsPcmData {
        val wavAttempt = runCatching { parseWav(bytes) }
        wavAttempt.getOrNull()?.let { return it }
        if (looksLikeWav(bytes)) {
            throw IOException("Invalid WAV TTS output", wavAttempt.exceptionOrNull())
        }

        val frameBytes = frameBytes(fallbackEncoding, fallbackChannels)
        if (fallbackSampleRate <= 0 || fallbackChannels !in 1..2 || frameBytes <= 0 ||
            bytes.isEmpty() || bytes.size % frameBytes != 0
        ) {
            throw IOException("Unsupported TTS output file")
        }
        return SystemTtsPcmData(
            sampleRate = fallbackSampleRate,
            encoding = fallbackEncoding,
            channels = fallbackChannels,
            pcm = bytes
        )
    }

    private fun looksLikeWav(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray())

    fun parseWav(bytes: ByteArray): SystemTtsPcmData {
        fun requireBytes(offset: Int, count: Int) {
            if (offset < 0 || count < 0 || offset > bytes.size - count) {
                throw IOException("Invalid WAV chunk bounds")
            }
        }

        fun u16(offset: Int): Int {
            requireBytes(offset, 2)
            return (bytes[offset].toInt() and 0xff) or
                ((bytes[offset + 1].toInt() and 0xff) shl 8)
        }

        fun u32(offset: Int): Int {
            requireBytes(offset, 4)
            return (bytes[offset].toInt() and 0xff) or
                ((bytes[offset + 1].toInt() and 0xff) shl 8) or
                ((bytes[offset + 2].toInt() and 0xff) shl 16) or
                ((bytes[offset + 3].toInt() and 0xff) shl 24)
        }

        fun tag(offset: Int): String {
            requireBytes(offset, 4)
            return String(bytes, offset, 4, Charsets.US_ASCII)
        }

        if (bytes.size < 12 || tag(0) != "RIFF" || tag(8) != "WAVE") {
            throw IOException("Unsupported TTS output: expected RIFF/WAVE")
        }
        var offset = 12
        var format = 0
        var channels = 0
        var sampleRate = 0
        var bits = 0
        var dataStart = -1
        var dataLength = 0
        while (offset <= bytes.size - 8) {
            val chunkSize = u32(offset + 4)
            if (chunkSize < 0) throw IOException("Invalid WAV chunk size")
            val payload = offset + 8
            if (payload > bytes.size || chunkSize > bytes.size - payload) {
                throw IOException("Truncated WAV chunk")
            }
            when (tag(offset)) {
                "fmt " -> {
                    if (chunkSize < 16) throw IOException("Invalid WAV fmt chunk")
                    format = u16(payload)
                    channels = u16(payload + 2)
                    sampleRate = u32(payload + 4)
                    bits = u16(payload + 14)
                }
                "data" -> {
                    dataStart = payload
                    dataLength = chunkSize
                }
            }
            val step = chunkSize + (chunkSize and 1)
            if (step > bytes.size - payload) break
            offset = payload + step
        }

        val encoding = when {
            format == 1 && bits == 8 -> AudioFormat.ENCODING_PCM_8BIT
            format == 1 && bits == 16 -> AudioFormat.ENCODING_PCM_16BIT
            format == 1 && bits == 24 -> AudioFormat.ENCODING_PCM_24BIT_PACKED
            format == 1 && bits == 32 -> AudioFormat.ENCODING_PCM_32BIT
            format == 3 && bits == 32 -> AudioFormat.ENCODING_PCM_FLOAT
            else -> throw IOException("Unsupported WAV PCM format=$format bits=$bits")
        }
        if (channels !in 1..2 || sampleRate <= 0 || dataStart < 0 || dataLength <= 0) {
            throw IOException("Incomplete WAV TTS output")
        }
        val frameBytes = frameBytes(encoding, channels)
        if (frameBytes <= 0 || dataLength % frameBytes != 0) {
            throw IOException("WAV data is not frame aligned")
        }
        return SystemTtsPcmData(
            sampleRate = sampleRate,
            encoding = encoding,
            channels = channels,
            pcm = bytes.copyOfRange(dataStart, dataStart + dataLength)
        )
    }

    private fun frameBytes(encoding: Int, channels: Int): Int {
        if (channels !in 1..2) return 0
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            AudioFormat.ENCODING_PCM_FLOAT,
            AudioFormat.ENCODING_PCM_32BIT -> 4
            AudioFormat.ENCODING_PCM_16BIT -> 2
            else -> 0
        }
        return bytesPerSample * channels
    }
}
