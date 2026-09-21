package com.huangder.lumibooks.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.huangder.lumibooks.util.cache.BookFingerprint
import com.huangder.lumibooks.util.cache.ReaderCacheStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Disposable, versioned PNGs. All commits share the reader store's clear/invalidate lock. */
internal class RasterResumeCache(
    context: Context,
    private val fingerprint: BookFingerprint,
    private val maxBookBytes: Long = 64L * 1024 * 1024,
    private val maxTotalBytes: Long = 192L * 1024 * 1024,
    private val maxBooks: Int = 3
) {
    private val store = ReaderCacheStore.get(context)
    private val generation = store.currentGeneration()
    private val root = File(context.cacheDir, "reader_cache")
    private val directory = File(root, "raster_${fingerprint.key}")
    private val lease = UUID.randomUUID().toString()
    private val leaseKey = directory.absolutePath
    private var revision = 0L
    private var window = emptyList<Int>()

    init {
        synchronized(store) {
            leases[leaseKey] = lease
            // No writer from this lease exists yet. Old incomplete encodes are disposable.
            directory.listFiles { file -> file.extension == "tmp" }?.forEach(File::delete)
        }
    }

    val enabled: Boolean get() = fingerprint.reliable

    private fun valid(): Boolean = fingerprint.reliable && leases[leaseKey] == lease &&
        runCatching { store.currentGeneration() == generation }.getOrDefault(false)

    fun setWindow(pages: List<Int>): Long = synchronized(store) {
        if (window != pages) {
            revision++
            window = pages
        }
        if (valid()) runCatching {
            val manifest = manifest() ?: newManifest()
            val entries = manifest.getJSONObject("pages")
            directory.listFiles { file -> file.extension == "png" }?.filter {
                it.nameWithoutExtension.toIntOrNull() !in pages || !entries.has(it.nameWithoutExtension)
            }?.forEach(File::delete)
            entries.keys().asSequence().toList().filter { it.toIntOrNull() !in pages }.forEach {
                File(directory, "$it.png").delete()
                entries.remove(it)
            }
            manifest.put("window", JSONArray(pages)).put("accessedAt", System.currentTimeMillis())
            commitManifest(manifest)
            trim()
        }
        revision
    }

    fun read(page: Int): Bitmap? {
        val metadata = synchronized(store) {
            if (!valid()) return null
            runCatching { manifest()?.getJSONObject("pages")?.optJSONObject(page.toString()) }.getOrNull()
        } ?: return null
        val file = File(directory, "$page.png")
        val bitmap = runCatching {
            if (file.length() != metadata.getLong("bytes")) return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            file.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth != metadata.getInt("width") || bounds.outHeight != metadata.getInt("height") ||
                bounds.outWidth.toLong() * bounds.outHeight > NORMAL_RENDER_MAX_PIXELS + 32_768L) return@runCatching null
            file.inputStream().use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        return synchronized(store) {
            if (!valid()) { bitmap?.recycle(); null } else {
                if (bitmap == null) runCatching {
                    file.delete()
                    manifest()?.let { it.getJSONObject("pages").remove(page.toString()); commitManifest(it) }
                }
                bitmap
            }
        }
    }

    fun write(page: Int, source: Bitmap, spec: RasterDecodeSpec, expectedRevision: Long) {
        val temporary = synchronized(store) {
            if (!valid() || revision != expectedRevision || page !in window) return
            val existing = runCatching { manifest()?.getJSONObject("pages")?.optJSONObject(page.toString()) }.getOrNull()
            if (existing != null && existing.optInt("width") >= spec.width &&
                existing.optInt("height") >= spec.height && File(directory, "$page.png").length() == existing.optLong("bytes")) return
            if (!directory.isDirectory && !directory.mkdirs()) return
            File(directory, "$page.${UUID.randomUUID()}.tmp")
        }
        var scaled: Bitmap? = null
        try {
            // Never upscale a preview and label it as a normal-quality resume image.
            if (source.width < spec.width || source.height < spec.height) return
            val bitmap = if (source.width == spec.width && source.height == spec.height) source else
                Bitmap.createScaledBitmap(source, spec.width, spec.height, true).also { scaled = it }
            temporary.outputStream().buffered().use {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) return
            }
            synchronized(store) {
                if (!valid() || revision != expectedRevision || page !in window || temporary.length() > maxBookBytes) return
                val manifest = manifest() ?: newManifest()
                move(temporary, File(directory, "$page.png"))
                manifest.getJSONObject("pages").put(page.toString(), JSONObject()
                    .put("width", bitmap.width).put("height", bitmap.height).put("bytes", File(directory, "$page.png").length()))
                manifest.put("window", JSONArray(window)).put("accessedAt", System.currentTimeMillis())
                commitManifest(manifest)
                trim()
            }
        } catch (error: Exception) {
            // Cache writes are optional, including low-storage and concurrent system cache eviction.
            if (com.huangder.lumibooks.BuildConfig.DEBUG) android.util.Log.d("RasterLoad", "resume_write_failed page=$page", error)
        } catch (_: OutOfMemoryError) {
            // A low-memory device may not have room for the temporary downscaled PNG buffer.
        } finally {
            temporary.delete()
            scaled?.takeIf { it !== source }?.recycle()
        }
    }

    private fun newManifest() = JSONObject().put("version", VERSION).put("identity", fingerprint.identity)
        .put("fingerprint", fingerprint.key).put("pages", JSONObject())
        .put("window", JSONArray(window)).put("accessedAt", System.currentTimeMillis())

    private fun manifest(): JSONObject? = runCatching {
        val file = File(directory, "manifest.json")
        if (!file.isFile) return@runCatching null
        JSONObject(file.readText()).takeIf {
            it.optInt("version") == VERSION && it.optString("fingerprint") == fingerprint.key &&
                it.optString("identity") == fingerprint.identity
        }
    }.getOrNull()

    private fun commitManifest(json: JSONObject) {
        if (!directory.isDirectory && !directory.mkdirs()) return
        val temporary = File(directory, "manifest.tmp")
        temporary.writeText(json.toString())
        move(temporary, File(directory, "manifest.json"))
    }

    private fun trim() {
        val books = root.listFiles { file -> file.isDirectory && file.name.startsWith("raster_") }.orEmpty()
            .sortedByDescending { dir -> runCatching { JSONObject(File(dir, "manifest.json").readText()).optLong("accessedAt") }.getOrDefault(0) }
            .toMutableList()
        fun size(dir: File) = dir.listFiles().orEmpty().sumOf { it.length() }
        var total = books.sumOf { size(it) }
        for (dir in books.toList().asReversed()) {
            if (books.size <= maxBooks && total <= maxTotalBytes) break
            if (dir == directory) continue
            total -= size(dir)
            dir.deleteRecursively()
            books.remove(dir)
        }
        val manifest = manifest() ?: return
        val entries = manifest.getJSONObject("pages")
        for (page in window.asReversed()) {
            if (size(directory) <= maxBookBytes && total <= maxTotalBytes) break
            val file = File(directory, "$page.png")
            total -= file.length()
            file.delete()
            entries.remove(page.toString())
        }
        commitManifest(manifest)
    }

    private fun move(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun close() { leases.remove(leaseKey, lease) }

    private companion object {
        const val VERSION = 1
        val leases = ConcurrentHashMap<String, String>()
    }
}
