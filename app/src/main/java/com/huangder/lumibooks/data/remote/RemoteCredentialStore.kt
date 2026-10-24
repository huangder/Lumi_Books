package com.huangder.lumibooks.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Remote library passwords, encrypted per source by a non-exportable Android Keystore key. */
@Singleton
class RemoteCredentialStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun save(sourceId: String, password: String) {
        if (password.isEmpty()) {
            clear(sourceId)
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(ivKey(sourceId), Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(valueKey(sourceId), Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun read(sourceId: String): String? {
        val iv = preferences.getString(ivKey(sourceId), null) ?: return null
        val value = preferences.getString(valueKey(sourceId), null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
                )
            }
            cipher.doFinal(Base64.decode(value, Base64.NO_WRAP)).toString(Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun clear(sourceId: String) {
        preferences.edit()
            .remove(ivKey(sourceId))
            .remove(valueKey(sourceId))
            .apply()
    }

    private fun ivKey(sourceId: String) = "$sourceId.iv"
    private fun valueKey(sourceId: String) = "$sourceId.value"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        /** Excluded from Auto Backup and device transfer in the backup rules. */
        const val PREFERENCES_NAME = "remote_library_secure"
        const val KEY_ALIAS = "lumibooks_remote_library"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
