package com.huangder.lumibooks.ui.welcome
import com.huangder.lumibooks.ui.theme.LocalReduceTransparency
import com.huangder.lumibooks.ui.icons.AppIcons

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import com.huangder.lumibooks.ui.animation.VisualMotion.spring
import androidx.compose.animation.core.snap
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import com.huangder.lumibooks.ui.animation.VisualMotion.delayMillis
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.components.ConfigurableBackHandler
import com.huangder.lumibooks.ui.components.ConfigurableBottomSheetBackHandler
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.materialBottomSheetMotion
import com.huangder.lumibooks.ui.components.adaptiveSheetFrame
import com.huangder.lumibooks.ui.components.adaptiveSheetShape
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassTransparency
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.LocalMotionPreference
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassSamplingQuality
import com.huangder.lumibooks.ui.theme.MotionPreference
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.domain.model.blendAppAccentArgb
import com.huangder.lumibooks.util.LocaleHelper
import java.util.Locale
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import com.huangder.lumibooks.ui.theme.AppPresentation

// 设计规范颜色 - 浅色模式
private val AccentColor: Color @Composable get() = AppColors.Accent
// Theme tokens (same hues as before in light/dark, and now also honour Material3 and e-ink).
private val LightTextSecondary: Color @Composable get() = AppColors.TextSecondary
private val LightBgGray: Color @Composable get() = AppColors.BgGray
private val LightBackground: Color @Composable get() = AppColors.WindowBg
private val LightCardBg: Color @Composable get() = AppColors.CardBg

private val DarkTextSecondary: Color @Composable get() = AppColors.TextSecondary
private val DarkBgGray: Color @Composable get() = AppColors.BgGray
private val DarkBackground: Color @Composable get() = AppColors.WindowBg
private val DarkCardBg: Color @Composable get() = AppColors.CardBg
private val WelcomeContentMaxWidth = 560.dp

internal enum class WelcomePage {
    LANGUAGE_SETUP,
    INTRODUCTION,
    LIQUID_GLASS_PREVIEW,
    SUPPORT
}

private enum class UpdatePreview {
    MINERU,
    LIQUID_GLASS,
    BOOKSHELF,
    EXCERPT,
    G2,
    DICTIONARY
}

internal fun welcomePages(
    shouldShowLanguageSetup: Boolean,
    isNewInstallation: Boolean,
    isEInkMode: Boolean
): List<WelcomePage> = buildList {
    if (shouldShowLanguageSetup) add(WelcomePage.LANGUAGE_SETUP)
    add(WelcomePage.INTRODUCTION)
    if (isNewInstallation && !isEInkMode) add(WelcomePage.LIQUID_GLASS_PREVIEW)
    add(WelcomePage.SUPPORT)
}

private data class WelcomeLanguageOption(
    val languageTag: String,
    val label: String
)

private val welcomeLanguageOptions = listOf(
    WelcomeLanguageOption("zh-CN", "简体中文（中国大陆）"),
    WelcomeLanguageOption("zh-TW", "繁體中文（中國台灣）"),
    WelcomeLanguageOption("zh-HK", "繁體中文（中國香港）"),
    WelcomeLanguageOption("zh-MO", "繁體中文（中國澳門）"),
    WelcomeLanguageOption("ko", "한국어"),
    WelcomeLanguageOption("ja", "日本語"),
    WelcomeLanguageOption("en", "English")
)

private fun defaultWelcomeLanguage(savedLanguage: String): String {
    if (welcomeLanguageOptions.any { it.languageTag == savedLanguage }) return savedLanguage

    return when (Locale.getDefault().language) {
        "zh" -> when (Locale.getDefault().country.uppercase(Locale.ROOT)) {
            "TW" -> "zh-TW"
            "HK" -> "zh-HK"
            "MO" -> "zh-MO"
            else -> "zh-CN"
        }
        "ko" -> "ko"
        "ja" -> "ja"
        else -> "en"
    }
}

