package com.huangder.lumibooks.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.huangder.lumibooks.R
import com.huangder.lumibooks.tts.TtsReaderResumeChoice
import com.huangder.lumibooks.tts.TtsStartChoice
import com.huangder.lumibooks.ui.components.AppDialogAction
import com.huangder.lumibooks.ui.components.AppDialogActionRole
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog

@Composable
fun ListeningPreferencesDetail(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    ListeningPreferencesContent(
        uiState,
        viewModel::saveTtsStartChoice,
        viewModel::saveTtsReaderResumeChoice,
        viewModel::saveTtsContinueOnReaderExit
    )
}

@Composable
internal fun ListeningPreferencesContent(
    uiState: SettingsUiState,
    onStartChoice: (TtsStartChoice?) -> Unit,
    onResumeChoice: (TtsReaderResumeChoice?) -> Unit,
    onContinueOnExit: (Boolean?) -> Unit
) {
    var showStartDialog by remember { mutableStateOf(false) }
    var showResumeDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    val startOptions = listOf(
        null to stringResource(R.string.tts_start_ask_each_time),
        TtsStartChoice.CURRENT_PAGE to stringResource(R.string.tts_start_current_page),
        TtsStartChoice.SAVED_POSITION to stringResource(R.string.tts_start_saved_position)
    )
    val resumeOptions = listOf(
        null to stringResource(R.string.tts_listening_resume_show_prompt),
        TtsReaderResumeChoice.KEEP_READING to stringResource(R.string.tts_listening_resume_keep),
        TtsReaderResumeChoice.LISTENING_POSITION to stringResource(R.string.tts_listening_resume_jump),
        TtsReaderResumeChoice.FURTHEST_PROGRESS to stringResource(R.string.tts_listening_resume_furthest)
    )
    val exitOptions = listOf(
        null to stringResource(R.string.tts_start_ask_each_time),
        true to stringResource(R.string.tts_exit_continue),
        false to stringResource(R.string.tts_exit_stop)
    )

    SettingsSectionTitle(
        stringResource(R.string.settings_section_listening_behavior),
        sectionKey = "listening_behavior"
    )
    DetailCard {
        SettingsNavigationRow(
            label = stringResource(R.string.tts_start_setting),
            value = startOptions.first { it.first == uiState.ttsStartChoice }.second
        ) { showStartDialog = true }
        SettingsDivider()
        SettingsNavigationRow(
            label = stringResource(R.string.tts_listening_resume_setting),
            value = resumeOptions.first { it.first == uiState.ttsReaderResumeChoice }.second
        ) { showResumeDialog = true }
        SettingsDivider()
        SettingsNavigationRow(
            label = stringResource(R.string.tts_exit_setting),
            value = exitOptions.first { it.first == uiState.ttsContinueOnReaderExit }.second
        ) { showExitDialog = true }
    }

    if (showStartDialog) {
        ChoiceDialog<TtsStartChoice?>(
            title = stringResource(R.string.tts_start_setting),
            selected = uiState.ttsStartChoice,
            options = startOptions,
            onSelect = onStartChoice,
            onDismiss = { showStartDialog = false }
        )
    }
    if (showResumeDialog) {
        ChoiceDialog<TtsReaderResumeChoice?>(
            title = stringResource(R.string.tts_listening_resume_setting),
            selected = uiState.ttsReaderResumeChoice,
            options = resumeOptions,
            onSelect = onResumeChoice,
            onDismiss = { showResumeDialog = false }
        )
    }
    if (showExitDialog) {
        ChoiceDialog<Boolean?>(
            title = stringResource(R.string.tts_exit_setting),
            selected = uiState.ttsContinueOnReaderExit,
            options = exitOptions,
            onSelect = onContinueOnExit,
            onDismiss = { showExitDialog = false }
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    selected: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    LiquidGlassAlertDialog(
        onDismissRequest = onDismiss,
        titleText = title,
        text = {
            Column {
                options.forEach { (value, label) ->
                    TtsProviderOptionRow(
                        title = label,
                        selected = selected == value,
                        onClick = {
                            onSelect(value)
                            onDismiss()
                        }
                    )
                }
            }
        },
        actions = listOf(
            AppDialogAction(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                role = AppDialogActionRole.Cancel
            )
        )
    )
}
