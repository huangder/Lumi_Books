package com.huangder.lumibooks.ui.privacy

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppType
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace

@Composable
internal fun gateTitle(controller: PasscodeGateController): String = stringResource(
    when (controller.stage) {
        GateStage.Enter -> if (controller.purpose == PasscodeGatePurpose.Unlock) {
            R.string.passcode_title_enter
        } else {
            R.string.passcode_title_current
        }
        GateStage.Create -> if (controller.resetting || controller.verifiedOld) {
            R.string.passcode_title_new
        } else {
            R.string.passcode_title_create
        }
        GateStage.Confirm -> R.string.passcode_title_confirm
        GateStage.OfferBiometric -> R.string.biometric_offer_title
    }
)

/** Subtitle text and whether it reports an error. Blank keeps the line's height reserved. */
@Composable
internal fun gateSubtitle(controller: PasscodeGateController, nowMillis: Long, forHide: Boolean): Pair<String, Boolean> {
    if (controller.lockedOut) {
        val seconds = ((controller.lockedUntil - nowMillis).coerceAtLeast(0L) + 999L) / 1000L
        return stringResource(R.string.passcode_locked_out, DateUtils.formatElapsedTime(seconds)) to true
    }
    when (val message = controller.message) {
        is GateMessage.Wrong -> return pluralStringResource(
            R.plurals.passcode_error_wrong, message.attemptsLeft, message.attemptsLeft
        ) to true
        GateMessage.Mismatch -> return stringResource(R.string.passcode_error_mismatch) to true
        GateMessage.ResetUnavailable -> return stringResource(R.string.passcode_reset_unavailable) to true
        is GateMessage.Text -> return message.text.toString() to true
        null -> Unit
    }
    val creatingFirst = controller.stage == GateStage.Create && !controller.resetting && !controller.verifiedOld
    return when {
        creatingFirst && forHide -> stringResource(R.string.passcode_subtitle_create_for_hide) to false
        creatingFirst -> stringResource(R.string.passcode_subtitle_create) to false
        controller.stage == GateStage.OfferBiometric -> stringResource(R.string.biometric_offer_body) to false
        else -> "" to false
    }
}

/**
 * Replaces the keypad after the first passcode is set, when the device has biometrics.
 * The header already turned into a fingerprint and explains it; the card only offers the choice.
 */
@Composable
internal fun BiometricOfferCard(onEnable: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.widthIn(max = 320.dp).padding(horizontal = AppSpace.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpace.md)) {
            LiquidGlassTextButton(text = stringResource(R.string.biometric_offer_skip), onClick = onSkip)
            LiquidGlassTextButton(
                text = stringResource(R.string.biometric_offer_enable),
                onClick = onEnable,
                tintedColor = AppColors.Accent
            )
        }
    }
}

/** Plain secondary text at the bottom of the screen; the touch target still meets 44dp. */
@Composable
internal fun GateForgotLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(AppRoundedCornerShape(AppRadius.sm))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AppSpace.md),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(R.string.passcode_forgot), color = AppColors.TextSecondary, fontSize = AppType.BodySmall, maxLines = 1)
    }
}

internal val GateTitleWeight = FontWeight.SemiBold