@Composable
fun WelcomeScreen(
    isNewInstallation: Boolean,
    shouldShowLanguageSetup: Boolean,
    initialLanguage: String,
    initialEInkMode: Boolean,
    isEInkMode: Boolean,
    isDark: Boolean,
    isLiquidGlass: Boolean,
    onFinished: () -> Unit,
    onOpenSponsor: () -> Unit,
    onLanguageSetupComplete: (language: String, eInkEnabled: Boolean) -> Unit,
    onEnableLiquidGlass: () -> Unit,
    startOnIntroduction: Boolean = false,
    startOnSupport: Boolean = false,
    startOnLiquidGlassPreview: Boolean = false,
    allowLiquidGlassReplay: Boolean = false
) {
    val motionEnabled = LocalMotionEnabled.current
    val pages = remember(shouldShowLanguageSetup, isNewInstallation, isEInkMode) {
        welcomePages(shouldShowLanguageSetup, isNewInstallation, isEInkMode)
    }
    var currentPage by rememberSaveable {
        mutableStateOf(
            when {
                startOnSupport && WelcomePage.SUPPORT in pages -> WelcomePage.SUPPORT
                startOnLiquidGlassPreview && WelcomePage.LIQUID_GLASS_PREVIEW in pages -> WelcomePage.LIQUID_GLASS_PREVIEW
                startOnIntroduction && WelcomePage.INTRODUCTION in pages -> WelcomePage.INTRODUCTION
                else -> pages.first()
            }
        )
    }
    val pageIndex = pages.indexOf(currentPage)

    LaunchedEffect(pages) {
        if (currentPage !in pages) currentPage = WelcomePage.INTRODUCTION
    }

    val predictiveBackProgress = ConfigurableBackHandler(
        enabled = pageIndex > 0
    ) {
        currentPage = pages[(pageIndex - 1).coerceAtLeast(0)]
    }

    val backVisualProgress = if (predictiveBackProgress >= 1f) 0f else predictiveBackProgress
    AnimatedContent(
        targetState = currentPage,
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
            .graphicsLayer {
                if (!isEInkMode && motionEnabled) {
                    translationX = backVisualProgress * size.width * 0.12f
                    alpha = 1f - backVisualProgress * 0.1f
                }
            },
        transitionSpec = {
            if (isEInkMode) {
                EnterTransition.None togetherWith ExitTransition.None
            } else if (!motionEnabled) {
                fadeIn(tween(140)) togetherWith fadeOut(tween(120))
            } else if (targetState.ordinal > initialState.ordinal) {
                (slideInHorizontally(
                    animationSpec = tween(520, easing = AppEasing.Smooth),
                    initialOffsetX = { it }
                ) + fadeIn(tween(420, delayMillis = 80, easing = AppEasing.Decelerate))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(520, easing = AppEasing.Smooth),
                        targetOffsetX = { -it / 4 }
                    ) + fadeOut(tween(360, easing = AppEasing.Accelerate)))
            } else {
                (slideInHorizontally(
                    animationSpec = tween(520, easing = AppEasing.Smooth),
                    initialOffsetX = { -it }
                ) + fadeIn(tween(420, delayMillis = 80, easing = AppEasing.Decelerate))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(520, easing = AppEasing.Smooth),
                        targetOffsetX = { it / 4 }
                    ) + fadeOut(tween(360, easing = AppEasing.Accelerate)))
            }
        },
        label = "welcomePage"
    ) { page ->
        when (page) {
            WelcomePage.LANGUAGE_SETUP -> LanguageAndEInkPage(
                initialLanguage = initialLanguage,
                initialEInkMode = initialEInkMode,
                isDark = isDark,
                onNext = onLanguageSetupComplete
            )

            WelcomePage.INTRODUCTION -> WelcomeIntroductionPage(
                isDark = isDark,
                isEInkMode = isEInkMode,
                onContinue = { currentPage = pages[(pages.indexOf(WelcomePage.INTRODUCTION) + 1).coerceAtMost(pages.lastIndex)] }
            )

            WelcomePage.LIQUID_GLASS_PREVIEW -> UpdatePreviewPage(
                preview = UpdatePreview.LIQUID_GLASS,
                isEInkMode = isEInkMode,
                isLiquidGlass = isLiquidGlass,
                allowLiquidGlassReplay = allowLiquidGlassReplay,
                onBack = { currentPage = pages[(pages.indexOf(page) - 1).coerceAtLeast(0)] },
                onNext = { currentPage = pages[(pages.indexOf(page) + 1).coerceAtMost(pages.lastIndex)] },
                onEnableLiquidGlass = onEnableLiquidGlass
            )

            // 官方版替换为会员试用引导；OSS 保留原赞助页。
            WelcomePage.SUPPORT -> com.huangder.lumibooks.edition.EditionWelcomeFinalPage(
                isDark = isDark,
                isLiquidGlass = isLiquidGlass,
                onFinished = onFinished,
                default = {
                    SupportProjectPage(
                        isDark = isDark,
                        isLiquidGlass = isLiquidGlass,
                        onOpenSponsor = onOpenSponsor,
                        onFinished = onFinished
                    )
                }
            )
        }
    }
}

