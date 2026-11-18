package com.huangder.lumibooks.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.AppUpdateDialog
import com.huangder.lumibooks.ui.theme.AppSpace

@Composable
internal fun rememberUpdatePreviewControl(showingRealUpdate: Boolean): @Composable () -> Unit {
    var preview by rememberSaveable { mutableStateOf(false) }
    if (preview && !showingRealUpdate) {
        AppUpdateDialog(
            appVersion = "Dream",
            downloadUrl = "https://github.com/huangder/Lumi_Books/releases/download/Dream/app-universal-release.apk",
            onDownloadStarted = { preview = false },
            onLater = { preview = false }
        )
    }
    return {
        DetailCard(horizontalPadding = AppSpace.page) {
            SettingsSwitchRow(
                label = stringResource(R.string.app_update_debug_preview),
                checked = preview,
                onCheckedChange = { preview = it }
            )
        }
        Spacer(Modifier.height(AppSpace.md))
    }
}
