package com.huangder.lumibooks.ui.privacy

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.huangder.lumibooks.R
import com.huangder.lumibooks.privacy.HiddenLibraryBiometrics

/** System prompts used by the gate. Only one is shown at a time; leaving the gate cancels it. */
internal class GateSystemAuth(private val context: Context) {
    private var signal: CancellationSignal? = null

    fun biometric(onSuccess: () -> Unit, onLockout: (CharSequence) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        cancel()
        signal = HiddenLibraryBiometrics.authenticate(
            context = context,
            title = context.getString(R.string.biometric_prompt_title),
            negativeText = context.getString(R.string.biometric_prompt_use_passcode),
            onSuccess = onSuccess,
            onFallback = { lockout -> if (lockout != null) onLockout(lockout) }
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun deviceCredential(title: String, onSuccess: () -> Unit) {
        cancel()
        signal = HiddenLibraryBiometrics.confirmDeviceCredential(context, title, onSuccess, onCancel = {})
    }

    fun cancel() {
        signal?.cancel()
        signal = null
    }
}

@Composable
internal fun rememberGateSystemAuth(): GateSystemAuth {
    val context = LocalContext.current
    val auth = remember(context) { GateSystemAuth(context) }
    DisposableEffect(auth) { onDispose { auth.cancel() } }
    return auth
}

/**
 * "Forgot passcode": the system lock screen confirms the device owner before a new passcode
 * can be set. Without a screen lock there is nothing to confirm with, so the reset is refused.
 */
@Composable
internal fun rememberDeviceCredentialCheck(
    auth: GateSystemAuth,
    onUnavailable: () -> Unit,
    onConfirmed: () -> Unit
): () -> Unit {
    val context = LocalContext.current
    val confirmed by rememberUpdatedState(onConfirmed)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) confirmed()
    }
    return {
        val title = context.getString(R.string.passcode_reset_prompt_title)
        when {
            !HiddenLibraryBiometrics.isDeviceSecure(context) -> onUnavailable()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> auth.deviceCredential(title) { confirmed() }
            else -> {
                @Suppress("DEPRECATION")
                val intent = context.getSystemService(KeyguardManager::class.java)
                    ?.createConfirmDeviceCredentialIntent(title, null)
                if (intent == null) onUnavailable() else runCatching { launcher.launch(intent) }.onFailure { onUnavailable() }
            }
        }
    }
}