@Composable
private fun UpdatePreviewPage(
    preview: UpdatePreview,
    isEInkMode: Boolean,
    isLiquidGlass: Boolean,
    allowLiquidGlassReplay: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onEnableLiquidGlass: () -> Unit
) {
    val animateEntrance = LocalMotionEnabled.current && !isEInkMode
    var hasEntered by remember(preview) { mutableStateOf(!animateEntrance) }
    var liquidGlassSelected by rememberSaveable { mutableStateOf(isLiquidGlass) }
    var themeSwitchStage by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(preview, isEInkMode) {
        if (animateEntrance) delay(delayMillis(40L))
        hasEntered = true
    }
    LaunchedEffect(isLiquidGlass) {
        if (isLiquidGlass) liquidGlassSelected = true
    }

    val title = when (preview) {
        UpdatePreview.MINERU -> stringResource(R.string.welcome_mineru_preview_title)
        UpdatePreview.LIQUID_GLASS -> stringResource(R.string.welcome_liquid_glass_title)
        UpdatePreview.BOOKSHELF -> stringResource(R.string.welcome_bookshelf_preview_title)
        UpdatePreview.EXCERPT -> stringResource(R.string.welcome_excerpt_title)
        UpdatePreview.G2 -> stringResource(R.string.welcome_g2_title)
        UpdatePreview.DICTIONARY -> stringResource(R.string.welcome_dictionary_title)
    }
    val subtitle = when (preview) {
        UpdatePreview.MINERU -> stringResource(R.string.welcome_mineru_preview_subtitle)
        UpdatePreview.LIQUID_GLASS -> stringResource(R.string.welcome_liquid_glass_subtitle)
        UpdatePreview.BOOKSHELF -> stringResource(R.string.welcome_bookshelf_preview_subtitle)
        UpdatePreview.EXCERPT -> stringResource(R.string.welcome_excerpt_subtitle)
        UpdatePreview.G2 -> stringResource(R.string.welcome_g2_subtitle)
        UpdatePreview.DICTIONARY -> stringResource(R.string.welcome_dictionary_subtitle)
    }
    val textPrimary = AppColors.TextPrimary
    val textSecondary = AppColors.TextSecondary
    val frameColor = AppColors.BgGray
    val titleProgress by animateFloatAsState(
        targetValue = if (hasEntered) 1f else 0f,
        animationSpec = if (!animateEntrance) snap() else tween(360, easing = AppEasing.Decelerate),
        label = "previewTitleProgress"
    )
    val artworkProgress by animateFloatAsState(
        targetValue = if (hasEntered) 1f else 0f,
        animationSpec = if (!animateEntrance) snap() else spring(dampingRatio = 0.72f, stiffness = 330f),
        label = "previewArtworkProgress"
    )
    val buttonsProgress by animateFloatAsState(
        targetValue = if (hasEntered) 1f else 0f,
        animationSpec = if (!animateEntrance) snap() else tween(360, delayMillis = 110, easing = AppEasing.Decelerate),
        label = "previewButtonsProgress"
    )
    val useLiquidGlassButtons = (isLiquidGlass || liquidGlassSelected) && !isEInkMode
    val shockwaveTier = remember { welcomeShockwaveTier(android.os.Build.MODEL.orEmpty()) }
    val shockwaveBandHeightPx = with(LocalDensity.current) { 110.dp.toPx() }
    val shockwaveProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    var shockwaveRun by remember { mutableIntStateOf(0) }
    val shockwaveDuration = if (LocalMotionPreference.current == MotionPreference.REDUCED) 600 else 1_000
    LaunchedEffect(shockwaveRun) {
        if (shockwaveRun > 0) {
            shockwaveProgress.snapTo(0f)
            shockwaveProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = shockwaveDuration, easing = ShockwaveEaseIn)
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
            .safeDrawingPadding()
    ) {
        val compact = maxHeight < 600.dp
        val artworkHeight = (maxHeight - if (preview == UpdatePreview.LIQUID_GLASS) 340.dp else 274.dp)
            .coerceIn(200.dp, 720.dp)
        Column(
            modifier = Modifier
                .widthIn(max = WelcomeContentMaxWidth)
                .fillMaxWidth()
                .fillMaxHeight()
                .align(Alignment.TopCenter)
                    .welcomeShockwavePageEffect(
                        progress = shockwaveProgress.value,
                        bandHeightPx = shockwaveBandHeightPx,
                        isDark = LocalIsDarkTheme.current,
                        tier = shockwaveTier,
                        reducedMotion = LocalMotionPreference.current == MotionPreference.REDUCED || LocalEInkMode.current,
                        samplingQuality = LocalLiquidGlassSamplingQuality.current
                    ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(if (compact) 16.dp else 36.dp))

                Column(
                    modifier = Modifier.graphicsLayer {
                        alpha = titleProgress
                        translationX = (1f - titleProgress) * 28.dp.toPx()
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        fontSize = 29.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = AccentColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = subtitle,
                        modifier = Modifier.padding(horizontal = 28.dp),
                        fontSize = AppType.Body,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                UpdatePreviewArtwork(
                    preview = preview,
                    frameColor = frameColor,
                    textSecondary = textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(artworkHeight)
                        .padding(horizontal = 32.dp)
                        .graphicsLayer {
                            alpha = artworkProgress
                            scaleX = 0.90f + artworkProgress * 0.10f
                            scaleY = 0.90f + artworkProgress * 0.10f
                            translationX = (1f - artworkProgress) * 36.dp.toPx()
                        }
                )

                Spacer(modifier = Modifier.height(20.dp))
            }

            if (preview == UpdatePreview.LIQUID_GLASS) {
                WelcomeActionButton(
                    text = if (liquidGlassSelected) stringResource(R.string.welcome_liquid_glass_enabled) else stringResource(R.string.welcome_enable_liquid_glass),
                    onClick = {
                        if ((!liquidGlassSelected || allowLiquidGlassReplay) && themeSwitchStage == 0) {
                            themeSwitchStage = 1
                        }
                    },
                    primary = true,
                    forceLiquidGlass = useLiquidGlassButtons,
                    textPrimary = textPrimary,
                    enabled = (!liquidGlassSelected || allowLiquidGlassReplay) && themeSwitchStage == 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .heightIn(min = 52.dp)
                        .graphicsLayer {
                            alpha = buttonsProgress
                            translationX = (1f - buttonsProgress) * 24.dp.toPx()
                        }
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .graphicsLayer {
                        alpha = buttonsProgress
                        translationX = (1f - buttonsProgress) * 24.dp.toPx()
                    },
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                WelcomeActionButton(
                    text = stringResource(R.string.welcome_previous),
                    onClick = onBack,
                    primary = false,
                    forceLiquidGlass = useLiquidGlassButtons,
                    textPrimary = textPrimary,
                    secondaryContainerColor = AppColors.BgGray,
                    enabled = themeSwitchStage == 0,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                )
                WelcomeActionButton(
                    text = stringResource(R.string.welcome_next),
                    onClick = onNext,
                    primary = true,
                    forceLiquidGlass = useLiquidGlassButtons,
                    textPrimary = textPrimary,
                    enabled = themeSwitchStage == 0,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                )
            }

            Spacer(modifier = Modifier.height(if (compact) 12.dp else 24.dp))
        }

        if (themeSwitchStage != 0) {
            ThemeSwitchOverlay(
                stage = themeSwitchStage,
                isDark = LocalIsDarkTheme.current,
                isEInkMode = isEInkMode,
                progress = shockwaveProgress.value,
                tier = shockwaveTier,
                samplingQuality = LocalLiquidGlassSamplingQuality.current,
                reducedMotion = LocalMotionPreference.current == MotionPreference.REDUCED,
                onApplyTheme = {
                    liquidGlassSelected = true
                    onEnableLiquidGlass()
                },
                onWaveStart = {
                    themeSwitchStage = 2
                    shockwaveRun++
                },
                onStageChange = { themeSwitchStage = it },
                onFinished = { themeSwitchStage = 0 }
            )
        }
    }
}

@Composable
private fun UpdatePreviewArtwork(
    preview: UpdatePreview,
    frameColor: Color,
    textSecondary: Color,
    modifier: Modifier = Modifier
) {
    when (preview) {
        UpdatePreview.MINERU -> PhonePreviewFrame(
            imageRes = R.drawable.welcome_mineru_preview,
            description = stringResource(R.string.welcome_mineru_preview_description),
            frameColor = frameColor,
            modifier = modifier
        )

        UpdatePreview.BOOKSHELF -> PhonePreviewFrame(
            imageRes = R.drawable.welcome_bookshelf_preview,
            description = stringResource(R.string.welcome_bookshelf_preview_description),
            frameColor = frameColor,
            modifier = modifier
        )

        UpdatePreview.EXCERPT -> PhonePreviewFrame(
            imageRes = R.drawable.welcome_excerpt_preview,
            description = stringResource(R.string.welcome_excerpt_description),
            frameColor = frameColor,
            modifier = modifier
        )

        UpdatePreview.G2 -> PhonePreviewFrame(
            imageRes = R.drawable.welcome_g2_preview,
            description = stringResource(R.string.welcome_g2_description),
            frameColor = frameColor,
            modifier = modifier
        )

        UpdatePreview.DICTIONARY -> PhonePreviewFrame(
            imageRes = R.drawable.welcome_dictionary_preview,
            description = stringResource(R.string.welcome_dictionary_description),
            frameColor = frameColor,
            modifier = modifier
        )

        UpdatePreview.LIQUID_GLASS -> LiquidGlassPreviewFrame(
            frameColor = frameColor,
            textSecondary = textSecondary,
            modifier = modifier
        )
    }
}

@Composable
private fun PhonePreviewFrame(
    imageRes: Int,
    description: String,
    frameColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(AppRoundedCornerShape(36.dp))
            .background(frameColor),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = description,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun LiquidGlassPreviewFrame(
    frameColor: Color,
    textSecondary: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(AppRoundedCornerShape(36.dp))
            .background(frameColor)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LiquidGlassPreviewTile(
            imageRes = R.drawable.welcome_glass_home_preview,
            description = stringResource(R.string.welcome_liquid_glass_home_description),
            alignment = Alignment.BottomCenter,
            modifier = Modifier.weight(1f)
        )
        LiquidGlassPreviewTile(
            imageRes = R.drawable.welcome_glass_dialog_preview,
            description = stringResource(R.string.welcome_liquid_glass_dialog_description),
            alignment = Alignment.Center,
            modifier = Modifier.weight(1f)
        )
        LiquidGlassPreviewTile(
            imageRes = R.drawable.welcome_glass_reader_preview,
            description = stringResource(R.string.welcome_liquid_glass_reader_description),
            alignment = Alignment.BottomCenter,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.welcome_liquid_glass_preview_caption),
            fontSize = AppType.Footnote,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            color = textSecondary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LiquidGlassPreviewTile(
    imageRes: Int,
    description: String,
    alignment: Alignment,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppRoundedCornerShape(24.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = description,
            modifier = Modifier
                .fillMaxWidth(0.64f)
                .fillMaxHeight(),
            contentScale = ContentScale.Crop,
            alignment = alignment
        )
    }
}

@Composable
private fun ThemeSwitchOverlay(
    stage: Int,
    isDark: Boolean,
    isEInkMode: Boolean,
    progress: Float,
    tier: WelcomeShockwaveTier,
    samplingQuality: com.huangder.lumibooks.domain.model.LiquidGlassSamplingQuality,
    reducedMotion: Boolean,
    onApplyTheme: () -> Unit,
    onWaveStart: () -> Unit,
    onStageChange: (Int) -> Unit,
    onFinished: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(delayMillis(40L))
        onWaveStart()
        onApplyTheme()
        delay(delayMillis(if (reducedMotion) 600L else 1_000L))
        onStageChange(3)
        delay(delayMillis(180L))
        onFinished()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { }
            }
    ) {
        val shaderHandlesShockwave = android.os.Build.VERSION.SDK_INT >= 33 &&
            !reducedMotion && tier != WelcomeShockwaveTier.HALO_ONLY
        if (!isEInkMode && !shaderHandlesShockwave) {
            WelcomeShockwave(
                progress = progress,
                isDark = isDark,
                tier = tier,
                samplingQuality = samplingQuality,
                reducedMotion = reducedMotion
            )
        }
    }
}

private val ShockwaveEaseIn = androidx.compose.animation.core.Easing { fraction -> fraction * fraction }

@Composable
private fun WelcomeActionButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean,
    forceLiquidGlass: Boolean,
    textPrimary: Color,
    secondaryContainerColor: Color = LightBgGray,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val shape = AppRoundedCornerShape(28.dp)
    val contentColor = if (primary) AppColors.OnAccent else textPrimary

    if (forceLiquidGlass) {
        CompositionLocalProvider(
            LocalAppTheme provides "liquid_glass"
        ) {
            LiquidGlassButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = shape,
                tintedColor = null,
                contentColor = if (primary) AccentColor else textPrimary
            ) {
                Text(
                    text = text,
                    color = if (primary) AccentColor else textPrimary,
                    fontSize = AppType.Body,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (primary) AccentColor else secondaryContainerColor,
                contentColor = contentColor
            )
        ) {
            Text(
                text = text,
                fontSize = AppType.Body,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LanguageAndEInkPage(
    initialLanguage: String,
    initialEInkMode: Boolean,
    isDark: Boolean,
    onNext: (language: String, eInkEnabled: Boolean) -> Unit
) {
    var selectedLanguage by rememberSaveable {
        mutableStateOf(defaultWelcomeLanguage(initialLanguage))
    }
    var eInkEnabled by rememberSaveable { mutableStateOf(initialEInkMode) }
    var isApplying by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val selectedLanguageContext = remember(context, selectedLanguage) {
        LocaleHelper.wrapContext(context, selectedLanguage)
    }
    val languageTitle = selectedLanguageContext.getString(R.string.welcome_language_title)
    val languageSubtitle = selectedLanguageContext.getString(R.string.welcome_language_subtitle)
    val eInkTitle = selectedLanguageContext.getString(R.string.label_e_ink_mode)
    val eInkDescription = selectedLanguageContext.getString(R.string.e_ink_mode_description)
    val nextLabel = selectedLanguageContext.getString(R.string.welcome_next)
    val textPrimary = if (isDark) Color.White else Color.Black
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val panelColor = if (isDark) DarkBgGray else LightBgGray
    val selectedRowColor = if (isDark) DarkCardBg else LightCardBg

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) DarkBackground else LightBackground)
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 62.dp, bottom = 20.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 40.dp)) {
                Text(
                    text = languageTitle,
                    fontSize = AppType.Display,
                    lineHeight = 39.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = languageSubtitle,
                    fontSize = AppType.Headline,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = textSecondary
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppRoundedCornerShape(28.dp))
                        .background(panelColor)
                        .padding(12.dp)
                ) {
                    welcomeLanguageOptions.forEach { option ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(AppRoundedCornerShape(14.dp))
                                .background(if (option.languageTag == selectedLanguage) selectedRowColor else Color.Transparent)
                                .pointerInput(option.languageTag) {
                                    detectTapGestures { selectedLanguage = option.languageTag }
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = option.label,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                fontSize = AppType.Headline,
                                fontWeight = if (option.languageTag == selectedLanguage) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (option.languageTag == selectedLanguage) AccentColor else textPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(54.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = eInkTitle,
                            fontSize = AppType.Section,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = eInkDescription,
                            fontSize = AppType.Caption,
                            lineHeight = 17.sp,
                            color = textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = eInkEnabled,
                        onCheckedChange = { eInkEnabled = it },
                        enabled = !isApplying,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentColor,
                            uncheckedThumbColor = if (isDark) Color(0xFFC7C7CC) else Color.White,
                            uncheckedTrackColor = if (isDark) Color(0xFF505055) else Color(0xFFD5D5DA)
                        )
                    )
                }
            }
        }

        WelcomeActionButton(
            text = nextLabel,
            onClick = {
                if (!isApplying) {
                    isApplying = true
                    onNext(selectedLanguage, eInkEnabled)
                }
            },
            primary = true,
            forceLiquidGlass = false,
            textPrimary = textPrimary,
            enabled = !isApplying,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .height(52.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun WelcomeIntroductionPage(
    isDark: Boolean,
    isEInkMode: Boolean,
    onContinue: () -> Unit
) {
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showTermsOfService by remember { mutableStateOf(false) }
    val privacyColor = when {
        isEInkMode -> Color.Black
        isDark -> AccentColor.copy(alpha = 0.62f)
        else -> AccentColor.copy(alpha = 0.58f)
    }
    val linkColor = when {
        isEInkMode -> Color.Black
        else -> AccentColor
    }

    Box(modifier = Modifier.fillMaxSize()) {
        WelcomeGradientBackground(
            isDark = isDark,
            isEInkMode = isEInkMode
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                WelcomeHeroText(
                    text = stringResource(R.string.welcome_title),
                    fontSize = 21.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Medium,
                    isEInkMode = isEInkMode
                )
                Spacer(modifier = Modifier.height(6.dp))
                WelcomeHeroText(
                    text = stringResource(R.string.welcome_brand_name),
                    fontSize = 37.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Medium,
                    isEInkMode = isEInkMode
                )
                Spacer(modifier = Modifier.height(18.dp))
                WelcomeHeroText(
                    text = stringResource(R.string.welcome_major_version),
                    fontSize = 167.sp,
                    lineHeight = 176.sp,
                    fontWeight = FontWeight.Medium,
                    outlineWidth = 1.33.dp,
                    useOpacityGradient = true,
                    isEInkMode = isEInkMode
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(modifier = Modifier.padding(horizontal = 32.dp)) {
                val privacyText = stringResource(R.string.welcome_privacy)
                val andText = stringResource(R.string.welcome_and)
                val termsText = stringResource(R.string.welcome_terms)
                val privacySummary = stringResource(R.string.welcome_privacy_summary)
                val policyPunctuation = stringResource(R.string.welcome_policy_punctuation)
                val annotatedText = buildAnnotatedString {
                    append(privacySummary)
                    pushStringAnnotation(tag = "PRIVACY", annotation = "privacy")
                    withStyle(style = SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)) {
                        append(privacyText)
                    }
                    pop()
                    append(andText)
                    pushStringAnnotation(tag = "TERMS", annotation = "terms")
                    withStyle(style = SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)) {
                        append(termsText)
                    }
                    pop()
                    append(policyPunctuation)
                }
                var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                BasicText(
                    text = annotatedText,
                    style = androidx.compose.ui.text.TextStyle(
                        color = privacyColor,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = AppType.Caption2,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.sp,
                        textAlign = TextAlign.Start
                    ),
                    onTextLayout = { textLayoutResult = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(annotatedText) {
                        detectTapGestures { offset ->
                            textLayoutResult?.let { layoutResult ->
                                val position = layoutResult.getOffsetForPosition(offset)
                                val privacyAnnotations = annotatedText.getStringAnnotations(
                                    tag = "PRIVACY",
                                    start = position,
                                    end = position
                                )
                                val termsAnnotations = annotatedText.getStringAnnotations(
                                    tag = "TERMS",
                                    start = position,
                                    end = position
                                )
                                when {
                                    privacyAnnotations.isNotEmpty() -> showPrivacyPolicy = true
                                    termsAnnotations.isNotEmpty() -> showTermsOfService = true
                                }
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            WelcomeStartButton(
                text = stringResource(R.string.welcome_start_using),
                isDark = isDark,
                isEInkMode = isEInkMode,
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(52.dp)
            )
        }

        AnimatedVisibility(
            visible = showPrivacyPolicy,
            enter = fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
        ) {
            PolicyBottomSheet(
                title = stringResource(R.string.welcome_privacy_title),
                content = getPrivacyPolicyContent(),
                isDark = isDark,
                visible = showPrivacyPolicy,
                onDismiss = { showPrivacyPolicy = false }
            )
        }

        AnimatedVisibility(
            visible = showTermsOfService,
            enter = fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
        ) {
            PolicyBottomSheet(
                title = stringResource(R.string.welcome_terms_title),
                content = getTermsOfServiceContent(),
                isDark = isDark,
                visible = showTermsOfService,
                onDismiss = { showTermsOfService = false }
            )
        }
    }
}

@Composable
private fun SupportProjectPage(
    isDark: Boolean,
    isLiquidGlass: Boolean,
    onOpenSponsor: () -> Unit,
    onFinished: () -> Unit
) {
    var entranceStage by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(delayMillis(70L))
        entranceStage = 1
        delay(delayMillis(150L))
        entranceStage = 2
        delay(delayMillis(160L))
        entranceStage = 3
        delay(delayMillis(120L))
        entranceStage = 4
        delay(delayMillis(150L))
        entranceStage = 5
    }

    val backgroundColor = if (isDark) DarkBackground else LightBackground
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val copyProgress by animateFloatAsState(
        targetValue = if (entranceStage >= 3) 1f else 0f,
        animationSpec = tween(420, easing = AppEasing.Decelerate),
        label = "supportCopyProgress"
    )
    val buttonProgress by animateFloatAsState(
        targetValue = if (entranceStage >= 5) 1f else 0f,
        animationSpec = tween(420, easing = AppEasing.Decelerate),
        label = "supportButtonProgress"
    )
    val messageProgress by animateFloatAsState(
        targetValue = if (entranceStage >= 4) 1f else 0f,
        animationSpec = tween(420, easing = AppEasing.Decelerate),
        label = "supportMessageProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.48f))

        Text(
            text = stringResource(R.string.welcome_support_title),
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .widthIn(max = 360.dp)
                .graphicsLayer {
                    alpha = copyProgress
                    translationY = (1f - copyProgress) * 18.dp.toPx()
                },
            fontSize = 25.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = AccentColor
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.welcome_support_message),
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .widthIn(max = 360.dp)
                .graphicsLayer {
                    alpha = messageProgress
                    translationY = (1f - messageProgress) * 16.dp.toPx()
                },
            fontSize = AppType.Subhead,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            color = textSecondary
        )

        Spacer(modifier = Modifier.weight(0.52f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    alpha = buttonProgress
                    translationY = (1f - buttonProgress) * 16.dp.toPx()
                },
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SupportWelcomeButton(
                text = stringResource(R.string.welcome_open_sponsor),
                onClick = onOpenSponsor,
                primary = false,
                isLiquidGlass = isLiquidGlass,
                isDark = isDark,
                enabled = entranceStage >= 5,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            )
            SupportWelcomeButton(
                text = stringResource(R.string.welcome_start_using),
                onClick = onFinished,
                primary = true,
                isLiquidGlass = isLiquidGlass,
                isDark = isDark,
                enabled = entranceStage >= 5,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** Shared with the edition-specific last welcome page so both match the same button style. */
@Composable
internal fun SupportWelcomeButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean,
    isLiquidGlass: Boolean,
    isDark: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isLiquidGlass) {
        WelcomeActionButton(
            text = text,
            onClick = onClick,
            primary = primary,
            forceLiquidGlass = false,
            textPrimary = if (isDark) Color.White else Color.Black,
            secondaryContainerColor = if (isDark) DarkBgGray else LightBgGray,
            enabled = enabled,
            modifier = modifier
        )
        return
    }

    val shape = AppRoundedCornerShape(28.dp)
    val glassColor = when {
        primary -> AccentColor
        isDark -> Color(0xFF2D2D31)
        else -> Color(0xFFFFFFFF)
    }
    val scrimColor = if (primary) {
        glassColor.copy(alpha = 0.84f)
    } else {
        glassColor.copy(alpha = if (isDark) 0.28f else 0.48f)
    }
    val contentColor = when {
        primary -> AppColors.OnAccent
        isDark -> Color.White
        else -> Color.Black
    }

    CompositionLocalProvider(
        LocalAppTheme provides "liquid_glass",
        LocalLiquidGlassTransparency provides 0.65f
    ) {
        LiquidGlassSurface(
            controlEdge = true,
            shape = shape,
            fallbackColor = glassColor,
            contentScrimColor = scrimColor,
            transparencyOverride = 0.65f,
            enabled = enabled,
            onClick = onClick,
            decorationModifier = Modifier,
            modifier = modifier.graphicsLayer { alpha = if (enabled) 1f else 0.48f }
        ) {
            Text(
                text = text,
                color = contentColor,
                fontSize = AppType.Body,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun WelcomeHeroText(
    text: String,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    isEInkMode: Boolean,
    outlineWidth: Dp = 0.dp,
    useOpacityGradient: Boolean = false
) {
    val outlineWidthPx = with(LocalDensity.current) { outlineWidth.toPx() }
    val fillBrush = when {
        isEInkMode -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
        useOpacityGradient -> Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.82f), Color.White.copy(alpha = 0.06f))
        )
        else -> Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.70f), Color.White.copy(alpha = 0.70f))
        )
    }
    val outlineBrush = when {
        isEInkMode -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
        useOpacityGradient -> Brush.verticalGradient(
            listOf(Color.White, Color.White.copy(alpha = 0.08f))
        )
        else -> Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.90f), Color.White.copy(alpha = 0.90f))
        )
    }
    val baseStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        letterSpacing = 0.sp,
        textAlign = TextAlign.Center
    )

    Box(contentAlignment = Alignment.Center) {
        if (outlineWidth > 0.dp) {
            Text(
                text = text,
                modifier = Modifier.clearAndSetSemantics {},
                maxLines = 1,
                style = baseStyle.copy(
                    brush = outlineBrush,
                    drawStyle = Stroke(width = outlineWidthPx)
                )
            )
        }
        Text(
            text = text,
            maxLines = 1,
            style = baseStyle.copy(
                brush = fillBrush,
                drawStyle = Fill
            )
        )
    }
}

@Composable
private fun WelcomeGradientBackground(
    isDark: Boolean,
    isEInkMode: Boolean
) {
    val motionEnabled = LocalMotionEnabled.current && !isEInkMode
    val accentColor = AccentColor
    val accentArgb = accentColor.toArgb()
    val backgroundColor = if (isDark) {
        Color(blendAppAccentArgb(0xFF000000.toInt(), accentArgb, 0.10f))
    } else {
        Color(blendAppAccentArgb(0xFFFFFFFF.toInt(), accentArgb, 0.04f))
    }
    val darkTopColor = Color(blendAppAccentArgb(0xFF000000.toInt(), accentArgb, 0.48f))
    val darkMiddleColor = Color(blendAppAccentArgb(0xFF000000.toInt(), accentArgb, 0.26f))
    val progress = if (motionEnabled) {
        val transition = rememberInfiniteTransition(label = "welcomeGradientFlow")
        val first by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(18_000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "welcomeGradientFirst"
        )
        val second by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(23_000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "welcomeGradientSecond"
        )
        val third by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(29_000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "welcomeGradientThird"
        )
        Triple(first, second, third)
    } else {
        Triple(0.5f, 0.5f, 0.5f)
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (isEInkMode) {
            drawRect(Color.White)
            return@Canvas
        }

        drawRect(backgroundColor)
        drawRect(
            brush = Brush.verticalGradient(
                0f to if (isDark) darkTopColor else accentColor,
                0.52f to if (isDark) darkMiddleColor else accentColor.copy(alpha = 0.62f),
                1f to Color.Transparent,
                startY = 0f,
                endY = size.height
            )
        )

        val longestSide = maxOf(size.width, size.height)
        fun center(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            value: Float
        ) = Offset(
            x = size.width * (startX + (endX - startX) * value),
            y = size.height * (startY + (endY - startY) * value)
        )

        fun drawAccentField(center: Offset, radius: Float, alpha: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = alpha),
                        accentColor.copy(alpha = alpha * 0.42f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        val darkAlphaScale = if (isDark) 0.42f else 1f
        val firstCenter = center(-0.08f, 0.20f, 0.05f, 0.28f, progress.first)
        val secondCenter = center(0.88f, 0.35f, 0.74f, 0.47f, progress.second)
        val thirdCenter = center(0.18f, 0.70f, 0.34f, 0.63f, progress.third)
        drawAccentField(firstCenter, longestSide * 0.68f, 0.58f * darkAlphaScale)
        drawAccentField(secondCenter, longestSide * 0.62f, 0.54f * darkAlphaScale)
        drawAccentField(thirdCenter, longestSide * 0.74f, 0.38f * darkAlphaScale)

        val highlightCenter = center(0.82f, 0.48f, 0.68f, 0.57f, progress.second)
        val highlightRadius = longestSide * 0.52f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isDark) 0.08f else 0.72f),
                    Color.Transparent
                ),
                center = highlightCenter,
                radius = highlightRadius
            ),
            radius = highlightRadius,
            center = highlightCenter
        )
    }
}

