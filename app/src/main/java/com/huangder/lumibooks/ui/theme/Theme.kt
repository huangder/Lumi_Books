package com.huangder.lumibooks.ui.theme

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.huangder.lumibooks.domain.model.DEFAULT_APP_ACCENT_HEX
import com.huangder.lumibooks.domain.model.appAccentContentArgb
import com.huangder.lumibooks.domain.model.blendAppAccentArgb
import com.huangder.lumibooks.domain.model.deriveDarkAppAccentArgb
import com.huangder.lumibooks.domain.model.normalizeAppAccentHex
import com.huangder.lumibooks.domain.model.parseAppAccentArgb

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F2FD),
    onPrimaryContainer = Color(0xFF001F3F),
    secondary = SecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E0FF),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF34C759),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8F5E9),
    onTertiaryContainer = Color(0xFF1B5E20),
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF636366),
    error = ErrorLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFFC6C6C8)
)


private val EInkColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDEDED),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF333333),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E6E6),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF555555),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDADADA),
    onTertiaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF4F4F4),
    onSurfaceVariant = Color(0xFF444444),
    error = Color.Black,
    onError = Color.White,
    errorContainer = Color(0xFFE0E0E0),
    onErrorContainer = Color.Black,
    outline = Color(0xFF8A8A8A),
    outlineVariant = Color(0xFFBDBDBD),
    scrim = Color.Black,
    inverseSurface = Color.Black,
    inverseOnSurface = Color.White,
    inversePrimary = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1C3A5F),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = SecondaryDark,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF3E3A5F),
    onSecondaryContainer = Color(0xFFE8E0FF),
    tertiary = Color(0xFF30D158),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF1B5E20),
    onTertiaryContainer = Color(0xFFE8F5E9),
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = Color(0xFF3A3A3C),
    onSurfaceVariant = Color(0xFF8E8E93),
    error = ErrorDark,
    onError = Color.Black,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF636366)
)

private fun lightAppColorScheme(accentArgb: Int, darkAccentArgb: Int) = LightColorScheme.copy(
    primary = Color(accentArgb),
    onPrimary = Color(appAccentContentArgb(accentArgb)),
    primaryContainer = Color(blendAppAccentArgb(0xFFFFFFFF.toInt(), accentArgb, 0.14f)),
    onPrimaryContainer = Color.Black,
    inversePrimary = Color(darkAccentArgb)
)

private fun darkAppColorScheme(accentArgb: Int, lightAccentArgb: Int) = DarkColorScheme.copy(
    primary = Color(accentArgb),
    onPrimary = Color(appAccentContentArgb(accentArgb)),
    primaryContainer = Color(blendAppAccentArgb(0xFF1C1C1E.toInt(), accentArgb, 0.24f)),
    onPrimaryContainer = Color.White,
    inversePrimary = Color(lightAccentArgb)
)

@Composable
fun EBookReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    appTheme: String = "lumi",
    appAccentColor: String = DEFAULT_APP_ACCENT_HEX,
    liquidGlassTransparency: Float = 0.55f,
    liquidGlassHdrHighlightEnabled: Boolean = false,
    cardOutlinesEnabled: Boolean = false,
    eInkMode: Boolean = false,
    globalFontMode: String = GlobalFontMode.DEFAULT,
    motionPreference: MotionPreference = MotionPreference.STANDARD,
    content: @Composable () -> Unit
) {
    val effectiveDarkTheme = if (eInkMode) false else darkTheme
    val effectiveDynamicColor = if (eInkMode) false else dynamicColor
    val view = LocalView.current
    val liquidGlassCapability = rememberLiquidGlassCapability(eInkMode, view)
    val effectiveAppTheme = effectiveAppTheme(appTheme, liquidGlassCapability)
    val effectiveHdrHighlightEnabled = liquidGlassHdrHighlightEnabled && !eInkMode
    val normalizedAccentHex = normalizeAppAccentHex(appAccentColor)
    val lightAccentArgb = remember(normalizedAccentHex) { parseAppAccentArgb(normalizedAccentHex) }
    val darkAccentArgb = remember(lightAccentArgb) { deriveDarkAppAccentArgb(lightAccentArgb) }

    val colorScheme = when {
        eInkMode -> EInkColorScheme
        effectiveDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (effectiveDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        effectiveDynamicColor -> if (effectiveDarkTheme) DarkColorScheme else LightColorScheme
        effectiveDarkTheme -> darkAppColorScheme(darkAccentArgb, lightAccentArgb)
        else -> lightAppColorScheme(lightAccentArgb, darkAccentArgb)
    }
    val effectiveAccentColor = when {
        eInkMode -> Color.Black
        effectiveDynamicColor -> colorScheme.primary
        effectiveDarkTheme -> Color(darkAccentArgb)
        else -> Color(lightAccentArgb)
    }
    val effectiveOnAccentColor = when {
        eInkMode -> Color.White
        effectiveDynamicColor -> colorScheme.onPrimary
        else -> Color(appAccentContentArgb(effectiveAccentColor.toArgb()))
    }

    val hdrHighlightRequested = effectiveAppTheme == "liquid_glass" && effectiveHdrHighlightEnabled
    val hdrHighlightActive = hdrHighlightRequested && liquidGlassCapability.hdrSupported
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            // 透明状态栏，内容延伸到状态栏下方
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !effectiveDarkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !effectiveDarkTheme
            window.colorMode = if (hdrHighlightActive) {
                ActivityInfo.COLOR_MODE_HDR
            } else {
                ActivityInfo.COLOR_MODE_DEFAULT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                // Request only a small HDR envelope for the pressed highlight. Large values
                // make several OEMs tone-map the entire window and visibly dim SDR content.
                window.desiredHdrHeadroom = if (hdrHighlightActive) 1.15f else 0f
            }
        }
    }

    CompositionLocalProvider(
        LocalIsDarkTheme provides effectiveDarkTheme,
        LocalUseMaterial3Theme provides effectiveDynamicColor,
        LocalAppTheme provides effectiveAppTheme,
        LocalAppAccentHex provides normalizedAccentHex,
        LocalAppAccentColor provides effectiveAccentColor,
        LocalOnAppAccentColor provides effectiveOnAccentColor,
        LocalLiquidGlassCapability provides liquidGlassCapability,
        LocalEInkMode provides eInkMode,
        LocalMotionEnabled provides (!eInkMode && motionPreference == MotionPreference.STANDARD),
        LocalMotionPreference provides motionPreference,
        LocalGlobalFontMode provides GlobalFontMode.normalize(globalFontMode),
        LocalLiquidGlassTransparency provides liquidGlassTransparency.coerceIn(0f, 1f),
        LocalLiquidGlassHdrHighlightEnabled provides hdrHighlightActive,
        LocalCardOutlinesEnabled provides cardOutlinesEnabled
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
