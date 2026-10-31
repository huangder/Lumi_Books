package com.huangder.lumibooks.privacy

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi

/**
 * Thin wrapper over the platform BiometricPrompt. The app activities are not
 * FragmentActivity, so androidx.biometric is not used. Biometric unlock needs API 29+;
 * on older devices the option is hidden and the passcode is the only way in.
 */
object HiddenLibraryBiometrics {

    fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val manager = context.getSystemService(BiometricManager::class.java) ?: return false
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        } else {
            @Suppress("DEPRECATION")
            manager.canAuthenticate()
        }
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /** True when the device has a PIN, pattern or password that can confirm its owner. */
    fun isDeviceSecure(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /**
     * Shows the system biometric sheet. [onFallback] runs once when the user cancels, taps
     * "use passcode" or the sensor is locked out; [lockoutMessage] is set only in the last case.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun authenticate(
        context: Context,
        title: String,
        negativeText: String,
        onSuccess: () -> Unit,
        onFallback: (lockoutMessage: CharSequence?) -> Unit
    ): CancellationSignal {
        val executor = context.mainExecutor
        var finished = false
        fun finish(block: () -> Unit) {
            if (finished) return
            finished = true
            block()
        }
        val builder = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setConfirmationRequired(false)
            .setNegativeButton(negativeText, executor) { _, _ -> finish { onFallback(null) } }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        }
        val signal = CancellationSignal()
        builder.build().authenticate(signal, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                finish(onSuccess)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val lockout = errorCode == BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT ||
                    errorCode == BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT
                finish { onFallback(if (lockout) errString else null) }
            }
        })
        return signal
    }

    /** Confirms the device owner with the system lock screen (API 30+). */
    @RequiresApi(Build.VERSION_CODES.R)
    fun confirmDeviceCredential(
        context: Context,
        title: String,
        onSuccess: () -> Unit,
        onCancel: () -> Unit
    ): CancellationSignal {
        val executor = context.mainExecutor
        val signal = CancellationSignal()
        BiometricPrompt.Builder(context)
            .setTitle(title)
            .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
            .authenticate(signal, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onCancel()
                }
            })
        return signal
    }
}
