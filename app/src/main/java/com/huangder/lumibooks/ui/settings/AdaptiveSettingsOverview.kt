package com.huangder.lumibooks.ui.settings

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R

/** Use the available window, so split-screen windows return to the compact layout. */
@Composable
internal fun AdaptiveSettingsOverview(
    onBack: () -> Unit,
    overview: @Composable () -> Unit,
    preferences: @Composable () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoColumns = maxWidth >= 960.dp && maxHeight >= 480.dp && maxWidth > maxHeight
        CollapsingSettingsScaffold(
            title = stringResource(R.string.settings_title),
            onBack = onBack,
            alignToolbarToContent = twoColumns,
            contentMaxWidth = if (twoColumns) 1200.dp else 840.dp
        ) {
            if (twoColumns) {
                Row(Modifier.fillMaxWidth().testTag("settings-two-columns")) {
                    Column(Modifier.weight(0.9f).testTag("settings-overview")) { overview() }
                    Column(Modifier.weight(1.1f).testTag("settings-preferences")) { preferences() }
                }
            } else {
                Column(Modifier.fillMaxWidth().testTag("settings-single-column")) {
                    Column(Modifier.fillMaxWidth().testTag("settings-overview")) { overview() }
                    Column(Modifier.fillMaxWidth().testTag("settings-preferences")) { preferences() }
                }
            }
        }
    }
}
