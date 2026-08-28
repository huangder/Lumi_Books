package com.huangder.lumibooks.util.epub

import android.content.Context
import com.huangder.lumibooks.util.cache.BookFingerprint
import com.huangder.lumibooks.util.cache.ReaderCacheStore
import org.json.JSONArray
import org.json.JSONObject

data class CachedEpubMetadata(
    val epubPackage: EpubPackage,
    val chapterTitles: List<String>,
    val coverPath: String?
)

object EpubMetadataCache {
    fun read(context: Context, sourceLocation: String, seekablePath: String): CachedEpubMetadata? {
        val fingerprint = BookFingerprint.resolve(context, sourceLocation)
        val payload = ReaderCacheStore.get(context).readMetadata(NAMESPACE, fingerprint) ?: return null
        return runCatching { decode(payload, seekablePath) }.getOrNull()
    }

    fun write(context: Context, sourceLocation: String, metadata: CachedEpubMetadata) {
        val fingerprint = BookFingerprint.resolve(context, sourceLocation)
        ReaderCacheStore.get(context).writeMetadata(NAMESPACE, fingerprint, encode(metadata))
    }

    private fun encode(metadata: CachedEpubMetadata): JSONObject {
        val model = metadata.epubPackage
        val manifest = JSONArray()
        model.manifest.values.forEach { item ->
            manifest.put(
                JSONObject()
                    .put("id", item.id)
                    .put("href", item.href)
                    .put("fullPath", item.fullPath)
                    .put("mediaType", item.mediaType)
                    .put("properties", JSONArray(item.properties.toList()))
            )
        }
        val spine = JSONArray()
        model.spine.forEach { item ->
            spine.put(
                JSONObject()
                    .put("idRef", item.idRef)
                    .put("linear", item.linear)
                    .put("properties", JSONArray(item.properties.toList()))
                    .put("renditionLayout", item.renditionLayout.name)
            )
        }
        val navigation = JSONArray()
        model.navigation.forEach { item ->
            navigation.put(
                JSONObject().put("title", item.title).put("href", item.href).put("level", item.level)
            )
        }
        return JSONObject()
            .put("version", FORMAT_VERSION)
            .put("opfPath", model.opfPath)
            .put("basePath", model.basePath)
            .put("title", model.title)
            .put("author", model.author)
            .put("manifest", manifest)
            .put("spine", spine)
            .put("navigation", navigation)
            .put("pageProgressionDirection", model.pageProgressionDirection.name)
            .put("renditionLayout", model.renditionLayout.name)
            .put("renditionOrientation", model.renditionOrientation)
            .put("renditionSpread", model.renditionSpread)
            .put("renditionFlow", model.renditionFlow)
            .put("chapterTitles", JSONArray(metadata.chapterTitles))
            .put("coverPath", metadata.coverPath)
    }

    private fun decode(json: JSONObject, seekablePath: String): CachedEpubMetadata {
        require(json.getInt("version") == FORMAT_VERSION)
        val manifestArray = json.getJSONArray("manifest")
        val manifest = buildMap {
            for (index in 0 until manifestArray.length()) {
                val item = manifestArray.getJSONObject(index)
                val model = EpubManifestItem(
                    id = item.getString("id"),
                    href = item.getString("href"),
                    fullPath = item.getString("fullPath"),
                    mediaType = item.getString("mediaType"),
                    properties = item.getJSONArray("properties").strings().toSet()
                )
                put(model.id, model)
            }
        }
        val spineArray = json.getJSONArray("spine")
        val spine = buildList {
            for (index in 0 until spineArray.length()) {
                val item = spineArray.getJSONObject(index)
                val idRef = item.getString("idRef")
                val manifestItem = requireNotNull(manifest[idRef])
                add(
                    EpubSpineItem(
                        idRef = idRef,
                        manifestItem = manifestItem,
                        linear = item.optBoolean("linear", true),
                        properties = item.getJSONArray("properties").strings().toSet(),
                        renditionLayout = enumValueOf(item.getString("renditionLayout"))
                    )
                )
            }
        }
        val navigationArray = json.getJSONArray("navigation")
        val navigation = buildList {
            for (index in 0 until navigationArray.length()) {
                val item = navigationArray.getJSONObject(index)
                add(EpubNavigationItem(item.getString("title"), item.getString("href"), item.getInt("level")))
            }
        }
        val epubPackage = EpubPackage(
            filePath = seekablePath,
            opfPath = json.getString("opfPath"),
            basePath = json.getString("basePath"),
            title = json.getString("title"),
            author = json.getString("author"),
            manifest = manifest,
            spine = spine,
            navigation = navigation,
            pageProgressionDirection = enumValueOf(json.getString("pageProgressionDirection")),
            renditionLayout = enumValueOf(json.getString("renditionLayout")),
            renditionOrientation = json.optNullableString("renditionOrientation"),
            renditionSpread = json.optNullableString("renditionSpread"),
            renditionFlow = json.optNullableString("renditionFlow")
        )
        return CachedEpubMetadata(
            epubPackage = epubPackage,
            chapterTitles = json.getJSONArray("chapterTitles").strings(),
            coverPath = json.optNullableString("coverPath")
        )
    }

    private fun JSONArray.strings(): List<String> = buildList {
        for (index in 0 until length()) add(getString(index))
    }

    private fun JSONObject.optNullableString(key: String): String? =
        takeUnless { isNull(key) }?.optString(key)?.takeIf(String::isNotBlank)

    private const val NAMESPACE = "epub_metadata"
    private const val FORMAT_VERSION = 1
}
