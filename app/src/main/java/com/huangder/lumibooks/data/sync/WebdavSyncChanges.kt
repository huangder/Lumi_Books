package com.huangder.lumibooks.data.sync

import android.content.Context
import com.huangder.lumibooks.domain.model.WebdavConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Only successful syncs advance the baseline; failures and concurrent local edits stay dirty. */
@Singleton
class WebdavSyncChanges @Inject constructor(
    @ApplicationContext context: Context,
    private val client: WebdavClient
) {
    private val preferences = context.getSharedPreferences("webdav_change_baselines", Context.MODE_PRIVATE)

    fun key(config: WebdavConfig): String = syncFingerprint(
        "body-reconciliation-v2:" + config.normalized().copy(lastSyncTime = 0)
    )

    suspend fun unchanged(key: String, local: String, config: WebdavConfig, password: String): Boolean {
        if (preferences.getString("$key.local", null) != local) return false
        val manifest = remoteFingerprint(key, "manifest.json", config, password)
        val state = if (config.usesPortableState()) {
            remoteFingerprint(key, "state-v1/state.json", config, password)
        } else MISSING
        return preferences.getString("$key.manifest", null) == manifest &&
            preferences.getString("$key.state", null) == state
    }

    fun record(key: String, local: String, manifest: SyncManifest, state: String?) {
        preferences.edit()
            .putString("$key.local", local)
            .putString("$key.manifest", syncJsonFingerprint(manifest.toJson()))
            .putString("$key.state", state ?: MISSING)
            .apply()
    }

    private suspend fun remoteFingerprint(
        key: String, path: String, config: WebdavConfig, password: String
    ): String {
        val cacheKey = "$key/$path"
        val previousFingerprint = preferences.getString("$cacheKey.fingerprint", null)
        val cached = preferences.getString("$cacheKey.etag", null)?.takeIf { previousFingerprint != null }
            ?.let { WebdavVersionedData(ByteArray(0), it) }
        return try {
            val version = client.downloadVersioned(
                WebdavUrl.append(config.serverUrl, config.syncPath, path),
                config.username, password, cached
            )
            // A 304 returns the supplied cache object; only its validator and digest are stored.
            if (version === cached) return checkNotNull(previousFingerprint)
            val fingerprint = if (path.endsWith("state.json")) {
                webdavStateFingerprint(com.huangder.lumibooks.data.backup.PortableSnapshot.fromJson(version.data.toString(Charsets.UTF_8)))
            } else syncJsonFingerprint(version.data.toString(Charsets.UTF_8))
            preferences.edit().putString("$cacheKey.etag", version.etag)
                .putString("$cacheKey.fingerprint", fingerprint).apply()
            fingerprint
        } catch (error: WebdavException) {
            if (error.statusCode != 404) throw error
            preferences.edit().remove("$cacheKey.etag").remove("$cacheKey.fingerprint").apply()
            MISSING
        }
    }

    companion object {
        private const val MISSING = "missing"

        /**
         * A book body was rewritten in place. Size and timestamp cannot be trusted to show it
         * (SAF documents report neither), so the next automatic sync must not be skipped.
         */
        fun markLocalChanged(context: Context) {
            val preferences = context.getSharedPreferences("webdav_change_baselines", Context.MODE_PRIVATE)
            val editor = preferences.edit()
            preferences.all.keys.filter { it.endsWith(".local") }.forEach(editor::remove)
            editor.apply()
        }
    }
}

internal fun WebdavConfig.usesPortableState(): Boolean =
    syncProfileAndSettings || syncLibraryOrganization || syncReadingData

internal fun syncFingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** Sort object keys while preserving ordered arrays. */
internal fun syncJsonFingerprint(value: String): String {
    fun canonical(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().sorted().joinToString(prefix = "{", postfix = "}") {
            JSONObject.quote(it) + ":" + canonical(value.get(it))
        }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") { canonical(value.get(it)) }
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }
    val json = JSONObject(value)
    return syncFingerprint(canonical(json))
}
