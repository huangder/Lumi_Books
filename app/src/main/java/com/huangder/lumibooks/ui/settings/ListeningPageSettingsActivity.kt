package com.huangder.lumibooks.ui.settings

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.tts.ListeningDisplayStyle
import com.huangder.lumibooks.tts.ListeningPageSettings
import com.huangder.lumibooks.tts.SubtitleSeekGesture
import com.huangder.lumibooks.ui.components.ConfigurableActivityBack
import com.huangder.lumibooks.ui.statistics.ReadingFeatureTheme
import com.huangder.lumibooks.util.LaunchThemeController
import com.huangder.lumibooks.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import com.huangder.lumibooks.ui.animation.finishWithPush

@AndroidEntryPoint
class ListeningPageSettingsActivity : ComponentActivity() {
    @Inject lateinit var settings: DataStoreManager

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val initial = LaunchThemeController.themeSnapshot(this)
        setContent {
            val pageSettings by settings.listeningPageSettings.collectAsState(ListeningPageSettings())
            val predictiveBack by settings.predictiveBackEnabled.collectAsState(initial.predictiveBackEnabled)
            ReadingFeatureTheme(settings) {
                ConfigurableActivityBack(predictiveBackEnabled = predictiveBack, onBack = { finishWithPush() })
                ListeningPageSettingsScreen(
                    settings = pageSettings,
                    onStyle = { lifecycleScope.launch { settings.saveListeningDisplayStyle(it) } },
                    onGesture = { lifecycleScope.launch { settings.saveSubtitleSeekGesture(it) } },
                    onBack = { finishWithPush() }
                )
            }
        }
    }
}

@Composable
internal fun ListeningPageSettingsScreen(
    settings: ListeningPageSettings,
    onStyle: (ListeningDisplayStyle) -> Unit,
    onGesture: (SubtitleSeekGesture) -> Unit,
    onBack: () -> Unit
) {
    DetailPage(stringResource(R.string.tts_page_settings_title), onBack) {
        DetailCard {
            DropdownSettingRow(
                label = stringResource(R.string.tts_page_default_style),
                options = listOf(
                    ListeningDisplayStyle.COVER.storedValue to stringResource(R.string.tts_page_style_cover),
                    ListeningDisplayStyle.SUBTITLES.storedValue to stringResource(R.string.tts_page_style_subtitles)
                ),
                selected = settings.defaultDisplayStyle.storedValue,
                onSelect = { onStyle(ListeningDisplayStyle.fromStoredValue(it)) }
            )
            SettingsDivider()
            DropdownSettingRow(
                label = stringResource(R.string.tts_page_seek_gesture),
                options = listOf(
                    SubtitleSeekGesture.DOUBLE_TAP.storedValue to stringResource(R.string.tts_page_seek_double_tap),
                    SubtitleSeekGesture.SINGLE_TAP.storedValue to stringResource(R.string.tts_page_seek_single_tap)
                ),
                selected = settings.seekGesture.storedValue,
                onSelect = { onGesture(SubtitleSeekGesture.fromStoredValue(it)) }
            )
        }
    }
}
