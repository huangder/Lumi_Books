package com.huangder.lumibooks.ui.privacy

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.privacy.HiddenLibraryBiometrics
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.VisualMotion.spring
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import com.huangder.lumibooks.ui.components.ConfigurableBackHandler
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.LocalReduceTransparency
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen passcode panel of the hidden library. The host only flips [visible]; the gate
 * plays its own entrance (the lock flies in from [anchorInRoot]) and exit before leaving
 * composition. [opaque] hides the page behind it from the very first frame, for re-locking
 * screens that already show hidden content.
 */
@Composable
fun HiddenLibraryGate(
    visible: Boolean,
    purpose: PasscodeGatePurpose,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    motionState: GateMotionState = rememberGateMotionState(),
    anchorInRoot: Offset? = null,
    opaque: Boolean = false,
    forHide: Boolean = false,
    viewModel: HiddenLibraryLockViewModel = hiltViewModel()
) {
    val lock by viewModel.lockState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val eInk = LocalEInkMode.current
    val motion = LocalMotionEnabled.current && !eInk
    val shakeAmplitude = with(LocalDensity.current) { 10.dp.toPx() }
    val controller = remember(viewModel) { PasscodeGateController(viewModel) }
    val auth = rememberGateSystemAuth()
    val progress = motionState.progress
    val open = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    var rendered by remember { mutableStateOf(false) }
    var entering by remember { mutableStateOf(true) }
    var finishing by remember { mutableStateOf(false) }
    val unlocked by rememberUpdatedState(onUnlocked)
    val biometricReady = remember(visible) { HiddenLibraryBiometrics.isAvailable(context) }

    LaunchedEffect(visible) {
        if (visible) {
            controller.start(purpose, viewModel.lockState.value.hasPasscode, viewModel.lockedUntil())
            open.snapTo(0f)
            finishing = false
            entering = true
            rendered = true
            progress.animateTo(1f, if (eInk) snap() else if (motion) tween(300, easing = AppEasing.Decelerate) else tween(120))
        } else if (rendered) {
            auth.cancel()
            entering = false
            progress.animateTo(0f, if (eInk) snap() else tween(if (motion) 200 else 100, easing = AppEasing.Accelerate))
            rendered = false
        }
    }
    if (!visible && !rendered) return

    fun finish() {
        if (finishing) return
        finishing = true
        auth.cancel()
        controller.markVerified()
        LockHaptics.confirm(view)
        scope.launch { open.animateTo(1f, if (motion) spring(dampingRatio = 0.55f, stiffness = 380f) else snap()) }
        scope.launch {
            delay(if (motion) 220L else 60L)
            unlocked()
        }
    }

    fun reject() {
        LockHaptics.reject(view)
        scope.launch {
            if (motion) {
                shake.snapTo(0f)
                shake.animateTo(1f, tween(420, easing = LinearEasing))
            } else {
                delay(300L)
            }
            controller.clearAfterError()
        }
    }

    val onKey: (PasscodeKey) -> Unit = { key ->
        when (key) {
            is PasscodeKey.Digit -> scope.launch {
                when (controller.type(key.value)) {
                    GateOutcome.Rejected, GateOutcome.LockedOut -> reject()
                    GateOutcome.Verified -> finish()
                    GateOutcome.Created -> if (
                        purpose == PasscodeGatePurpose.Unlock && biometricReady && !viewModel.lockState.value.biometricEnabled
                    ) {
                        controller.offerBiometric()
                    } else {
                        finish()
                    }
                    GateOutcome.Advanced, GateOutcome.None -> Unit
                }
            }
            PasscodeKey.Backspace -> controller.backspace()
            PasscodeKey.Cancel -> onDismiss()
            PasscodeKey.Biometric -> auth.biometric(onSuccess = { finish() }, onLockout = { controller.showMessage(GateMessage.Text(it)) })
        }
    }

    val showBiometricKey = purpose == PasscodeGatePurpose.Unlock && biometricReady &&
        lock.biometricEnabled && lock.hasPasscode
    // Offer biometrics right after the entrance; cancelling it leaves the keypad ready.
    LaunchedEffect(visible, showBiometricKey) {
        if (!visible || !showBiometricKey) return@LaunchedEffect
        delay(if (motion) 320L else 80L)
        if (controller.stage == GateStage.Enter && !controller.resetting) {
            auth.biometric(onSuccess = { finish() }, onLockout = { controller.showMessage(GateMessage.Text(it)) })
        }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(controller.lockedUntil) {
        while (controller.lockedOut) {
            now = System.currentTimeMillis()
            if (now >= controller.lockedUntil) controller.expireLockout() else delay(500L)
        }
    }

    val startReset = rememberDeviceCredentialCheck(
        auth = auth,
        onUnavailable = { controller.showMessage(GateMessage.ResetUnavailable) },
        onConfirmed = {
            scope.launch {
                viewModel.clearFailures()
                controller.beginReset()
            }
        }
    )

    ConfigurableBackHandler(enabled = visible) {
        if (controller.stage == GateStage.OfferBiometric) finish() else onDismiss()
    }

    val isDark = LocalIsDarkTheme.current
    val solidVeil = eInk || LocalReduceTransparency.current || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    val veilAlpha = if (solidVeil) 0.97f else if (isDark) 0.62f else 0.55f
    val veilColor = AppColors.WindowBg
    val glyphColor by animateColorAsState(
        targetValue = if (controller.tone == DotTone.Success) AppColors.Accent else AppColors.TextPrimary,
        animationSpec = tween(if (eInk) 0 else 180),
        label = "gateGlyphTint"
    )
    val accent = AppColors.Accent
    val paneTitle = stringResource(R.string.hidden_library_title)
    val ui = GateUi(
        controller = controller,
        progress = { progress.value },
        entering = { entering },
        open = { open.value },
        shake = { gateShakeOffset(shake.value, shakeAmplitude) },
        motion = motion,
        anchor = anchorInRoot,
        slot = remember { GlyphSlot() },
        // Leaves the pull indicator in its armed accent and settles to the normal ink.
        glyphTint = {
            if (motion && entering && anchorInRoot != null) {
                androidx.compose.ui.graphics.lerp(accent, glyphColor, progress.value)
            } else {
                glyphColor
            }
        },
        subtitle = gateSubtitle(controller, now, forHide),
        showBiometricKey = showBiometricKey && controller.stage == GateStage.Enter,
        showForgot = controller.stage == GateStage.Enter && lock.hasPasscode,
        onKey = onKey,
        onForgot = startReset,
        onEnableBiometric = {
            auth.biometric(
                onSuccess = {
                    viewModel.setBiometricEnabled(true)
                    finish()
                },
                onLockout = { controller.showMessage(GateMessage.Text(it)) }
            )
        },
        onSkipBiometric = { finish() }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // A re-lock covers hidden content at once and fades out after unlocking;
                // otherwise the translucent veil follows the entrance.
                val alpha = when {
                    opaque && visible -> 1f
                    opaque -> progress.value
                    else -> veilAlpha * progress.value
                }
                drawRect(veilColor, alpha = alpha.coerceIn(0f, 1f))
            }
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { this.paneTitle = paneTitle }
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        GateContent(ui)
    }
}
