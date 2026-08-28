package com.huangder.lumibooks.util.cache

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

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

/** Versioned, clearable reader cache: at most three mirrored books and 96 MiB. */
class ReaderCacheStore private constructor(private val context: Context) {
    private val root = File(context.cacheDir, "reader_cache").apply { mkdirs() }
    private val processPrefix = "process_${android.os.Process.myPid()}_"

    init {
        root.listFiles()?.filter { it.name.startsWith("process_") && !it.name.startsWith(processPrefix) }
            ?.forEach(File::delete)
    }

    @Synchronized
    fun mirrorContentUri(location: String): File? {
        val fingerprint = BookFingerprint.resolve(context, location)
        if (fingerprint.size > MAX_SINGLE_MIRROR_BYTES) return null
        val prefix = if (fingerprint.reliable) "mirror_" else processPrefix
        val target = File(root, "$prefix${fingerprint.key}.book")
        val metadata = File(root, "$prefix${fingerprint.key}.json")
        if (target.isFile && metadataMatches(metadata, fingerprint, target.length())) {
            writeMetadata(metadata, fingerprint, target.length(), System.currentTimeMillis())
            trim(excludeKey = fingerprint.key)
            return target
        }

        target.delete()
        metadata.delete()
        val temporary = File(root, target.name + ".tmp")
        return try {
            val uri = Uri.parse(location)
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().buffered().use(input::copyTo)
            } ?: return null
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
    }

    @Synchronized
    fun invalidate(location: String) {
        val identity = BookFingerprint.resolve(context, location).identity
        root.listFiles { file -> file.extension == "json" }?.forEach { metadata ->
            val matches = runCatching {
                JSONObject(metadata.readText()).optString("identity") == identity
            }.getOrDefault(false)
            if (matches) {
                File(root, metadata.nameWithoutExtension + ".book").delete()
                metadata.delete()
            }
        }
    }

    fun metadataFile(namespace: String, fingerprint: BookFingerprint): File =
        File(root, "${namespace}_${fingerprint.key}.json")

    @Synchronized
    fun readMetadata(namespace: String, fingerprint: BookFingerprint): JSONObject? {
        if (!fingerprint.reliable) return null
        val file = metadataFile(namespace, fingerprint)
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
        val file = metadataFile(namespace, fingerprint)
        val envelope = JSONObject()
            .put("cacheVersion", VERSION)
            .put("identity", fingerprint.identity)
            .put("sourceSize", fingerprint.size)
            .put("lastModified", fingerprint.lastModified)
            .put("payload", payload)
        val temporary = File(root, file.name + ".tmp")
        temporary.writeText(envelope.toString())
        moveAtomically(temporary, file)
    }

    @Synchronized
    fun clear() {
        root.listFiles()?.forEach(File::delete)
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
        data class Entry(val metadata: File, val book: File, val accessedAt: Long)
        val entries = root.listFiles { file ->
            file.extension == "json" && (file.name.startsWith("mirror_") || file.name.startsWith("process_"))
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

    internal fun enforceLimitsForTesting() = trim(excludeKey = null)

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
        private const val MAX_SINGLE_MIRROR_BYTES: Long = 64L * 1024L * 1024L
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
