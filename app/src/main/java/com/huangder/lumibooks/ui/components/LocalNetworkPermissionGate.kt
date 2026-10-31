package com.huangder.lumibooks.ui.components

import com.huangder.lumibooks.ui.components.AppDialogAction
import com.huangder.lumibooks.ui.components.AppDialogActionRole

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.sync.LocalNetworkAccessManager
import com.huangder.lumibooks.data.sync.LocalNetworkAccessState
import kotlinx.coroutines.launch

typealias RequestLocalNetworkPermission = (String, () -> Unit, () -> Unit) -> Unit

private data class PendingLocalNetworkRequest(
    val onGranted: () -> Unit,
    val onDenied: () -> Unit
)

/** Shared rationale and Android 17 permission bridge for visible WebDAV flows. */
@Composable
fun rememberLocalNetworkPermissionGate(
    accessManager: LocalNetworkAccessManager,
    onPermissionGranted: () -> Unit = {}
): RequestLocalNetworkPermission {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingLocalNetworkRequest?>(null) }
    var showRationale by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pending
        pending = null
        if (granted) {
            onPermissionGranted()
            request?.onGranted?.invoke()
        } else {
            request?.onDenied?.invoke()
        }
    }

    if (showRationale) {
        LiquidGlassAlertDialog(
            onDismissRequest = {
                showRationale = false
                pending?.onDenied?.invoke()
                pending = null
            },
            titleText = stringResource(R.string.local_network_permission_title),
            text = { Text(stringResource(R.string.local_network_permission_message)) },
            actions = listOf(
                AppDialogAction(
                    text = stringResource(R.string.local_network_permission_continue),
                    onClick = {
                        showRationale = false
                        launcher.launch(LocalNetworkAccessManager.PERMISSION)
                    },
                    role = AppDialogActionRole.Primary
                ),
                AppDialogAction(
                    text = stringResource(R.string.local_network_permission_not_now),
                    onClick = {
                        showRationale = false
                        pending?.onDenied?.invoke()
                        pending = null
                    },
                    role = AppDialogActionRole.Cancel
                )
            ))
    }

    return remember(accessManager) {
        { serverUrl: String, onGranted: () -> Unit, onDenied: () -> Unit ->
            scope.launch {
                when (accessManager.stateFor(serverUrl)) {
                    LocalNetworkAccessState.NOT_REQUIRED,
                    LocalNetworkAccessState.GRANTED -> onGranted()
                    LocalNetworkAccessState.PERMISSION_REQUIRED -> {
                        pending = PendingLocalNetworkRequest(onGranted, onDenied)
                        showRationale = true
                    }
                }
            }
        }
    }
}
