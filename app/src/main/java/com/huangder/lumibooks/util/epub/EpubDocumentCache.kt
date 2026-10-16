package com.huangder.lumibooks.util.epub

/** Session-owned, thread-safe cache of converted documents, bounded by their encoded bytes. */
internal class EpubDocumentCache(private val maximumBytes: Int = 16 * 1024 * 1024) {
    private val entries = LinkedHashMap<String, EpubResource>(8, 0.75f, true)
    var sizeBytes: Int = 0
        private set

    @Synchronized
    operator fun get(key: String): EpubResource? = entries[key]

    @Synchronized
    fun put(key: String, resource: EpubResource): EpubResource {
        entries.remove(key)?.let { sizeBytes -= it.bytes.size }
        if (resource.bytes.size > maximumBytes) return resource
        while (sizeBytes > maximumBytes - resource.bytes.size && entries.isNotEmpty()) {
            val oldest = entries.entries.iterator()
            sizeBytes -= oldest.next().value.bytes.size
            oldest.remove()
        }
        entries[key] = resource
        sizeBytes += resource.bytes.size
        return resource
    }

    @Synchronized
    fun clear() {
        entries.clear()
        sizeBytes = 0
    }
}
