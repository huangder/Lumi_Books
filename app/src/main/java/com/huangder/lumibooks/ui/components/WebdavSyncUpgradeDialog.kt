package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R

@Composable
fun WebdavSyncUpgradeDialog(onOpenSync: () -> Unit, onDismiss: () -> Unit) {
    LiquidGlassAlertDialog(
        onDismissRequest = onDismiss,
        titleText = stringResource(R.string.webdav_identity_upgrade_title),
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.webdav_identity_upgrade_message))
            }
        },
        actions = listOf(
            AppDialogAction(
                text = stringResource(R.string.webdav_identity_upgrade_open),
                onClick = onOpenSync,
                role = AppDialogActionRole.Primary
            ),
            AppDialogAction(
                text = stringResource(R.string.webdav_identity_upgrade_later),
                onClick = onDismiss,
                role = AppDialogActionRole.Cancel
            )
        )
    )
}