@Composable
private fun WelcomeStartButton(
    text: String,
    isDark: Boolean,
    isEInkMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motionEnabled = LocalMotionEnabled.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (motionEnabled && pressed) 0.97f else 1f,
        animationSpec = if (!motionEnabled) {
            snap()
        } else {
            tween(
                durationMillis = if (pressed) 90 else 160,
                easing = FastOutSlowInEasing
            )
        },
        label = "welcomeStartButtonScale"
    )
    val shape = AppRoundedCornerShape(26.dp)
    val containerColor = when {
        isEInkMode -> Color.White
        isDark -> Color(
            blendAppAccentArgb(0xFF000000.toInt(), AccentColor.toArgb(), 0.14f)
        ).copy(alpha = 0.55f)
        else -> Color.White.copy(alpha = 0.48f)
    }
    val contentColor = when {
        isEInkMode -> Color.Black
        else -> AccentColor
    }
    val borderColor = if (isEInkMode) Color.Black else Color.White.copy(alpha = 0.90f)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            fontFamily = FontFamily.SansSerif,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun PolicyBottomSheet(
    title: String,
    content: String,
    isDark: Boolean,
    visible: Boolean,
    onDismiss: () -> Unit
) {
    val textPrimary = AppColors.TextPrimary
    val textSecondary = AppColors.TextSecondary
    val bgGray = AppColors.BgGray
    val cardBg = AppColors.CardBg

    // 容器滑入动画（独立于遮罩）
    val containerOffsetY = remember { androidx.compose.animation.core.Animatable(1f) }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler(onBack = onDismiss)

    LaunchedEffect(visible) {
        if (visible) {
            // 滑入动画（更快）
            containerOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )
        } else {
            // 滑出动画
            containerOffsetY.animateTo(
                targetValue = 1f,
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 遮罩层（由外层 AnimatedVisibility 控制渐显/渐隐）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    AppColors.Scrim.copy(alpha = if (isDark) 0.4f else 0.2f)
                )
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                }
        )

        // 容器层（滑入动画）；平板为居中卡片
        val sheetShape = adaptiveSheetShape(AppRoundedCornerShape(topStart = AppPresentation.SheetCorner, topEnd = AppPresentation.SheetCorner))
        Column(
            modifier = adaptiveSheetFrame(heightFraction = 0.88f)
                .materialBottomSheetMotion(containerOffsetY.value, predictiveBackProgress)
                .shadow(
                    elevation = 24.dp,
                    shape = sheetShape
                )
                .background(cardBg, sheetShape)
                .padding(top = 24.dp, start = 24.dp, end = 24.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = AppType.Section,
                    fontWeight = FontWeight.Bold,
                    fontFamily = resolveAppFontFamily(KaiTi),
                    color = textPrimary
                )

                Spacer(modifier = Modifier.weight(1f))

                // 关闭按钮
                LiquidGlassIconButton(
                    imageVector = AppIcons.X,
                    contentDescription = stringResource(R.string.close),
                    onClick = onDismiss,
                    size = 36.dp,
                    iconSize = 18.dp,
                    contentColor = AppColors.TextPrimary,
                    normalContainerColor = bgGray
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 内容（带格式化标题）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                FormattedPolicyContent(
                    content = content,
                    textColor = textPrimary,
                    secondaryColor = textSecondary
                )
            }
        }
    }
}

