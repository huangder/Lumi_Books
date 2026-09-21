package com.huangder.lumibooks.util.cache

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID

data class BookFingerprint(
    val identity: String,
    val size: Long,
    val lastModified: Long,
    val reliable: Boolean
) {
    val key: String = sha256("$identity|$size|$lastModified")

    companion object {
        fun resolve(context: Context, location: String): BookFingerprint {
            val uri = runCatching { Uri.parse(location) }.getOrNull()
            if (uri != null && uri.scheme.equals("content", ignoreCase = true)) {
                val contentUri = uri
                var size = 0L
                var lastModified = 0L
                val columns = arrayOf(
                    OpenableColumns.SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                )
                runCatching {
                    context.contentResolver.query(contentUri, columns, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            val modifiedIndex = cursor.getColumnIndex(
                                DocumentsContract.Document.COLUMN_LAST_MODIFIED
                            )
                            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                            if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) {
                                lastModified = cursor.getLong(modifiedIndex)
                            }
                        }
                    }
                }
                val documentIdentity = runCatching {
                    if (DocumentsContract.isDocumentUri(context, contentUri)) {
                        "${contentUri.authority}:${DocumentsContract.getDocumentId(contentUri)}"
                    } else {
                        location
                    }
                }.getOrDefault(location)
                return BookFingerprint(
                    documentIdentity,
                    size.coerceAtLeast(0L),
                    lastModified,
                    lastModified > 0L
                )
            }
            val file = File(location)
            val canonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
            return BookFingerprint(
                identity = canonical,
                size = file.takeIf(File::isFile)?.length() ?: 0L,
                lastModified = file.takeIf(File::isFile)?.lastModified() ?: 0L,
                reliable = file.isFile
            )
        }

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

internal enum class MirrorLifetime {
    PERSISTENT,
    SESSION
}

internal class ReaderMirrorLease internal constructor(
    val file: File,
    private val release: () -> Unit = {}
) : Closeable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) release()
    }
}

/** Versioned, clearable reader cache: at most three mirrored books and 96 MiB per standard pool. */
class ReaderCacheStore private constructor(private val context: Context) {
    private data class SessionMirror(
        val fingerprint: BookFingerprint,
        val file: File,
        val metadata: File,
        var leases: Int,
        var deleteWhenReleased: Boolean = false
    )

    private val root = File(context.cacheDir, "reader_cache")
    private val processToken = "${android.os.Process.myPid()}_${UUID.randomUUID()}"
    private val processPrefix = "process_${processToken}_"
    private val processSentinel = File(root, ".process_$processToken")
    private val sessionMirrors = HashMap<String, SessionMirror>()
    private var rootInitialized = false
    private var cacheGeneration = 0L

    init {
        val directory = ensureRoot()
        cleanupStaleMirrors(directory)
    }

    /**
     * Android may clear cache directories without killing the app process. Every disk operation
     * must therefore recreate the directory instead of trusting the File captured by this singleton.
     */
    private fun ensureRoot(): File {
        val cacheStillPresent = root.isDirectory && processSentinel.isFile
        if (!root.isDirectory && !root.mkdirs() && !root.isDirectory) {
            throw IOException("Unable to create reader cache directory: ${root.absolutePath}")
        }
        if (!processSentinel.isFile) {
            if (rootInitialized && !cacheStillPresent) cacheGeneration++
            if (!processSentinel.createNewFile() && !processSentinel.isFile) {
                throw IOException("Unable to create reader cache sentinel: ${processSentinel.absolutePath}")
            }
        }
        rootInitialized = true
        return root
    }

    /** Returns a process-local generation that advances whenever the cache is cleared. */
    @Synchronized
    fun currentGeneration(): Long {
        ensureRoot()
        return cacheGeneration
    }

    @Synchronized
    internal fun acquireContentUriMirror(
        location: String,
        lifetime: MirrorLifetime
    ): ReaderMirrorLease? = when (lifetime) {
        MirrorLifetime.PERSISTENT -> mirrorContentUri(location)?.let(::ReaderMirrorLease)
        MirrorLifetime.SESSION -> acquireSessionMirror(location)
    }

