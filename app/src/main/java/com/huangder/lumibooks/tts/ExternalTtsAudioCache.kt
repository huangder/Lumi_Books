package com.huangder.lumibooks.tts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.Closeable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bounded application-private cache for complete 24 kHz mono PCM16LE utterances.
 *
 * Cache keys contain no credential material. A file becomes visible only after the complete
 * response has been received and fsynced, so cancellation never exposes partial audio as a hit.
 */
@Singleton
class ExternalTtsAudioCache internal constructor(
    private val directory: File
) {
    private val cacheGeneration = AtomicLong(0L)
    private val activeReads = mutableMapOf<String, Int>()
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        File(context.cacheDir, DIRECTORY_NAME)
    )

    fun createKey(settings: ExternalTtsSettings, text: String): String {
        val normalized = settings.normalized()
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(
            CACHE_FORMAT_VERSION,
            normalized.protocol.key,
            normalized.baseUrl,
            normalized.model,
            normalized.voice,
            normalized.styleInstructions,
            PCM_FORMAT_ID,
            text
        ).forEach { value ->
            digest.update(value.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }
        return digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    fun contains(key: String): Boolean = cacheFile(key)?.let(::isValidPcmFile) == true

    fun frameCount(key: String): Long = cacheFile(key)
        ?.takeIf(::isValidPcmFile)
        ?.length()
        ?.div(PCM_FRAME_BYTES)
        ?: 0L

    fun sizeBytes(): Long = cacheFiles().sumOf(File::length)
    @Synchronized
    fun clear() {
        cacheGeneration.incrementAndGet()
        directory.listFiles()?.forEach { file ->
            val isActivePcm = file.extension == PCM_EXTENSION &&
                activeReads.containsKey(file.nameWithoutExtension)
            if (
                file.isFile &&
                !isActivePcm &&
                (file.extension == PCM_EXTENSION || file.extension == TEMP_EXTENSION)
            ) {
                file.delete()
            }
        }
    }

    @Synchronized
    fun trimToLimit(maxBytes: Long) {
        if (maxBytes <= 0L) {
            clear()
            return
        }
        deleteStaleTemporaryFiles()
        val entries = cacheFiles()
            .filterNot { activeReads.containsKey(it.nameWithoutExtension) }
            .sortedBy(File::lastModified)
        var totalBytes = cacheFiles().sumOf(File::length)
        for (entry in entries) {
            if (totalBytes <= maxBytes) break
            val bytes = entry.length()
            if (entry.delete()) totalBytes -= bytes
        }
    }

    internal class ReadHandle(
        private val input: FileInputStream,
        private val byteOffset: Long,
        private val onClose: () -> Unit
    ) : Closeable {
        private val collectionStarted = AtomicBoolean(false)
        private val closed = AtomicBoolean(false)

        val chunks: Flow<PcmChunk> = flow {
            check(collectionStarted.compareAndSet(false, true)) {
                "External TTS cache reads can only be collected once"
            }
            try {
                if (closed.get()) return@flow
                skipFully(byteOffset)
                val buffer = ByteArray(READ_BUFFER_BYTES)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    if (count % PCM_FRAME_BYTES != 0L) throw ExternalTtsException.InvalidAudio
                    emit(buffer.copyOf(count))
                }
            } finally {
                close()
            }
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            try {
                input.close()
            } finally {
                onClose()
            }
        }

        private fun skipFully(byteCount: Long) {
            var remaining = byteCount
            while (remaining > 0L) {
                val skipped = input.skip(remaining)
                if (skipped <= 0L) {
                    if (input.read() < 0) return
                    remaining--
                } else {
                    remaining -= skipped
                }
            }
        }
    }

    @Synchronized
    internal fun open(key: String, startFrame: Long = 0L): ReadHandle? {
        val file = cacheFile(key)?.takeIf(::isValidPcmFile) ?: return null
        val input = try {
            FileInputStream(file)
        } catch (_: java.io.IOException) {
            return null
        }
        activeReads[key] = (activeReads[key] ?: 0) + 1
        file.setLastModified(System.currentTimeMillis())
        val byteOffset = startFrame.coerceAtLeast(0L) * PCM_FRAME_BYTES
        return ReadHandle(input, byteOffset) {
            synchronized(this@ExternalTtsAudioCache) {
                val remaining = (activeReads[key] ?: 1) - 1
                if (remaining <= 0) activeReads.remove(key) else activeReads[key] = remaining
            }
        }
    }
    /** Atomically stores a complete response without exposing its bytes to a player. */
    suspend fun store(
        key: String,
        source: Flow<PcmChunk>,
        maxBytes: Long
    ): Boolean {
        writeThrough(key, source, maxBytes).collect()
        return contains(key)
    }

    private fun writeThrough(
        key: String,
        source: Flow<PcmChunk>,
        maxBytes: Long
    ): Flow<PcmChunk> = flow {
        if (!isValidKey(key)) throw IllegalArgumentException("Invalid external TTS cache key")
        directory.mkdirs()
        deleteStaleTemporaryFiles()
        val writeGeneration = cacheGeneration.get()
        val temporary = File(directory, "$key.${UUID.randomUUID()}.$TEMP_EXTENSION")
        var totalBytes = 0L
        var cacheable = maxBytes > 0L
        try {
            FileOutputStream(temporary).use { output ->
                source.collect { chunk ->
                    if (chunk.isEmpty() || chunk.size % PCM_FRAME_BYTES != 0L) {
                        throw ExternalTtsException.InvalidAudio
                    }
                    totalBytes += chunk.size
                    if (cacheable && totalBytes <= maxBytes) {
                        output.write(chunk)
                    } else {
                        cacheable = false
                    }
                    emit(chunk)
                }
                if (cacheable && totalBytes > 0L) {
                    output.fd.sync()
                }
            }
            if (
                cacheable &&
                totalBytes > 0L &&
                cacheGeneration.get() == writeGeneration
            ) {
                commit(temporary, cacheFile(key) ?: error("Invalid cache key"))
                trimToLimit(maxBytes)
            }
        } finally {
            temporary.delete()
        }
    }

    private fun commit(temporary: File, target: File) = synchronized(this) {
        if (isValidPcmFile(target)) return@synchronized
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        target.setLastModified(System.currentTimeMillis())
    }

    private fun cacheFile(key: String): File? = key
        .takeIf(::isValidKey)
        ?.let { File(directory, "$it.$PCM_EXTENSION") }

    private fun cacheFiles(): List<File> = directory.listFiles()
        ?.filter(::isValidPcmFile)
        .orEmpty()

    private fun deleteStaleTemporaryFiles() {
        val staleBefore = System.currentTimeMillis() - TEMP_FILE_MAX_AGE_MS
        directory.listFiles()
            ?.filter {
                it.isFile &&
                    it.extension == TEMP_EXTENSION &&
                    it.lastModified() < staleBefore
            }
            ?.forEach(File::delete)
    }

    private fun isValidPcmFile(file: File): Boolean = file.isFile &&
        file.extension == PCM_EXTENSION &&
        file.length() > 0L &&
        file.length() % PCM_FRAME_BYTES == 0L

    private fun isValidKey(key: String): Boolean = CACHE_KEY.matches(key)


    companion object {
        const val DIRECTORY_NAME = "external_tts_audio"
        const val PCM_FRAME_BYTES = 2L
        private const val CACHE_FORMAT_VERSION = "1"
        private const val PCM_FORMAT_ID = "pcm_s16le_24000_mono"
        private const val PCM_EXTENSION = "pcm"
        private const val TEMP_EXTENSION = "tmp"
        private const val READ_BUFFER_BYTES = 16_384
        private const val TEMP_FILE_MAX_AGE_MS = 24L * 60L * 60L * 1_000L
        private val CACHE_KEY = Regex("[0-9a-f]{64}")
    }
}
