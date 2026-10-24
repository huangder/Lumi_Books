package com.huangder.lumibooks.ui.home.remote

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.components.LiquidGlassDialogHost
import com.huangder.lumibooks.ui.components.LiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.LocalLiquidGlassBackdrop
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import com.huangder.lumibooks.ui.theme.effectiveAppTheme
import com.huangder.lumibooks.ui.theme.rememberLiquidGlassCapability
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * Theme and glass hosts for the remote library Activities, mirroring AuthorizedFolderBooksActivity:
 * one page backdrop captured from the scrolling content; the page's own controls keep the material
 * fallback (no window-level backdrop), so nothing samples a layer that contains itself.
 */
@Composable
internal fun ComponentActivity.RemoteThemedContent(
    dataStoreManager: DataStoreManager,
    systemDarkMode: Boolean,
    content: @Composable (contentBackdrop: LayerBackdrop?, predictiveBackEnabled: Boolean) -> Unit
) {
    val launchTheme = androidx.compose.runtime.remember {
        com.huangder.lumibooks.util.LaunchThemeController.themeSnapshot(this)
    }
    val appTheme by dataStoreManager.appTheme.collectAsState(initial = launchTheme.appTheme)
    val appAccentColor by dataStoreManager.appAccentColor.collectAsState(initial = launchTheme.appAccentColor)
    val appFontSize by dataStoreManager.appFontSize.collectAsState(initial = launchTheme.appFontSize)
    val globalFontMode by dataStoreManager.globalFontMode.collectAsState(initial = launchTheme.globalFontMode)
    val liquidGlassTransparency by dataStoreManager.liquidGlassTransparency
        .collectAsState(initial = launchTheme.liquidGlassTransparency)
    val liquidGlassHdrHighlightEnabled by dataStoreManager.liquidGlassHdrHighlightEnabled
        .collectAsState(initial = launchTheme.liquidGlassHdrHighlightEnabled)
    val cardOutlinesEnabled by dataStoreManager.cardOutlinesEnabled
        .collectAsState(initial = launchTheme.cardOutlinesEnabled)
    val darkMode by dataStoreManager.darkMode.collectAsState(initial = launchTheme.darkMode)
    val coverReadingProgressEnabled by dataStoreManager.coverReadingProgressEnabled.collectAsState(initial = false)
    val motionPreferenceValue by dataStoreManager.motionPreference
        .collectAsState(initial = launchTheme.motionPreference)
    val predictiveBackEnabled by dataStoreManager.predictiveBackEnabled
        .collectAsState(initial = launchTheme.predictiveBackEnabled)
    val eInkMode by dataStoreManager.eInkModeEnabled.collectAsState(initial = launchTheme.eInkModeEnabled)
    val menuAnimationStyleValue by dataStoreManager.menuAnimationStyle
        .collectAsState(initial = launchTheme.menuAnimationStyle)
    val isDark = if (eInkMode) {
        false
    } else {
        when (darkMode) {
            "dark" -> true
            "light" -> false
            else -> systemDarkMode
        }
    }
    val capability = rememberLiquidGlassCapability(eInkMode, LocalView.current)
    val effectiveTheme = effectiveAppTheme(appTheme, capability)
    val liquidGlassStyle by dataStoreManager.liquidGlassStyle
        .collectAsState(initial = launchTheme.liquidGlassStyle)
    val reduceTransparencyEnabled by dataStoreManager.reduceTransparencyEnabled
        .collectAsState(initial = launchTheme.reduceTransparencyEnabled)
    EBookReaderTheme(
        liquidGlassStyle = liquidGlassStyle,
        reduceTransparencyEnabled = reduceTransparencyEnabled,
        menuAnimationStyle = com.huangder.lumibooks.domain.model.MenuAnimationStyle.fromStoredValue(menuAnimationStyleValue),
        darkTheme = isDark,
        dynamicColor = effectiveTheme == "material3",
        appTheme = appTheme,
        lumiBackgroundScene = com.huangder.lumibooks.ui.theme.LumiBackgroundScene.SECONDARY,
        appAccentColor = appAccentColor,
        liquidGlassTransparency = liquidGlassTransparency,
        liquidGlassHdrHighlightEnabled = liquidGlassHdrHighlightEnabled && !eInkMode,
        cardOutlinesEnabled = cardOutlinesEnabled,
        coverReadingProgressEnabled = coverReadingProgressEnabled,
        eInkMode = eInkMode,
        appFontSize = appFontSize,
        globalFontMode = globalFontMode,
        motionPreference = MotionPreference.fromStoredValue(motionPreferenceValue)
    ) {
        val pageBackdrop = rememberLayerBackdrop()
        val glassBackdrop = pageBackdrop.takeIf { effectiveTheme == "liquid_glass" && !eInkMode }
        Surface(modifier = Modifier.fillMaxSize(), color = AppColors.PageBg) {
            LiquidGlassMenuHost(modifier = Modifier.fillMaxSize(), backdrop = glassBackdrop) {
                LiquidGlassDialogHost(modifier = Modifier.fillMaxSize(), backdrop = glassBackdrop) {
                    CompositionLocalProvider(LocalLiquidGlassBackdrop provides null) {
                        content(glassBackdrop, predictiveBackEnabled && !eInkMode)
                    }
                }
            }
        }
    }
}

internal fun Configuration.isNightModeEnabled(): Boolean =
    (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