    private fun mirrorContentUri(location: String): File? = runCatching {
        val directory = ensureRoot()
        val fingerprint = BookFingerprint.resolve(context, location)
        if (fingerprint.size > MAX_SINGLE_BYTES) return@runCatching null
        val prefix = if (fingerprint.reliable) MIRROR_PREFIX else processPrefix
        val target = File(directory, "$prefix${fingerprint.key}.book")
        val metadata = File(directory, "$prefix${fingerprint.key}.json")
        if (target.isFile && metadataMatches(metadata, fingerprint, target.length())) {
            writeMetadata(metadata, fingerprint, target.length(), System.currentTimeMillis())
            trim(excludeKey = fingerprint.key)
            return@runCatching target.takeIf(File::isFile)
        }

        target.delete()
        metadata.delete()
        val temporary = File(root, target.name + ".tmp")
        try {
            val uri = Uri.parse(location)
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().buffered().use(input::copyTo)
            } ?: return@runCatching null
            moveAtomically(temporary, target)
            writeMetadata(metadata, fingerprint, target.length(), System.currentTimeMillis())
            trim(excludeKey = fingerprint.key)
            target
        } catch (_: Throwable) {
            temporary.delete()
            target.delete()
            metadata.delete()
            null
        }
    }.getOrNull()

    private fun acquireSessionMirror(location: String): ReaderMirrorLease {
        val directory = ensureRoot()
        val fingerprint = BookFingerprint.resolve(context, location)
        sessionMirrors[fingerprint.key]?.let { active ->
            if (active.file.isFile && metadataMatches(active.metadata, fingerprint, active.file.length())) {
                active.leases++
                return leaseFor(fingerprint.key, active)
            }
            active.file.delete()
            active.metadata.delete()
            sessionMirrors.remove(fingerprint.key)
        }

        val prefix = "${processPrefix}comic_"
        val target = File(directory, "$prefix${fingerprint.key}.book")
        val metadata = File(directory, "$prefix${fingerprint.key}.json")
        val temporary = File(directory, target.name + ".tmp")
        if (target.isFile && metadataMatches(metadata, fingerprint, target.length())) {
            val active = SessionMirror(fingerprint, target, metadata, leases = 1)
            sessionMirrors[fingerprint.key] = active
            return leaseFor(fingerprint.key, active)
        }

        target.delete()
        metadata.delete()
        temporary.delete()
        ensureSessionMirrorSpace(directory, fingerprint.size)
        try {
            val uri = Uri.parse(location)
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().buffered().use(input::copyTo)
            } ?: throw IOException("Unable to open CBZ document: $uri")
            if (fingerprint.size > 0L && temporary.length() != fingerprint.size) {
                throw IOException(
                    "Incomplete CBZ mirror: expected ${fingerprint.size} bytes, copied ${temporary.length()}"
                )
            }
            moveAtomically(temporary, target)
            writeMetadata(metadata, fingerprint, target.length(), System.currentTimeMillis())
            val active = SessionMirror(fingerprint, target, metadata, leases = 1)
            sessionMirrors[fingerprint.key] = active
            return leaseFor(fingerprint.key, active)
        } catch (error: Throwable) {
            temporary.delete()
            target.delete()
            metadata.delete()
            throw IOException("Unable to prepare temporary CBZ mirror", error)
        }
    }

    private fun leaseFor(key: String, mirror: SessionMirror): ReaderMirrorLease =
        ReaderMirrorLease(mirror.file) { releaseSessionMirror(key) }

    @Synchronized
    private fun releaseSessionMirror(key: String) {
        val mirror = sessionMirrors[key] ?: return
        mirror.leases = (mirror.leases - 1).coerceAtLeast(0)
        if (mirror.leases == 0) {
            mirror.file.delete()
            mirror.metadata.delete()
            File(mirror.file.parentFile, mirror.file.name + ".tmp").delete()
            sessionMirrors.remove(key)
        }
    }

    private fun ensureSessionMirrorSpace(directory: File, sourceBytes: Long) {
        if (sourceBytes <= 0L) return
        val usable = directory.usableSpace
        if (usable > 0L && usable - sourceBytes < MIN_FREE_AFTER_SESSION_MIRROR_BYTES) {
            throw IOException("Not enough free space for temporary CBZ mirror")
        }
    }

    @Synchronized
    fun invalidate(location: String) {
        val directory = runCatching { ensureRoot() }.getOrNull() ?: return
        val identity = BookFingerprint.resolve(context, location).identity
        sessionMirrors.values.filter { it.fingerprint.identity == identity }
            .forEach { it.deleteWhenReleased = true }
        val activeFiles = activeSessionFiles()
        // In-flight raster writers must not resurrect invalidated page images.
        cacheGeneration++
        directory.listFiles { file -> file.isDirectory && file.name.startsWith("raster_") }
            ?.forEach { raster ->
                val matches = runCatching {
                    JSONObject(File(raster, "manifest.json").readText()).optString("identity") == identity
                }.getOrDefault(false)
                if (matches) raster.deleteRecursively()
            }
        directory.listFiles { file -> file.extension == "json" }?.forEach { metadata ->
            val matches = runCatching {
                JSONObject(metadata.readText()).optString("identity") == identity
            }.getOrDefault(false)
            if (matches) {
                val book = File(root, metadata.nameWithoutExtension + ".book")
                if (metadata !in activeFiles && book !in activeFiles) {
                    book.delete()
                    metadata.delete()
                }
            }
        }
    }

    @Synchronized
    fun metadataFile(namespace: String, fingerprint: BookFingerprint): File =
        File(ensureRoot(), "${namespace}_${fingerprint.key}.json")

    @Synchronized
    fun readMetadata(namespace: String, fingerprint: BookFingerprint): JSONObject? {
        if (!fingerprint.reliable) return null
        val file = runCatching { metadataFile(namespace, fingerprint) }.getOrNull() ?: return null
        if (!file.isFile) return null
        return runCatching {
            val envelope = JSONObject(file.readText())
            if (envelope.optInt("cacheVersion") != VERSION ||
                envelope.optString("identity") != fingerprint.identity ||
                envelope.optLong("sourceSize") != fingerprint.size ||
                envelope.optLong("lastModified") != fingerprint.lastModified
            ) {
                file.delete()
                null
            } else {
                envelope.optJSONObject("payload")
            }
        }.getOrElse {
            file.delete()
            null
        }
    }

    @Synchronized
    fun writeMetadata(namespace: String, fingerprint: BookFingerprint, payload: JSONObject) {
        if (!fingerprint.reliable) return
        runCatching {
            val directory = ensureRoot()
            val file = File(directory, "${namespace}_${fingerprint.key}.json")
            val envelope = JSONObject()
                .put("cacheVersion", VERSION)
                .put("identity", fingerprint.identity)
                .put("sourceSize", fingerprint.size)
                .put("lastModified", fingerprint.lastModified)
                .put("payload", payload)
            val temporary = File(directory, file.name + ".tmp")
            temporary.writeText(envelope.toString())
            moveAtomically(temporary, file)
        }
    }

    @Synchronized
    fun clear() {
        val directory = ensureRoot()
        sessionMirrors.values.forEach { it.deleteWhenReleased = true }
        val activeFiles = activeSessionFiles()
        directory.listFiles()?.forEach { file ->
            if (file != processSentinel && file !in activeFiles) file.deleteRecursively()
        }
        cacheGeneration++
    }

    private fun activeSessionFiles(): Set<File> = buildSet {
        sessionMirrors.values.forEach { mirror ->
            add(mirror.file)
            add(mirror.metadata)
            add(File(mirror.file.parentFile, mirror.file.name + ".tmp"))
        }
    }

    private fun metadataMatches(metadata: File, fingerprint: BookFingerprint, actualSize: Long): Boolean {
        if (!metadata.isFile) return false
        return runCatching {
            val json = JSONObject(metadata.readText())
            json.optInt("version") == VERSION &&
                json.optString("identity") == fingerprint.identity &&
                json.optLong("sourceSize") == fingerprint.size &&
                json.optLong("lastModified") == fingerprint.lastModified &&
                json.optLong("cachedSize") == actualSize
        }.getOrDefault(false)
    }

    private fun writeMetadata(
        file: File,
        fingerprint: BookFingerprint,
        cachedSize: Long,
        accessedAt: Long
    ) {
        val json = JSONObject()
            .put("version", VERSION)
            .put("identity", fingerprint.identity)
            .put("sourceSize", fingerprint.size)
            .put("lastModified", fingerprint.lastModified)
            .put("cachedSize", cachedSize)
            .put("accessedAt", accessedAt)
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(json.toString())
        moveAtomically(temporary, file)
    }

    private fun trim(excludeKey: String?) {
        val directory = ensureRoot()
        data class Entry(val metadata: File, val book: File, val accessedAt: Long)
        val entries = directory.listFiles { file ->
            file.extension == "json" && (
                file.name.startsWith(MIRROR_PREFIX) ||
                    (file.name.startsWith("process_") && !file.name.contains("_comic_"))
                )
        }.orEmpty().mapNotNull { metadata ->
            val json = runCatching { JSONObject(metadata.readText()) }.getOrNull() ?: return@mapNotNull null
            val book = File(root, metadata.nameWithoutExtension + ".book")
            if (!book.isFile) {
                metadata.delete()
                return@mapNotNull null
            }
            Entry(metadata, book, json.optLong("accessedAt"))
        }.sortedByDescending(Entry::accessedAt).toMutableList()

        var total = entries.sumOf { it.book.length() }
        var kept = entries.size
        entries.asReversed().forEach { entry ->
            if (kept <= MAX_BOOKS && total <= MAX_BYTES) return@forEach
            if (excludeKey != null && entry.book.name.contains(excludeKey)) return@forEach
            total -= entry.book.length()
            kept--
            entry.book.delete()
            entry.metadata.delete()
        }
    }

    @Synchronized
    internal fun enforceLimitsForTesting() {
        trim(excludeKey = null)
    }

    @Synchronized
    internal fun cleanupStaleMirrorsForTesting() {
        cleanupStaleMirrors(ensureRoot())
    }

    private fun cleanupStaleMirrors(directory: File) {
        directory.listFiles()?.forEach { file ->
            val staleProcessFile = file.name.startsWith("process_") &&
                !file.name.startsWith(processPrefix)
            val staleSentinel = file.name.startsWith(".process_") && file != processSentinel
            val legacyComicMirror = file.name.startsWith("comic_") &&
                (file.extension == "book" || file.extension == "json" || file.extension == "tmp")
            if (staleProcessFile || staleSentinel || legacyComicMirror) file.deleteRecursively()
        }
    }

    private fun moveAtomically(source: File, target: File) {
        target.parentFile?.mkdirs()
        runCatching {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        }.getOrElse {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        const val MAX_BYTES: Long = 96L * 1024L * 1024L
        const val MAX_BOOKS: Int = 3
        private const val MAX_SINGLE_BYTES: Long = 64L * 1024L * 1024L
        private const val MIN_FREE_AFTER_SESSION_MIRROR_BYTES: Long = 64L * 1024L * 1024L
        private const val MIRROR_PREFIX = "mirror_"
        private const val VERSION = 1
        private val instances = ConcurrentHashMap<String, ReaderCacheStore>()

        fun get(context: Context): ReaderCacheStore {
            val appContext = context.applicationContext
            return instances.getOrPut(appContext.cacheDir.absolutePath) { ReaderCacheStore(appContext) }
        }
    }
}