@Composable
private fun FormattedPolicyContent(
    content: String,
    textColor: Color,
    secondaryColor: Color
) {
    val lines = remember(content) {
        content
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
    }
    val firstNonBlankLine = lines.indexOfFirst { it.trim().isNotEmpty() }
    val hasContentAfterDocumentTitle = firstNonBlankLine >= 0 &&
        lines.drop(firstNonBlankLine + 1).any { it.trim().isNotEmpty() }
    var index = 0

    while (index < lines.size) {
        val line = lines[index].trim()

        when {
            // The sheet header already presents the document title. Only omit it when
            // there is a real document body; a collapsed resource must never become blank.
            index == firstNonBlankLine && hasContentAfterDocumentTitle -> {
                index++
                continue
            }

            line.isEmpty() -> {
                Spacer(modifier = Modifier.height(8.dp))
            }

            isPolicyMetadataLine(line) -> {
                val metadataLines = buildList {
                    while (index < lines.size && isPolicyMetadataLine(lines[index].trim())) {
                        add(lines[index].trim())
                        index++
                    }
                }
                PolicyMetadataBlock(
                    lines = metadataLines,
                    textColor = textColor,
                    secondaryColor = secondaryColor
                )
                Spacer(modifier = Modifier.height(14.dp))
                continue
            }

            isPolicyPrimaryHeading(line) -> {
                PolicySectionHeading(text = line, textColor = textColor)
            }

            isPolicySecondaryHeading(line) -> {
                Text(
                    text = line,
                    fontSize = AppType.Subhead,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    modifier = Modifier.padding(top = 12.dp, bottom = 5.dp)
                )
            }

            isPolicyNotice(line) -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppRoundedCornerShape(14.dp))
                        .background(AccentColor.copy(alpha = 0.11f))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = line,
                        fontSize = AppType.BodySmall,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            isPolicyBullet(line) -> {
                PolicyBulletItem(
                    text = line,
                    textColor = textColor,
                    secondaryColor = secondaryColor
                )
            }

            else -> {
                Text(
                    text = line,
                    fontSize = AppType.BodySmall,
                    lineHeight = 22.sp,
                    color = textColor,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
        }
        index++
    }
}

@Composable
private fun PolicyMetadataBlock(
    lines: List<String>,
    textColor: Color,
    secondaryColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppRoundedCornerShape(16.dp))
            .background(secondaryColor.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        lines.forEach { line ->
            val separator = line.indexOfFirst { it == '：' || it == ':' }
            if (separator > 0) {
                val label = line.substring(0, separator + 1)
                val value = line.substring(separator + 1).trimStart()
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = textColor)) {
                            append(label)
                        }
                        withStyle(SpanStyle(color = secondaryColor)) {
                            append("  ")
                            append(value)
                        }
                    },
                    fontSize = AppType.Caption,
                    lineHeight = 18.sp
                )
            } else {
                Text(
                    text = line,
                    fontSize = AppType.Caption,
                    lineHeight = 18.sp,
                    color = secondaryColor
                )
            }
        }
    }
}

