package com.huangder.lumibooks.ui.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.DEFAULT_APP_ACCENT_HEX
import com.huangder.lumibooks.ui.components.ConfigurableActivityBack
import com.huangder.lumibooks.ui.settings.DetailPage
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.effectiveAppTheme
import com.huangder.lumibooks.ui.theme.rememberLiquidGlassCapability
import com.huangder.lumibooks.util.LaunchThemeController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TxtTocRuleHelpActivity : ComponentActivity() {
    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val launchTheme = LaunchThemeController.themeSnapshot(this)

        setContent {
            val appTheme by dataStoreManager.appTheme.collectAsState(initial = launchTheme.appTheme)
            val appAccentColor by dataStoreManager.appAccentColor.collectAsState(initial = launchTheme.appAccentColor)
            val globalFontMode by dataStoreManager.globalFontMode.collectAsState(initial = launchTheme.globalFontMode)
            val liquidGlassTransparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = launchTheme.liquidGlassTransparency)
            val liquidGlassHdrHighlightEnabled by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = launchTheme.liquidGlassHdrHighlightEnabled)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = launchTheme.darkMode)
            val isDark = when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val capability = rememberLiquidGlassCapability(view = LocalView.current)
            val resolvedTheme = effectiveAppTheme(appTheme, capability)

            EBookReaderTheme(
                darkTheme = isDark,
                dynamicColor = resolvedTheme == "material3",
                appTheme = resolvedTheme,
                appAccentColor = appAccentColor.ifBlank { DEFAULT_APP_ACCENT_HEX },
                liquidGlassTransparency = liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = liquidGlassHdrHighlightEnabled,
                globalFontMode = globalFontMode
            ) {
                ConfigurableActivityBack(
                    predictiveBackEnabled = dataStoreManager.predictiveBackEnabled.collectAsState(initial = true).value,
                    onBack = { finish() }
                )
                Surface(color = AppColors.WindowBg) {
                    DetailPage(
                        title = stringResource(R.string.txt_toc_rule_help),
                        onBack = { finish() }
                    ) {
                        TxtTocRuleHelpContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun TxtTocRuleHelpContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        HelpSection(
            title = stringResource(R.string.txt_toc_help_intro_title),
            body = stringResource(R.string.txt_toc_help_intro_body)
        )
        HelpSection(
            title = stringResource(R.string.txt_toc_help_step_one_title),
            body = stringResource(R.string.txt_toc_help_step_one_body)
        )
        HelpSection(
            title = stringResource(R.string.txt_toc_help_step_two_title),
            body = stringResource(R.string.txt_toc_help_step_two_body)
        )
        HelpSection(
            title = stringResource(R.string.txt_toc_help_step_three_title),
            body = stringResource(R.string.txt_toc_help_step_three_body)
        )
        HelpSection(
            title = stringResource(R.string.txt_toc_help_custom_title),
            body = stringResource(R.string.txt_toc_help_custom_body)
        )
        HelpSection(
            title = stringResource(R.string.txt_toc_help_template_title),
            body = stringResource(R.string.txt_toc_help_template_body)
        )
        HelpSection(
            title = stringResource(R.string.txt_toc_help_notes_title),
            body = stringResource(R.string.txt_toc_help_notes_body)
        )
    }
}

@Composable
private fun HelpSection(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            text = title,
            color = AppColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = body,
            color = AppColors.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
    }
}