class WeightedLruCache<K, V>(
    private val maxWeight: Long,
    private val weigh: (V) -> Long
) : Map<K, V> {
    private val backingMap = object : LinkedHashMap<K, V>(8, 0.75f, true) {}
    private var weight = 0L

    @Synchronized
    override operator fun get(key: K): V? = backingMap[key]

    override val entries: Set<Map.Entry<K, V>>
        @Synchronized get() = LinkedHashMap(backingMap).entries
    override val keys: Set<K>
        @Synchronized get() = LinkedHashSet(backingMap.keys)
    override val size: Int
        @Synchronized get() = backingMap.size
    override val values: Collection<V>
        @Synchronized get() = ArrayList(backingMap.values)

    @Synchronized
    override fun containsKey(key: K): Boolean = backingMap.containsKey(key)

    @Synchronized
    override fun containsValue(value: V): Boolean = backingMap.containsValue(value)

    @Synchronized
    override fun isEmpty(): Boolean = backingMap.isEmpty()

    @Synchronized
    fun put(key: K, value: V) {
        backingMap.put(key, value)?.let { weight -= weigh(it).coerceAtLeast(0L) }
        weight += weigh(value).coerceAtLeast(0L)
        val iterator = backingMap.entries.iterator()
        while (weight > maxWeight && backingMap.size > 1 && iterator.hasNext()) {
            val eldest = iterator.next()
            weight -= weigh(eldest.value).coerceAtLeast(0L)
            iterator.remove()
        }
    }

    @Synchronized
    fun clear() {
        backingMap.clear()
        weight = 0L
    }

}
