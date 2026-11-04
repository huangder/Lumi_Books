package com.huangder.lumibooks.ui.statistics

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.MenuAnimationStyle
import com.huangder.lumibooks.ui.theme.*
import com.huangder.lumibooks.util.LaunchThemeController

@Composable internal fun ReadingFeatureTheme(manager: DataStoreManager, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val initial = remember { LaunchThemeController.themeSnapshot(context) }
    val theme by manager.launchThemeSnapshot.collectAsState(initial = initial)
    val dark = when (theme.darkMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    EBookReaderTheme(
        liquidGlassStyle = theme.liquidGlassStyle, reduceTransparencyEnabled = theme.reduceTransparencyEnabled,
        darkTheme = dark, dynamicColor = theme.appTheme == "material3", appTheme = theme.appTheme,
        appAccentColor = theme.appAccentColor, appFontSize = theme.appFontSize, globalFontMode = theme.globalFontMode,
        liquidGlassTransparency = theme.liquidGlassTransparency, liquidGlassHdrHighlightEnabled = theme.liquidGlassHdrHighlightEnabled,
        eInkMode = theme.eInkModeEnabled, motionPreference = MotionPreference.fromStoredValue(theme.motionPreference),
        menuAnimationStyle = MenuAnimationStyle.fromStoredValue(theme.menuAnimationStyle), lumiBackgroundScene = LumiBackgroundScene.SECONDARY,
        content = content
    )
}
