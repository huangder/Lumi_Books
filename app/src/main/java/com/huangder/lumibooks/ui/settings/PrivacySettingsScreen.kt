package com.huangder.lumibooks.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.R
import com.huangder.lumibooks.privacy.HiddenLibraryBiometrics
import com.huangder.lumibooks.ui.components.lumiCardSurface
import com.huangder.lumibooks.ui.privacy.HiddenLibraryGate
import com.huangder.lumibooks.ui.privacy.HiddenLibraryLockViewModel
import com.huangder.lumibooks.ui.privacy.PasscodeGatePurpose
import com.huangder.lumibooks.ui.privacy.rememberGateMotionState
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalReduceTransparency
import com.huangder.lumibooks.ui.theme.cardOutline

/** Settings → 隐私. Every entry that changes access first asks for the current passcode. */
@Composable
fun PrivacySettingsRoute(onBack: () -> Unit, lockViewModel: HiddenLibraryLockViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lock by lockViewModel.lockState.collectAsState()
    val biometricAvailable = remember { HiddenLibraryBiometrics.isAvailable(context) }
    var gatePurpose by remember { mutableStateOf<PasscodeGatePurpose?>(null) }
    var enablingBiometric by remember { mutableStateOf(false) }
    val gateMotion = rememberGateMotionState()
    val blurAllowed = !LocalEInkMode.current && !LocalReduceTransparency.current && android.os.Build.VERSION.SDK_INT >= 31

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val radius = 28.dp.toPx() * gateMotion.presence
                renderEffect = if (blurAllowed && radius > 0.01f) {
                    android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP)
                        .asComposeRenderEffect()
                } else null
            }
        ) {
            DetailPage(stringResource(R.string.category_privacy), onBack) {
                PrivacySettingsContent(
                    hasPasscode = lock.hasPasscode,
                    biometricAvailable = biometricAvailable,
                    biometricEnabled = lock.biometricEnabled,
                    onOpenHiddenLibrary = { gatePurpose = PasscodeGatePurpose.Unlock },
                    onChangePasscode = { gatePurpose = PasscodeGatePurpose.ChangePasscode },
                    onBiometricChange = { enabled ->
                        if (enabled) {
                            enablingBiometric = true
                            gatePurpose = PasscodeGatePurpose.Verify
                        } else {
                            lockViewModel.setBiometricEnabled(false)
                        }
                    }
                )
            }
        }
        HiddenLibraryGate(
            visible = gatePurpose != null,
            purpose = gatePurpose ?: PasscodeGatePurpose.Unlock,
            motionState = gateMotion,
            viewModel = lockViewModel,
            onDismiss = {
                gatePurpose = null
                enablingBiometric = false
            },
            onUnlocked = {
                when (gatePurpose) {
                    PasscodeGatePurpose.Unlock -> {
                        lockViewModel.unlockSession()
                        context.startActivity(
                            Intent(context, MainActivity::class.java)
                                .putExtra(MainActivity.EXTRA_OPEN_DESTINATION, MainActivity.DESTINATION_HIDDEN_LIBRARY)
                        )
                    }
                    PasscodeGatePurpose.ChangePasscode ->
                        Toast.makeText(context, R.string.passcode_changed, Toast.LENGTH_SHORT).show()
                    PasscodeGatePurpose.Verify -> if (enablingBiometric) lockViewModel.setBiometricEnabled(true)
                    null -> Unit
                }
                gatePurpose = null
                enablingBiometric = false
            }
        )
    }
}

@Composable
private fun PrivacySettingsContent(
    hasPasscode: Boolean,
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    onOpenHiddenLibrary: () -> Unit,
    onChangePasscode: () -> Unit,
    onBiometricChange: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        PrivacyCard {
            SettingsNavigationRow(
                label = stringResource(R.string.hidden_library_title),
                supportingText = stringResource(
                    if (hasPasscode) R.string.privacy_hidden_library_summary else R.string.privacy_hidden_library_no_passcode
                ),
                onClick = onOpenHiddenLibrary
            )
        }
        if (hasPasscode) {
            Text(
                stringResource(R.string.privacy_section_access),
                modifier = Modifier.padding(start = 28.dp, top = AppSpace.lg, bottom = 6.dp),
                fontSize = AppType.BodySmall,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextSecondary
            )
            PrivacyCard {
                SettingsNavigationRow(label = stringResource(R.string.privacy_change_passcode), onClick = onChangePasscode)
                if (biometricAvailable) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = AppSpace.md).height(0.5.dp).background(AppColors.Divider))
                    SettingsSwitchRow(
                        label = stringResource(R.string.privacy_biometric_unlock),
                        supportingText = stringResource(R.string.privacy_biometric_unlock_summary),
                        checked = biometricEnabled,
                        onCheckedChange = onBiometricChange
                    )
                }
            }
        }
        Text(
            stringResource(R.string.privacy_hidden_footer),
            modifier = Modifier.padding(horizontal = 28.dp, vertical = AppSpace.md),
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary
        )
    }
}

@Composable
private fun PrivacyCard(content: @Composable () -> Unit) {
    val shape = AppRoundedCornerShape(AppRadius.lg)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.md)
            .shadow(8.dp, shape, ambientColor = Color(0x06000000), spotColor = Color(0x06000000))
            .cardOutline(shape)
            .clip(shape)
            .lumiCardSurface(shape = shape)
    ) { content() }
}
