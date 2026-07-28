package com.huangder.lumibooks.ui.welcome

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.components.ConfigurableBackHandler
import com.huangder.lumibooks.ui.components.ConfigurableBottomSheetBackHandler
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.materialBottomSheetMotion
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassTransparency
import com.huangder.lumibooks.util.LocaleHelper
import java.util.Locale
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

// 设计规范颜色 - 浅色模式
private val AccentColor = Color(0xFFE85D5D)
private val LightTextSecondary = Color(0xFF6E6E73)
private val LightBgGray = Color(0xFFF2F2F7)
private val LightBackground = Color(0xFFFBFBFC)
private val LightCardBg = Color.White

// 深色模式颜色
private val DarkTextSecondary = Color(0xFF98989D)
private val DarkBgGray = Color(0xFF2C2C2E)
private val DarkBackground = Color(0xFF000000)
private val DarkCardBg = Color(0xFF1C1C1E)
private val LightSupportBackground = Color(0xFFFFECEF)
private val DarkSupportBackground = Color(0xFF3A2429)

private enum class WelcomePage {
    LANGUAGE_SETUP,
    INTRODUCTION,
    LIQUID_GLASS_PREVIEW,
    SUPPORT
}

private enum class UpdatePreview {
    MINERU,
    LIQUID_GLASS,
    BOOKSHELF
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
    isUpdate: Boolean,
    isNewInstallation: Boolean,
    shouldShowLanguageSetup: Boolean,
    initialLanguage: String,
    initialEInkMode: Boolean,
    isEInkMode: Boolean,
    isDark: Boolean,
    isLiquidGlass: Boolean,
    onFinished: () -> Unit,
    onExit: () -> Unit,
    onOpenSponsor: () -> Unit,
    onLanguageSetupComplete: (language: String, eInkEnabled: Boolean) -> Unit,
    onEnableLiquidGlass: () -> Unit,
    startOnIntroduction: Boolean = false
) {
    val pages = remember(shouldShowLanguageSetup, isNewInstallation, isEInkMode) {
        buildList {
            if (shouldShowLanguageSetup) add(WelcomePage.LANGUAGE_SETUP)
            add(WelcomePage.INTRODUCTION)
            if (isNewInstallation && !isEInkMode) add(WelcomePage.LIQUID_GLASS_PREVIEW)
            add(WelcomePage.SUPPORT)
        }
    }
    var currentPage by rememberSaveable {
        mutableStateOf(
            if (startOnIntroduction && WelcomePage.INTRODUCTION in pages) {
                WelcomePage.INTRODUCTION
            } else {
                pages.first()
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

    AnimatedContent(
        targetState = currentPage,
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) DarkBackground else LightBackground)
            .graphicsLayer {
                if (!isEInkMode) {
                    translationX = predictiveBackProgress * size.width * 0.12f
                    alpha = 1f - predictiveBackProgress * 0.1f
                }
            },
        transitionSpec = {
            if (isEInkMode) {
                EnterTransition.None togetherWith ExitTransition.None
            } else if (targetState.ordinal > initialState.ordinal) {
                (slideInHorizontally(
                    animationSpec = spring(dampingRatio = 0.84f, stiffness = 460f),
                    initialOffsetX = { it }
                ) + fadeIn(tween(220)) + scaleIn(initialScale = 0.96f)) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(280, easing = AppEasing.Accelerate),
                        targetOffsetX = { -it / 4 }
                    ) + fadeOut(tween(180)) + scaleOut(targetScale = 0.98f))
            } else {
                (slideInHorizontally(
                    animationSpec = spring(dampingRatio = 0.84f, stiffness = 460f),
                    initialOffsetX = { -it / 3 }
                ) + fadeIn(tween(220)) + scaleIn(initialScale = 0.96f)) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(280, easing = AppEasing.Smooth),
                        targetOffsetX = { it }
                    ) + fadeOut(tween(180)) + scaleOut(targetScale = 0.98f))
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
                isUpdate = isUpdate,
                isDark = isDark,
                onContinue = { currentPage = pages[(pages.indexOf(WelcomePage.INTRODUCTION) + 1).coerceAtMost(pages.lastIndex)] },
                onExit = onExit
            )

            WelcomePage.LIQUID_GLASS_PREVIEW -> UpdatePreviewPage(
                preview = UpdatePreview.LIQUID_GLASS,
                isDark = isDark,
                isEInkMode = isEInkMode,
                isLiquidGlass = isLiquidGlass,
                onBack = { currentPage = pages[(pages.indexOf(WelcomePage.LIQUID_GLASS_PREVIEW) - 1).coerceAtLeast(0)] },
                onNext = { currentPage = WelcomePage.SUPPORT },
                onEnableLiquidGlass = onEnableLiquidGlass
            )

            WelcomePage.SUPPORT -> SupportProjectPage(
                isDark = isDark,
                isLiquidGlass = isLiquidGlass,
                onOpenSponsor = onOpenSponsor,
                onFinished = onFinished
            )
        }
    }
}

