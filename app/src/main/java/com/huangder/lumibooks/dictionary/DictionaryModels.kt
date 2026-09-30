package com.huangder.lumibooks.dictionary

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

internal fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
internal fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.strings().orEmpty()
internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }

fun normalizeDictionaryKey(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")

data class DictionaryExample(val text: String, val translation: String = "")
data class DictionarySense(
    val id: String,
    val definition: String,
    val partOfSpeech: String = "",
    val examples: List<DictionaryExample> = emptyList(),
    val related: List<String> = emptyList()
)
data class DictionaryEntry(
    val id: String,
    val headword: String,
    val aliases: List<String> = emptyList(),
    val pronunciation: String = "",
    val kind: String = "",
    val senses: List<DictionarySense>,
    val sourceUrl: String = "",
    val partiallyFiltered: Boolean = false
) {
    // Always computed from the currently filtered senses; never persisted as an unfiltered snippet.
    val summary: String get() = senses.take(2).joinToString("\n") { it.definition }

    companion object {
        fun fromJson(json: JSONObject): DictionaryEntry = DictionaryEntry(
            id = json.getString("id"), headword = json.getString("headword"),
            aliases = json.strings("aliases"), pronunciation = json.optString("pronunciation"),
            kind = json.optString("kind"), sourceUrl = json.optString("sourceUrl"),
            partiallyFiltered = json.optBoolean("partiallyFiltered"),
            senses = json.getJSONArray("senses").objects().map { sense ->
                DictionarySense(sense.getString("id"), sense.getString("definition"),
                    sense.optString("partOfSpeech"),
                    sense.optJSONArray("examples")?.objects().orEmpty().map {
                        DictionaryExample(it.getString("text"), it.optString("translation"))
                    }, sense.strings("related"))
            }
        )
    }
}

data class DictionaryDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val source: String,
    val sourceUrl: String,
    val license: String,
    val attribution: String,
    val modifications: String,
    val upstreamVersion: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val installedBytes: Long,
    val sha256: String,
    val filterVersion: Int,
    val disclaimerVersion: Int,
    val entryCount: Long = 0,
    val optional: Boolean = false
) {
    val available: Boolean get() = downloadUrl.isNotBlank() && sizeBytes > 0 && sha256.length == 64
    val confirmationKey: String get() = "$id|$version|$sha256|$disclaimerVersion"
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name)
        .put("description", description).put("version", version).put("source", source)
        .put("sourceUrl", sourceUrl).put("license", license).put("attribution", attribution)
        .put("modifications", modifications).put("upstreamVersion", upstreamVersion)
        .put("downloadUrl", downloadUrl).put("sizeBytes", sizeBytes).put("installedBytes", installedBytes)
        .put("sha256", sha256).put("filterVersion", filterVersion).put("disclaimerVersion", disclaimerVersion)
        .put("entryCount", entryCount).put("optional", optional)

    companion object {
        fun fromJson(json: JSONObject): DictionaryDescriptor {
            require(json.optInt("formatVersion", 1) == 1) { "Unsupported dictionary format" }
            val result = DictionaryDescriptor(
                json.getString("id"), json.getString("name"), json.optString("description"),
                json.getString("version"), json.getString("source"), json.getString("sourceUrl"),
                json.getString("license"), json.getString("attribution"), json.getString("modifications"),
                json.getString("upstreamVersion"), json.optString("downloadUrl"),
                json.optLong("sizeBytes"), json.optLong("installedBytes"), json.optString("sha256"),
                json.getInt("filterVersion"), json.getInt("disclaimerVersion"),
                json.optLong("entryCount"), json.optBoolean("optional")
            )
            require(result.id.matches(Regex("[a-z][a-z0-9-]{0,63}")))
            require(result.version.matches(Regex("[a-zA-Z0-9._-]{1,64}")))
            require(result.filterVersion > 0 && result.disclaimerVersion > 0)
            require(result.sizeBytes in 0..MAX_PACKAGE_BYTES && result.installedBytes in 0..MAX_INSTALLED_BYTES)
            if (result.downloadUrl.isNotEmpty()) {
                require(isDictionaryDownloadUrl(result.downloadUrl)) { "Untrusted dictionary URL" }
                require(result.sha256.matches(Regex("[a-f0-9]{64}")) && result.sizeBytes > 0 && result.installedBytes > 0)
            }
            return result
        }
        const val MAX_PACKAGE_BYTES = 512L * 1024 * 1024
        const val MAX_INSTALLED_BYTES = 2L * 1024 * 1024 * 1024
    }
}

// Catalog data may select versions, but cannot redirect downloads to arbitrary hosts/repositories.
internal fun isDictionaryDownloadUrl(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null &&
        uri.port == -1 && uri.path.startsWith("/huangder/Lumi_Books/releases/download/")
}.getOrDefault(false)

enum class DictionaryDownloadPhase { DOWNLOADING, PAUSED, INSTALLING, FAILED }
data class DictionaryDownload(val phase: DictionaryDownloadPhase, val bytes: Long = 0, val total: Long = 0,
    val error: String? = null, val canRetry: Boolean = false)
data class InstalledDictionary(val descriptor: DictionaryDescriptor, val enabled: Boolean, val order: Int,
    val directory: String)
data class DictionaryCatalogItem(val descriptor: DictionaryDescriptor, val installed: InstalledDictionary? = null,
    val download: DictionaryDownload? = null) {
    val hasUpdate: Boolean get() = installed != null && installed.descriptor.confirmationKey != descriptor.confirmationKey
}
data class DictionaryState(val items: List<DictionaryCatalogItem> = emptyList(), val refreshing: Boolean = false,
    val error: String? = null, val filterVersion: Int = 1)
data class DictionaryResult(val dictionary: DictionaryDescriptor, val entries: List<DictionaryEntry>,
    val partiallyFiltered: Boolean = entries.any { it.partiallyFiltered })
data class DictionaryLookupResult(val results: List<DictionaryResult> = emptyList(), val filtered: Boolean = false,
    val failedDictionaries: List<String> = emptyList(), val enabledCount: Int = 0, val filterVersion: Int = 1,
    val queryBlocked: Boolean = false)
