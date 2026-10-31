package com.huangder.lumibooks.ui.privacy

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.animation.LumiMotion
import com.huangder.lumibooks.ui.animation.VisualMotion.spring
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled

internal sealed interface PasscodeKey {
    data class Digit(val value: Char) : PasscodeKey
    data object Biometric : PasscodeKey
    data object Cancel : PasscodeKey
    data object Backspace : PasscodeKey
}

private val DigitRows = listOf("123", "456", "789")

/**
 * Phone-style keypad. It stays left-to-right in RTL locales, like the system PIN pad.
 * Rows take an extra modifier so the gate can stagger their entrance.
 */
@Composable
internal fun PasscodeKeypad(
    keySize: Dp,
    enabled: Boolean,
    showBiometric: Boolean,
    showBackspace: Boolean,
    onKey: (PasscodeKey) -> Unit,
    modifier: Modifier = Modifier,
    rowModifier: (Int) -> Modifier = { Modifier }
) {
    val columnGap = keySize * 0.32f
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(keySize * 0.2f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DigitRows.forEachIndexed { index, row ->
                Row(rowModifier(index), horizontalArrangement = Arrangement.spacedBy(columnGap)) {
                    row.forEach { PasscodeKeyButton(PasscodeKey.Digit(it), keySize, enabled, onKey) }
                }
            }
            Row(rowModifier(DigitRows.size), horizontalArrangement = Arrangement.spacedBy(columnGap)) {
                if (showBiometric) {
                    PasscodeKeyButton(PasscodeKey.Biometric, keySize, enabled = true, onKey = onKey)
                } else {
                    Spacer(Modifier.size(keySize))
                }
                PasscodeKeyButton(PasscodeKey.Digit('0'), keySize, enabled, onKey)
                if (showBackspace) {
                    PasscodeKeyButton(PasscodeKey.Backspace, keySize, enabled, onKey)
                } else {
                    PasscodeKeyButton(PasscodeKey.Cancel, keySize, enabled = true, onKey = onKey)
                }
            }
        }
    }
}

@Composable
private fun PasscodeKeyButton(key: PasscodeKey, size: Dp, enabled: Boolean, onKey: (PasscodeKey) -> Unit) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val eInk = LocalEInkMode.current
    val motion = LocalMotionEnabled.current && !eInk
    val isDark = LocalIsDarkTheme.current
    val glass = LocalAppTheme.current == "liquid_glass" && !eInk
    val ink = AppColors.TextPrimary
    val disc = key is PasscodeKey.Digit
    val restAlpha = if (!disc || eInk) 0f else if (isDark) 0.14f else 0.07f
    // Draw-phase reads: pressing only redraws the key, it does not recompose the keypad.
    val fill = animateFloatAsState(
        targetValue = if (pressed) (if (isDark) 0.30f else 0.16f) else restAlpha,
        animationSpec = if (!motion) tween(0) else if (pressed) tween(60) else tween(220),
        label = "passcodeKeyFill"
    )
    val scale = animateFloatAsState(
        targetValue = if (pressed && motion) 0.92f else 1f,
        animationSpec = if (pressed) tween(LumiMotion.PressMillis) else spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "passcodeKeyScale"
    )
    val description = when (key) {
        PasscodeKey.Backspace -> stringResource(R.string.passcode_backspace)
        PasscodeKey.Biometric -> stringResource(R.string.passcode_use_biometric)
        else -> null
    }
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                alpha = if (enabled) 1f else 0.38f
            }
            .clip(CircleShape)
            .drawBehind { drawCircle(ink.copy(alpha = fill.value)) }
            .then(
                when {
                    eInk && disc -> Modifier.border(1.dp, Color.Black, CircleShape)
                    glass && disc -> Modifier.border(0.5.dp, Color.White.copy(alpha = if (isDark) 0.16f else 0.6f), CircleShape)
                    else -> Modifier
                }
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
                LockHaptics.keyTap(view)
                onKey(key)
            }
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        when (key) {
            is PasscodeKey.Digit -> Text(key.value.toString(), color = ink, fontSize = AppType.Title, maxLines = 1)
            // Function keys stay quieter than the digits.
            PasscodeKey.Cancel -> Text(
                stringResource(R.string.cancel), color = AppColors.TextSecondary, fontSize = AppType.Body, maxLines = 1
            )
            PasscodeKey.Backspace -> BackspaceGlyph(AppColors.TextSecondary, Modifier.size(size * 0.38f))
            PasscodeKey.Biometric -> androidx.compose.material3.Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_hidden_library_fingerprint),
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