@Composable
private fun PolicySectionHeading(
    text: String,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(22.dp)
                .clip(CircleShape)
                .background(AccentColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = AppType.Headline,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun PolicyBulletItem(
    text: String,
    textColor: Color,
    secondaryColor: Color
) {
    val (marker, body) = when {
        text.startsWith("❌") -> "✕" to text.removePrefix("❌").trimStart()
        text.startsWith("✓") || text.startsWith("✔") -> "✓" to text.drop(1).trimStart()
        text.startsWith("•") -> "•" to text.removePrefix("•").trimStart()
        text.startsWith("-") -> "•" to text.removePrefix("-").trimStart()
        else -> "•" to text
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = marker,
            fontSize = AppType.Body,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
            color = AccentColor
        )
        Spacer(modifier = Modifier.width(9.dp))
        Text(
            text = body,
            modifier = Modifier.weight(1f),
            fontSize = AppType.BodySmall,
            lineHeight = 22.sp,
            color = textColor
        )
    }
}

private fun isPolicyPrimaryHeading(line: String): Boolean {
    return line.matches(Regex("^[一二三四五六七八九十]+、\\s*.+")) ||
        (line.matches(Regex("^\\d+\\.\\s*[^\\d].*")) && !isPolicySecondaryHeading(line))
}

private fun isPolicySecondaryHeading(line: String): Boolean =
    line.matches(Regex("^\\d+\\.\\d+\\s*.+"))

private fun isPolicyBullet(line: String): Boolean =
    line.startsWith("•") || line.startsWith("-") || line.startsWith("❌") ||
        line.startsWith("✓") || line.startsWith("✔")

private fun isPolicyNotice(line: String): Boolean = listOf(
    "核心原则", "核心原則", "Core principle", "Core Principle",
    "基本方針", "基本原則", "핵심 원칙", "핵심 원칙：",
    "重要提示", "Important notice", "重要なお知らせ", "중요 안내"
).any(line::startsWith)

private fun isPolicyMetadataLine(line: String): Boolean = listOf(
    "生效日期", "最近更新", "应用名称", "包名", "开发者", "联系邮箱",
    "最後更新", "最后更新", "應用名稱", "應用程式名稱", "套件名稱", "開發者", "聯絡電郵", "聯絡電子郵件",
    "Effective date", "Last updated", "Application name", "App name", "Package name", "Developer", "Contact email", "Contact",
    "施行日", "最終更新日", "アプリ名", "パッケージ名", "開発者", "連絡先",
    "시행일", "최종 업데이트", "앱 이름", "패키지명", "패키지 이름", "개발자", "연락처"
).any(line::startsWith)
@Composable
private fun getPrivacyPolicyContent(): String = stringResource(R.string.welcome_privacy_content)

@Composable
private fun getTermsOfServiceContent(): String = stringResource(R.string.welcome_terms_content)
