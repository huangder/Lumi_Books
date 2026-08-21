package com.huangder.lumibooks.ui.bookshelf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.rememberLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.effectiveAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 书签、高亮与笔记 — 独立 Activity
 *
 * 系统过渡动画：与 SettingsActivity 一致，由 OEM 默认动画控制。
 */
@AndroidEntryPoint
class BookNotesActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BOOK_ID = "bookId"
        const val EXTRA_INITIAL_TAB = "initial_tab"
        const val EXTRA_TARGET_NOTE_ID = "target_note_id"

        const val TAB_HIGHLIGHTS = 0
        const val TAB_NOTES = 1
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val initialTab = intent.getIntExtra(EXTRA_INITIAL_TAB, TAB_HIGHLIGHTS)
            .coerceIn(TAB_HIGHLIGHTS, TAB_NOTES)
        val targetNoteId = intent.getLongExtra(EXTRA_TARGET_NOTE_ID, -1L)
            .takeIf { it > 0L }

        setContent {
            val appTheme by dataStoreManager.appTheme.collectAsState(initial = "lumi")
            val globalFontMode by dataStoreManager.globalFontMode.collectAsState(initial = "system")
            val liquidGlassTransparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = 0.55f)
            val liquidGlassHdrHighlightEnabled by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = false)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = "system")
            val predictiveBackEnabled by dataStoreManager.predictiveBackEnabled.collectAsState(initial = true)
            val isDark = when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val capability = rememberLiquidGlassCapability(view = LocalView.current)
            val resolvedAppTheme = effectiveAppTheme(appTheme, capability)

            EBookReaderTheme(
                darkTheme = isDark,
                dynamicColor = resolvedAppTheme == "material3",
                appTheme = resolvedAppTheme,
                liquidGlassTransparency = liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = liquidGlassHdrHighlightEnabled,
                globalFontMode = globalFontMode
            ) {
                com.huangder.lumibooks.ui.components.ConfigurableActivityBack(
                    predictiveBackEnabled = predictiveBackEnabled,
                    onBack = { finish() }
                )
                Surface(modifier = Modifier.fillMaxSize()) {
                    com.huangder.lumibooks.ui.components.LiquidGlassDialogHost(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        BookNotesScreen(
                            onNavigateBack = { finish() },
                            initialTab = initialTab,
                            targetNoteId = targetNoteId
                        )
                    }
                }
            }
        }
    }
}
