package com.huangder.lumibooks.privacy

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HiddenLibraryLockState(
    val hasPasscode: Boolean = false,
    val biometricEnabled: Boolean = false,
    val failedAttempts: Int = 0,
    val lockedUntilMillis: Long = 0L
)

sealed interface PasscodeCheck {
    data object Accepted : PasscodeCheck
    data class Rejected(val attemptsBeforeLockout: Int) : PasscodeCheck
    data class LockedOut(val untilMillis: Long) : PasscodeCheck
}

/** Five free attempts, then lockouts that grow up to 15 minutes. */
internal object PasscodeLockoutPolicy {
    const val FREE_ATTEMPTS = 5
    private val lockouts = longArrayOf(30_000L, 60_000L, 5 * 60_000L, 15 * 60_000L)

    fun lockoutMillisAfter(failedAttempts: Int): Long {
        if (failedAttempts < FREE_ATTEMPTS) return 0L
        return lockouts[(failedAttempts - FREE_ATTEMPTS).coerceAtMost(lockouts.lastIndex)]
    }

    fun attemptsBeforeLockout(failedAttempts: Int): Int =
        (FREE_ATTEMPTS - failedAttempts).coerceAtLeast(0)
}

/**
 * Six-digit passcode of the hidden library. Only a salted PBKDF2 hash is stored, in its own
 * preferences file so DataStore backups never carry it. The attempt limit, not the hash cost,
 * is what protects a six-digit code; failures are written synchronously so killing the app
 * does not reset them.
 */
@Singleton
class HiddenLibraryPasscodeStore internal constructor(
    private val preferences: SharedPreferences,
    private val clock: () -> Long
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        System::currentTimeMillis
    )

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<HiddenLibraryLockState> = _state.asStateFlow()

    @Synchronized
    fun setPasscode(passcode: String) {
        require(isValidPasscode(passcode)) { "Passcode must be $PASSCODE_LENGTH digits" }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        preferences.edit()
            .putString(KEY_SALT, salt.encode())
            .putString(KEY_HASH, derive(passcode, salt, ITERATIONS).encode())
            .putInt(KEY_ITERATIONS, ITERATIONS)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKED_UNTIL, 0L)
            .commit()
        publish()
    }

    @Synchronized
    fun verify(passcode: String): PasscodeCheck {
        val now = clock()
        val lockedUntil = effectiveLockedUntil(now)
        if (lockedUntil > now) return PasscodeCheck.LockedOut(lockedUntil)
        val salt = preferences.getString(KEY_SALT, null)?.decode()
        val expected = preferences.getString(KEY_HASH, null)?.decode()
        val iterations = preferences.getInt(KEY_ITERATIONS, ITERATIONS)
        val matches = salt != null && expected != null && isValidPasscode(passcode) &&
            MessageDigest.isEqual(derive(passcode, salt, iterations), expected)
        if (matches) {
            clearFailures()
            return PasscodeCheck.Accepted
        }
        val failed = preferences.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val lockout = PasscodeLockoutPolicy.lockoutMillisAfter(failed)
        preferences.edit()
            .putInt(KEY_FAILED_ATTEMPTS, failed)
            .putLong(KEY_LOCKED_UNTIL, if (lockout > 0L) now + lockout else 0L)
            .commit()
        publish()
        return if (lockout > 0L) {
            PasscodeCheck.LockedOut(now + lockout)
        } else {
            PasscodeCheck.Rejected(PasscodeLockoutPolicy.attemptsBeforeLockout(failed))
        }
    }

    /** Also used after the system lock screen confirmed the device owner. */
    @Synchronized
    fun clearFailures() {
        preferences.edit().putInt(KEY_FAILED_ATTEMPTS, 0).putLong(KEY_LOCKED_UNTIL, 0L).commit()
        publish()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
        publish()
    }

    /** Remaining lockout end, clamped so moving the clock back cannot extend it. */
    fun lockedUntil(): Long = effectiveLockedUntil(clock())

    private fun effectiveLockedUntil(now: Long): Long {
        val stored = preferences.getLong(KEY_LOCKED_UNTIL, 0L)
        if (stored <= now) return 0L
        val longest = PasscodeLockoutPolicy.lockoutMillisAfter(preferences.getInt(KEY_FAILED_ATTEMPTS, 0))
        return minOf(stored, now + longest)
    }

    private fun publish() {
        _state.value = readState()
    }

    private fun readState() = HiddenLibraryLockState(
        hasPasscode = preferences.contains(KEY_HASH),
        biometricEnabled = preferences.getBoolean(KEY_BIOMETRIC, false),
        failedAttempts = preferences.getInt(KEY_FAILED_ATTEMPTS, 0),
        lockedUntilMillis = preferences.getLong(KEY_LOCKED_UNTIL, 0L)
    )

    private fun derive(passcode: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passcode.toCharArray(), salt, iterations, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.decode(): ByteArray? = runCatching { Base64.decode(this, Base64.NO_WRAP) }.getOrNull()

    companion object {
        const val PASSCODE_LENGTH = 6

        fun isValidPasscode(passcode: String): Boolean =
            passcode.length == PASSCODE_LENGTH && passcode.all { it in '0'..'9' }

        internal const val PREFERENCES_NAME = "hidden_library_lock"
        private const val KEY_SALT = "salt"
        private const val KEY_HASH = "hash"
        private const val KEY_ITERATIONS = "iterations"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKED_UNTIL = "locked_until"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val SALT_BYTES = 16
        private const val HASH_BITS = 256
        private const val ITERATIONS = 20_000
    }
}
