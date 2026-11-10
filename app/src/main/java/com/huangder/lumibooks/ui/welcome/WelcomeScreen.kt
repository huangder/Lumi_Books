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
import com.huangder.lumibooks.ui.brand.LumiDawnBackground
import com.huangder.lumibooks.ui.brand.LumiDawnButton
import com.huangder.lumibooks.ui.brand.LumiWordmark
import com.huangder.lumibooks.ui.brand.lumiDawnColors
import com.huangder.lumibooks.ui.brand.lumiReveal
import com.huangder.lumibooks.ui.brand.rememberLumiReveal

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
    FEATURES,
    SUPPORT
}

internal fun welcomePages(
    shouldShowLanguageSetup: Boolean,
    isNewInstallation: Boolean,
    isEInkMode: Boolean
): List<WelcomePage> = buildList {
    if (shouldShowLanguageSetup) add(WelcomePage.LANGUAGE_SETUP)
    add(WelcomePage.INTRODUCTION)
    // The release's three headline updates, for everyone. New installs already start on Liquid
    // Glass, so there is no theme step any more.
    add(WelcomePage.FEATURES)
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
    startOnIntroduction: Boolean = false,
    startOnFeatures: Boolean = false,
    startOnSupport: Boolean = false
) {
    val motionEnabled = LocalMotionEnabled.current
    val pages = remember(shouldShowLanguageSetup, isNewInstallation, isEInkMode) {
        welcomePages(shouldShowLanguageSetup, isNewInstallation, isEInkMode)
    }
    var currentPage by rememberSaveable {
        mutableStateOf(
            when {
                startOnSupport && WelcomePage.SUPPORT in pages -> WelcomePage.SUPPORT
                startOnFeatures && WelcomePage.FEATURES in pages -> WelcomePage.FEATURES
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
    // The intro plays its wordmark entrance once; returning from the stories lands it in place.
    var introWordmarkPlayed by rememberSaveable { mutableStateOf(false) }
    val sharedMotion = motionEnabled && !isEInkMode
    // One stage for the whole flow: the glow drifts on uninterrupted while pages change, and under
    // Liquid Glass it is also the backdrop every glass control refracts.
    com.huangder.lumibooks.ui.brand.LumiDawnStage(Modifier.background(AppColors.WindowBg)) {
    androidx.compose.animation.SharedTransitionLayout {
    CompositionLocalProvider(LocalWelcomeSharedScope provides if (sharedMotion) this else null) {
    AnimatedContent(
        targetState = currentPage,
        modifier = Modifier
            .fillMaxSize()
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
            } else if (setOf(initialState, targetState) == setOf(WelcomePage.INTRODUCTION, WelcomePage.FEATURES)) {
                // Same backdrop on both sides: crossfade while "Filmy" flies between them.
                fadeIn(tween(460, delayMillis = 140, easing = AppEasing.Decelerate)) togetherWith
                    fadeOut(tween(260, easing = AppEasing.Accelerate))
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
        CompositionLocalProvider(LocalWelcomeAnimatedScope provides if (sharedMotion) this else null) {
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
                isNewInstallation = isNewInstallation,
                animateWordmark = !introWordmarkPlayed,
                onWordmarkPlayed = { introWordmarkPlayed = true },
                onContinue = { currentPage = pages[(pages.indexOf(WelcomePage.INTRODUCTION) + 1).coerceAtMost(pages.lastIndex)] }
            )

            WelcomePage.FEATURES -> WelcomeFeatureStoriesPage(
                hasReadingJournal = com.huangder.lumibooks.edition.editionHasReadingJournal,
                onContinue = { currentPage = pages[(pages.indexOf(page) + 1).coerceAtMost(pages.lastIndex)] }
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
    }
    }
    }
}

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

/**
 * The first welcome page, in the "Lumi Dawn" language: the same wordmark the splash shows,
 * now joined by a greeting (or an update notice) and the policy line above one ink button.
 */
@Composable
private fun WelcomeIntroductionPage(
    isDark: Boolean,
    isEInkMode: Boolean,
    isNewInstallation: Boolean,
    animateWordmark: Boolean,
    onWordmarkPlayed: () -> Unit,
    onContinue: () -> Unit
) {
    LaunchedEffect(Unit) { onWordmarkPlayed() }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showTermsOfService by remember { mutableStateOf(false) }
    val dawn = lumiDawnColors()
    val linkColor = if (isEInkMode) Color.Black else dawn.ink
    val greetingIn = rememberLumiReveal(index = 0, baseDelayMillis = 120L)
    val footerIn = rememberLumiReveal(index = 8, baseDelayMillis = 120L)

    Box(modifier = Modifier.fillMaxSize()) {
        LumiDawnBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(top = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = stringResource(if (isNewInstallation) R.string.welcome_title else R.string.welcome_update_title),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                letterSpacing = 0.04.sp,
                color = dawn.inkSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .lumiReveal(greetingIn, 8.dp)
            )
            Spacer(modifier = Modifier.height(26.dp))
            LumiWordmark(animate = animateWordmark, codenameModifier = Modifier.welcomeCodename())

            Spacer(modifier = Modifier.weight(1.25f))

            Column(
                modifier = Modifier
                    .widthIn(max = WelcomeContentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .lumiReveal(footerIn, 12.dp)
            ) {
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
                    style = TextStyle(
                        color = dawn.inkSecondary,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = AppType.Caption2,
                        lineHeight = 17.sp,
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
                                    when {
                                        annotatedText.getStringAnnotations("PRIVACY", position, position).isNotEmpty() ->
                                            showPrivacyPolicy = true
                                        annotatedText.getStringAnnotations("TERMS", position, position).isNotEmpty() ->
                                            showTermsOfService = true
                                    }
                                }
                            }
                        }
                )
                Spacer(modifier = Modifier.height(18.dp))
                LumiDawnButton(
                    text = stringResource(R.string.welcome_start_using),
                    onClick = onContinue,
                    style = com.huangder.lumibooks.ui.brand.LumiDawnButtonStyle.Frosted,
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
