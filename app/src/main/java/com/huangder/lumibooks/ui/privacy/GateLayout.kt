package com.huangder.lumibooks.ui.privacy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.huangder.lumibooks.R
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType

/** Everything the gate layout reads; motion values are lambdas so only draw phases re-run. */
internal class GateUi(
    val controller: PasscodeGateController,
    val progress: () -> Float,
    val entering: () -> Boolean,
    val open: () -> Float,
    val shake: () -> Float,
    val motion: Boolean,
    val anchor: Offset?,
    val slot: GlyphSlot,
    val glyphTint: () -> Color,
    val subtitle: Pair<String, Boolean>,
    val showBiometricKey: Boolean,
    val showForgot: Boolean,
    val onKey: (PasscodeKey) -> Unit,
    val onForgot: () -> Unit,
    val onEnableBiometric: () -> Unit,
    val onSkipBiometric: () -> Unit
)

private val TabularFigures = TextStyle(fontFeatureSettings = "tnum")

/**
 * Header and keypad are centred above a fixed footer, and every part has a fixed width and
 * height, so neither a ticking countdown nor the footer link can move the lock.
 */
@Composable
internal fun GateContent(ui: GateUi, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val landscape = maxHeight < 520.dp && maxWidth > maxHeight
        val keySize = gateKeySize(maxWidth, maxHeight, landscape)
        if (landscape) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                GateHeader(ui, Modifier.weight(1f))
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { GateKeypadBlock(ui, keySize) }
                    GateFooter(ui)
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1f))
                GateHeader(ui, Modifier.fillMaxWidth())
                Spacer(Modifier.height(AppSpace.xl))
                GateKeypadBlock(ui, keySize)
                Spacer(Modifier.weight(1f))
                GateFooter(ui)
            }
        }
    }
}

/** 「忘记密码？」sits at the bottom of the screen in a slot kept whether or not it shows. */
@Composable
private fun GateFooter(ui: GateUi) {
    Box(Modifier.fillMaxWidth().height(GateFooterHeight).gatePart(ui, 5), contentAlignment = Alignment.Center) {
        if (ui.showForgot) GateForgotLink(onClick = ui.onForgot)
    }
}

@Composable
private fun GateHeader(ui: GateUi, modifier: Modifier = Modifier) {
    val c = ui.controller
    val offering = c.stage == GateStage.OfferBiometric
    val fingerprint by animateFloatAsState(if (offering) 1f else 0f, tween(if (ui.motion) 200 else 0), label = "gateFingerprint")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(GateGlyphSlot)
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInRoot()
                    ui.slot.center = Offset(position.x + coordinates.size.width / 2f, position.y + coordinates.size.height / 2f)
                }
                .graphicsLayer {
                    val p = ui.progress()
                    val anchor = ui.anchor
                    val slot = ui.slot.center
                    if (ui.motion && ui.entering() && anchor != null && slot.isSpecified) {
                        // Fly from the pull indicator's lock into place while growing to full size.
                        val t = AppEasing.Smooth.transform(p)
                        translationX = (anchor.x - slot.x) * (1f - t)
                        translationY = (anchor.y - slot.y) * (1f - t)
                        val scale = GATE_GLYPH_START_SCALE + (1f - GATE_GLYPH_START_SCALE) * t
                        scaleX = scale
                        scaleY = scale
                    } else {
                        alpha = p
                        val scale = if (ui.entering()) 0.86f + 0.14f * p else 1f + 0.12f * (1f - p)
                        scaleX = if (ui.motion) scale else 1f
                        scaleY = scaleX
                    }
                }
        ) {
            // The lock stays laid out while the fingerprint fades in, so nothing shifts.
            LockIcon(
                iconSize = GateLockSize,
                tint = ui.glyphTint,
                openProgress = ui.open,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - fingerprint }
            )
            if (fingerprint > 0f) {
                Icon(
                    painter = painterResource(R.drawable.ic_hidden_library_fingerprint),
                    contentDescription = null,
                    tint = AppColors.Accent,
                    modifier = Modifier.size(GateLockSize).graphicsLayer { alpha = fingerprint }
                )
            }
        }
        Spacer(Modifier.height(AppSpace.md))
        AnimatedContent(
            targetState = gateTitle(c),
            transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
            modifier = Modifier.fillMaxWidth().gatePart(ui, 1),
            contentAlignment = Alignment.Center,
            label = "gateTitle"
        ) { title ->
            Text(
                title, color = AppColors.TextPrimary, fontSize = AppType.Section, fontWeight = GateTitleWeight,
                textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(AppSpace.sm))
        // Full width and tabular digits: the countdown never changes the header's width.
        Text(
            text = ui.subtitle.first,
            color = if (ui.subtitle.second) PasscodeErrorColor else AppColors.TextSecondary,
            fontSize = AppType.BodySmall,
            style = TabularFigures,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpace.xl).gatePart(ui, 1)
        )
        Spacer(Modifier.height(AppSpace.md))
        PasscodeDots(
            filled = c.input.length,
            length = PasscodeGateController.LENGTH,
            tone = c.tone,
            modifier = Modifier.gatePart(ui, 2).graphicsLayer {
                translationX = ui.shake()
                alpha = if (offering) 0f else 1f
            }
        )
    }
}

/** Keypad, or the biometric offer after the first passcode, in a box the size of the keypad. */
@Composable
private fun GateKeypadBlock(ui: GateUi, keySize: Dp, modifier: Modifier = Modifier) {
    val c = ui.controller
    // 3 keys + 2 gaps of 0.32 wide, 4 rows + 3 gaps of 0.2 tall; a little slack for px rounding.
    val blockModifier = modifier.size(width = keySize * 3.64f + 4.dp, height = keySize * 4.6f + 4.dp)
    AnimatedContent(
        targetState = c.stage == GateStage.OfferBiometric,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        modifier = blockModifier,
        contentAlignment = Alignment.Center,
        label = "gateKeypad"
    ) { offering ->
        if (offering) {
            BiometricOfferCard(onEnable = ui.onEnableBiometric, onSkip = ui.onSkipBiometric)
        } else {
            PasscodeKeypad(
                keySize = keySize,
                enabled = c.acceptsInput,
                showBiometric = ui.showBiometricKey,
                showBackspace = c.input.isNotEmpty(),
                onKey = ui.onKey,
                rowModifier = { row -> Modifier.gatePart(ui, 3 + row / 2) }
            )
        }
    }
}

/** Fade and rise of one staggered part; reduced motion keeps only the fade. */
private fun Modifier.gatePart(ui: GateUi, index: Int): Modifier = graphicsLayer {
    val part = gateStagger(ui.progress(), index)
    alpha = part
    if (ui.motion) translationY = (1f - part) * 14.dp.toPx() * if (ui.entering()) 1f else -0.5f
}