@Composable
private fun UpdatePreviewPage(
    preview: UpdatePreview,
    isDark: Boolean,
    isEInkMode: Boolean,
    isLiquidGlass: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onEnableLiquidGlass: () -> Unit,
    startOnIntroduction: Boolean = false
) {
    var hasEntered by remember(preview) { mutableStateOf(false) }
    var liquidGlassSelected by rememberSaveable { mutableStateOf(isLiquidGlass) }
    var themeSwitchStage by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(preview, isEInkMode) {
        if (!isEInkMode) delay(40)
        hasEntered = true
    }
    LaunchedEffect(isLiquidGlass) {
        if (isLiquidGlass) liquidGlassSelected = true
    }

    val title = when (preview) {
        UpdatePreview.MINERU -> stringResource(R.string.welcome_mineru_preview_title)
        UpdatePreview.LIQUID_GLASS -> stringResource(R.string.welcome_liquid_glass_title)
        UpdatePreview.BOOKSHELF -> stringResource(R.string.welcome_bookshelf_preview_title)
    }
    val subtitle = when (preview) {
        UpdatePreview.MINERU -> stringResource(R.string.welcome_mineru_preview_subtitle)
        UpdatePreview.LIQUID_GLASS -> stringResource(R.string.welcome_liquid_glass_subtitle)
        UpdatePreview.BOOKSHELF -> stringResource(R.string.welcome_bookshelf_preview_subtitle)
    }
    val textPrimary = if (isDark) Color.White else Color.Black
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val frameColor = if (isDark) Color(0xFF242426) else Color(0xFFF2F2F3)
    val titleProgress by animateFloatAsState(
        targetValue = if (hasEntered) 1f else 0f,
        animationSpec = if (isEInkMode) snap() else tween(360, easing = AppEasing.Decelerate),
        label = "previewTitleProgress"
    )
    val artworkProgress by animateFloatAsState(
        targetValue = if (hasEntered) 1f else 0f,
        animationSpec = if (isEInkMode) snap() else spring(dampingRatio = 0.72f, stiffness = 330f),
        label = "previewArtworkProgress"
    )
    val buttonsProgress by animateFloatAsState(
        targetValue = if (hasEntered) 1f else 0f,
        animationSpec = if (isEInkMode) snap() else tween(360, delayMillis = 110, easing = AppEasing.Decelerate),
        label = "previewButtonsProgress"
    )
    val useLiquidGlassButtons = isLiquidGlass || liquidGlassSelected
    val contentBlurRadius by animateDpAsState(
        targetValue = when (themeSwitchStage) {
            1, 2, 3, 4 -> 14.dp
            else -> 0.dp
        },
        animationSpec = tween(
            durationMillis = if (themeSwitchStage == 5) 900 else 260,
            easing = if (themeSwitchStage == 5) AppEasing.Accelerate else FastOutSlowInEasing
        ),
        label = "themeSwitchContentBlur"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .blur(contentBlurRadius)
                .background(if (isDark) DarkBackground else LightBackground)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Spacer(modifier = Modifier.height(76.dp))

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
                fontSize = 16.sp,
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
                .weight(1f)
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    alpha = artworkProgress
                    scaleX = 0.90f + artworkProgress * 0.10f
                    scaleY = 0.90f + artworkProgress * 0.10f
                    translationX = (1f - artworkProgress) * 36.dp.toPx()
                }
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (preview == UpdatePreview.LIQUID_GLASS) {
            WelcomeActionButton(
                text = if (liquidGlassSelected) stringResource(R.string.welcome_liquid_glass_enabled) else stringResource(R.string.welcome_enable_liquid_glass),
                onClick = {
                    if (!liquidGlassSelected && themeSwitchStage == 0) {
                        themeSwitchStage = 1
                    }
                },
                primary = true,
                forceLiquidGlass = useLiquidGlassButtons,
                textPrimary = textPrimary,
                enabled = !liquidGlassSelected && themeSwitchStage == 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(52.dp)
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
                secondaryContainerColor = if (isDark) DarkBgGray else LightBgGray,
                enabled = themeSwitchStage == 0,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
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
                    .height(52.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        }

        if (themeSwitchStage != 0) {
            ThemeSwitchOverlay(
                stage = themeSwitchStage,
                onApplyTheme = {
                    liquidGlassSelected = true
                    onEnableLiquidGlass()
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
            .clip(RoundedCornerShape(36.dp))
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
            .clip(RoundedCornerShape(36.dp))
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
            fontSize = 13.sp,
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
            .clip(RoundedCornerShape(24.dp))
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
    onApplyTheme: () -> Unit,
    onStageChange: (Int) -> Unit,
    onFinished: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(260)
        onStageChange(2)
        delay(120)
        onApplyTheme()
        onStageChange(3)
        delay(1_000)
        onStageChange(4)
        delay(240)
        onStageChange(5)
        delay(900)
        onFinished()
    }

    val solidCapsuleAlpha by animateFloatAsState(
        targetValue = if (stage == 2) 1f else 0f,
        animationSpec = tween(if (stage == 3) 1_000 else 180),
        label = "themeSwitchSolidCapsule"
    )
    val glassCapsuleAlpha by animateFloatAsState(
        targetValue = if (stage == 3) 1f else 0f,
        animationSpec = tween(if (stage == 3) 1_000 else 180),
        label = "themeSwitchGlassCapsule"
    )
    val capsuleAlpha by animateFloatAsState(
        targetValue = if (stage == 2 || stage == 3) 1f else 0f,
        animationSpec = tween(240),
        label = "themeSwitchCapsuleExit"
    )
    val capsuleScale by animateFloatAsState(
        targetValue = if (stage == 2 || stage == 3) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "themeSwitchCapsuleScale"
    )
    val sweepProgress by animateFloatAsState(
        targetValue = if (stage == 5) 1f else 0f,
        animationSpec = tween(900, easing = AppEasing.Accelerate),
        label = "themeSwitchLightSweep"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    alpha = sweepProgress
                    translationY = (-112).dp.toPx() + sweepProgress * 980.dp.toPx()
                }
        ) {
            val arcWidth = size.width * 0.60f
            val arcHeight = size.height * 1.45f
            val arcTopLeft = Offset(
                x = (size.width - arcWidth) / 2f,
                y = -size.height * 0.42f
            )
            val arcSize = Size(arcWidth, arcHeight)

            drawArc(
                color = Color.White.copy(alpha = 0.16f),
                startAngle = 202f,
                sweepAngle = 136f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = Color.White.copy(alpha = 0.58f),
                startAngle = 202f,
                sweepAngle = 136f,
                topLeft = arcTopLeft,
                size = arcSize,
                useCenter = false,
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(196.dp)
                .height(56.dp)
                .graphicsLayer {
                    alpha = capsuleAlpha
                    scaleX = capsuleScale
                    scaleY = capsuleScale
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = solidCapsuleAlpha }
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White)
            )
            CompositionLocalProvider(
                LocalAppTheme provides "liquid_glass",
                LocalLiquidGlassTransparency provides 0.65f
            ) {
                LiquidGlassSurface(
                    shape = RoundedCornerShape(28.dp),
                    fallbackColor = Color.White,
                    contentScrimColor = Color.White.copy(alpha = 0.38f),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = glassCapsuleAlpha }
                ) {}
            }
            Text(
                text = stringResource(R.string.welcome_switching_theme),
                color = Color(0xFF2C2C2E),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
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
    val shape = RoundedCornerShape(28.dp)
    val contentColor = if (primary) Color.White else textPrimary

    if (forceLiquidGlass) {
        CompositionLocalProvider(
            LocalAppTheme provides "liquid_glass",
            LocalLiquidGlassTransparency provides 0.65f
        ) {
            if (enabled) {
                LiquidGlassButton(
                    onClick = onClick,
                    modifier = modifier,
                    shape = shape,
                    tintedColor = if (primary) AccentColor else null,
                    prominentShadow = primary,
                    contentColor = contentColor
                ) {
                    Text(
                        text = text,
                        color = contentColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                LiquidGlassSurface(
                    shape = shape,
                    fallbackColor = if (primary) AccentColor else AppColors.CardBg,
                    contentScrimColor = if (primary) {
                        AccentColor.copy(alpha = 0.72f)
                    } else {
                        AppColors.CardBg.copy(alpha = 0.24f)
                    },
                    modifier = modifier
                ) {
                    Text(
                        text = text,
                        color = contentColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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
                fontSize = 16.sp,
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
                    fontSize = 32.sp,
                    lineHeight = 39.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = languageSubtitle,
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = textSecondary
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(panelColor)
                        .padding(12.dp)
                ) {
                    welcomeLanguageOptions.forEach { option ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (option.languageTag == selectedLanguage) selectedRowColor else Color.Transparent)
                                .pointerInput(option.languageTag) {
                                    detectTapGestures { selectedLanguage = option.languageTag }
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = option.label,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                fontSize = 17.sp,
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
                            fontSize = 20.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = eInkDescription,
                            fontSize = 12.sp,
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
    isUpdate: Boolean,
    isDark: Boolean,
    onContinue: () -> Unit,
    onExit: () -> Unit
) {
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showTermsOfService by remember { mutableStateOf(false) }

    // 根据深浅模式动态获取颜色
    val backgroundColor = if (isDark) DarkBackground else LightBackground
    val textPrimary = if (isDark) Color.White else Color.Black
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val bgGray = if (isDark) DarkBgGray else LightBgGray
    val cardBg = if (isDark) DarkCardBg else LightCardBg

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 上半部分 - 推到中间
            Spacer(modifier = Modifier.weight(1f))

            // App Icon
            Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = stringResource(R.string.welcome_app_icon_description),
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier.heightIn(min = 84.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isUpdate) {
                    Text(
                        text = stringResource(R.string.welcome_update_title),
                        modifier = Modifier.padding(horizontal = 24.dp),
                        fontSize = 30.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = textPrimary
                    )
                } else {
                    Text(
                        text = stringResource(R.string.welcome_title),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    Text(
                        text = "Lumi",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentColor
                    )
                }
            }

            // 下半部分 - 推到底部
            Spacer(modifier = Modifier.weight(1f))

            // 隐私说明区域
            Column(
                modifier = Modifier.padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 隐私文字（带可点击的链接）
                val privacyText = stringResource(R.string.welcome_privacy)
                val andText = stringResource(R.string.welcome_and)
                val termsText = stringResource(R.string.welcome_terms)
                val privacySummary = stringResource(R.string.welcome_privacy_summary)
                val policyPunctuation = stringResource(R.string.welcome_policy_punctuation)
                val annotatedText = buildAnnotatedString {
                    append(privacySummary)

                    // 隐私政策链接
                    pushStringAnnotation(tag = "PRIVACY", annotation = "privacy")
                    withStyle(style = SpanStyle(color = AccentColor, fontWeight = FontWeight.Medium)) {
                        append(privacyText)
                    }
                    pop()

                    append(andText)

                    // 用户协议链接
                    pushStringAnnotation(tag = "TERMS", annotation = "terms")
                    withStyle(style = SpanStyle(color = AccentColor, fontWeight = FontWeight.Medium)) {
                        append(termsText)
                    }
                    pop()

                    append(policyPunctuation)
                }

                var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                BasicText(
                    text = annotatedText,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        color = textSecondary,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    ),
                    onTextLayout = { textLayoutResult = it },
                    modifier = Modifier.pointerInput(Unit) {
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

            Spacer(modifier = Modifier.height(24.dp))

            // 按钮区域
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 退出按钮
                Button(
                    onClick = onExit,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = bgGray,
                        contentColor = textPrimary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.welcome_exit),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // 继续按钮
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentColor,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = stringResource(R.string.welcome_continue),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // 隐私政策底部弹窗（带动画）
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

        // 用户协议底部弹窗（带动画）
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
        delay(70)
        entranceStage = 1
        delay(150)
        entranceStage = 2
        delay(160)
        entranceStage = 3
        delay(120)
        entranceStage = 4
        delay(150)
        entranceStage = 5
    }

    val backgroundColor = if (isDark) DarkBackground else LightBackground
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val supportBackground = if (isDark) DarkSupportBackground else LightSupportBackground
    val panelAlpha by animateFloatAsState(
        targetValue = if (entranceStage >= 1) 1f else 0f,
        animationSpec = tween(300),
        label = "supportPanelAlpha"
    )
    val panelScale by animateFloatAsState(
        targetValue = if (entranceStage >= 1) 1f else 0.72f,
        animationSpec = spring(dampingRatio = 0.66f, stiffness = 320f),
        label = "supportPanelScale"
    )
    val sideEmojiProgress by animateFloatAsState(
        targetValue = if (entranceStage >= 2) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 360f),
        label = "supportSideEmojiProgress"
    )
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
        Spacer(modifier = Modifier.weight(0.42f))

        Box(
            modifier = Modifier
                .width(164.dp)
                .height(104.dp)
                .graphicsLayer {
                    alpha = panelAlpha
                    scaleX = panelScale
                    scaleY = panelScale
                    translationY = (1f - panelAlpha) * 18.dp.toPx()
                },
            contentAlignment = Alignment.Center
        ) {
            if (isLiquidGlass) {
                LiquidGlassSurface(
                    shape = RoundedCornerShape(28.dp),
                    fallbackColor = supportBackground,
                    contentScrimColor = supportBackground.copy(alpha = if (isDark) 0.48f else 0.30f),
                    outlineWidth = 0.dp,
                    decorationModifier = Modifier.shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(28.dp),
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = if (isDark) 0.16f else 0.11f),
                        spotColor = Color.Black.copy(alpha = if (isDark) 0.10f else 0.06f)
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {}
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp))
                        .background(supportBackground)
                ) {}
            }

            Box(
                modifier = Modifier.size(width = 112.dp, height = 88.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedEmoji(
                    emoji = "☕",
                    progress = sideEmojiProgress,
                    rotation = -16f,
                    fontSize = 30,
                    containerSize = 42,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 7.dp, y = (-1).dp)
                        .zIndex(3f)
                )
                AnimatedEmoji(
                    emoji = "📖",
                    progress = panelAlpha,
                    rotation = -4f,
                    fontSize = 50,
                    containerSize = 66,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 4.dp)
                        .zIndex(2f)
                )
                AnimatedEmoji(
                    emoji = "✨",
                    progress = sideEmojiProgress,
                    rotation = 18f,
                    fontSize = 28,
                    containerSize = 38,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-2).dp, y = (-1).dp)
                        .zIndex(3f)
                )
                AnimatedEmoji(
                    emoji = "💗",
                    progress = sideEmojiProgress,
                    rotation = 12f,
                    fontSize = 21,
                    containerSize = 30,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 1.dp, y = 23.dp)
                        .zIndex(4f)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

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
            fontSize = 15.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            color = textSecondary
        )

        Spacer(modifier = Modifier.weight(0.58f))

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
            WelcomeActionButton(
                text = stringResource(R.string.welcome_open_sponsor),
                onClick = onOpenSponsor,
                primary = false,
                forceLiquidGlass = isLiquidGlass,
                textPrimary = if (isDark) Color.White else Color.Black,
                secondaryContainerColor = if (isDark) DarkBgGray else LightBgGray,
                enabled = entranceStage >= 5,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            )
            WelcomeActionButton(
                text = stringResource(R.string.welcome_start_using),
                onClick = onFinished,
                primary = true,
                forceLiquidGlass = isLiquidGlass,
                textPrimary = if (isDark) Color.White else Color.Black,
                enabled = entranceStage >= 5,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AnimatedEmoji(
    emoji: String,
    progress: Float,
    rotation: Float,
    fontSize: Int,
    containerSize: Int,
    modifier: Modifier = Modifier
) {
    val alpha = progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier.size(containerSize.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            modifier = Modifier.graphicsLayer {
                this.alpha = alpha
                scaleX = 0.6f + progress * 0.4f
                scaleY = 0.6f + progress * 0.4f
                rotationZ = (1f - progress) * rotation
            },
            fontSize = fontSize.sp,
            textAlign = TextAlign.Center
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

        // 容器层（滑入动画）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .align(Alignment.BottomCenter)
                .materialBottomSheetMotion(containerOffsetY.value, predictiveBackProgress)
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                )
                .background(cardBg, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .padding(top = 24.dp, start = 24.dp, end = 24.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = KaiTi,
                    color = textPrimary
                )

                Spacer(modifier = Modifier.weight(1f))

                // 关闭按钮
                LiquidGlassIconButton(
                    imageVector = Icons.Outlined.Close,
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
                    fontSize = 15.sp,
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
                        .clip(RoundedCornerShape(14.dp))
                        .background(AccentColor.copy(alpha = 0.11f))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = line,
                        fontSize = 14.sp,
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
                    fontSize = 14.sp,
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
            .clip(RoundedCornerShape(16.dp))
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
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            } else {
                Text(
                    text = line,
                    fontSize = 12.sp,
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
            fontSize = 17.sp,
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
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
            color = AccentColor
        )
        Spacer(modifier = Modifier.width(9.dp))
        Text(
            text = body,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
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
    "基本方針", "基本原則", "핵심 원칙", "핵심 원칙："
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
