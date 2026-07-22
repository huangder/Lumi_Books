package com.huangder.lumibooks.ui.welcome

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
    INTRODUCTION,
    MINERU_PREVIEW,
    LIQUID_GLASS_PREVIEW,
    BOOKSHELF_PREVIEW,
    SUPPORT
}

private enum class UpdatePreview {
    MINERU,
    LIQUID_GLASS,
    BOOKSHELF
}

private fun WelcomePage.previousPage(): WelcomePage = when (this) {
    WelcomePage.INTRODUCTION -> WelcomePage.INTRODUCTION
    WelcomePage.MINERU_PREVIEW -> WelcomePage.INTRODUCTION
    WelcomePage.LIQUID_GLASS_PREVIEW -> WelcomePage.MINERU_PREVIEW
    WelcomePage.BOOKSHELF_PREVIEW -> WelcomePage.LIQUID_GLASS_PREVIEW
    WelcomePage.SUPPORT -> WelcomePage.BOOKSHELF_PREVIEW
}

@Composable
fun WelcomeScreen(
    isUpdate: Boolean,
    isDark: Boolean,
    isLiquidGlass: Boolean,
    onFinished: () -> Unit,
    onExit: () -> Unit,
    onOpenSponsor: () -> Unit,
    onEnableLiquidGlass: () -> Unit
) {
    var currentPage by rememberSaveable { mutableStateOf(WelcomePage.INTRODUCTION) }
    val backgroundColor = if (isDark) DarkBackground else LightBackground

    val predictiveBackProgress = ConfigurableBackHandler(
        enabled = currentPage != WelcomePage.INTRODUCTION
    ) {
        currentPage = currentPage.previousPage()
    }

    AnimatedContent(
        targetState = currentPage,
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .graphicsLayer {
                translationX = predictiveBackProgress * size.width * 0.12f
                alpha = 1f - predictiveBackProgress * 0.1f
            },
        transitionSpec = {
            if (targetState.ordinal > initialState.ordinal) {
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
            WelcomePage.INTRODUCTION -> WelcomeIntroductionPage(
                isUpdate = isUpdate,
                isDark = isDark,
                onContinue = { currentPage = WelcomePage.MINERU_PREVIEW },
                onExit = onExit
            )

            WelcomePage.MINERU_PREVIEW -> UpdatePreviewPage(
                preview = UpdatePreview.MINERU,
                isDark = isDark,
                isLiquidGlass = isLiquidGlass,
                onBack = { currentPage = WelcomePage.INTRODUCTION },
                onNext = { currentPage = WelcomePage.LIQUID_GLASS_PREVIEW },
                onEnableLiquidGlass = onEnableLiquidGlass
            )

            WelcomePage.LIQUID_GLASS_PREVIEW -> UpdatePreviewPage(
                preview = UpdatePreview.LIQUID_GLASS,
                isDark = isDark,
                isLiquidGlass = isLiquidGlass,
                onBack = { currentPage = WelcomePage.MINERU_PREVIEW },
                onNext = { currentPage = WelcomePage.BOOKSHELF_PREVIEW },
                onEnableLiquidGlass = onEnableLiquidGlass
            )

            WelcomePage.BOOKSHELF_PREVIEW -> UpdatePreviewPage(
                preview = UpdatePreview.BOOKSHELF,
                isDark = isDark,
                isLiquidGlass = isLiquidGlass,
                onBack = { currentPage = WelcomePage.LIQUID_GLASS_PREVIEW },
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
    isLiquidGlass: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onEnableLiquidGlass: () -> Unit
) {
    var hasEntered by remember(preview) { mutableStateOf(false) }
    var liquidGlassSelected by rememberSaveable { mutableStateOf(isLiquidGlass) }
    var themeSwitchStage by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(preview) {
        delay(40)
        hasEntered = true
    }
    LaunchedEffect(isLiquidGlass) {
        if (isLiquidGlass) liquidGlassSelected = true
    }

    val title = when (preview) {
        UpdatePreview.MINERU -> "内置 MinerU 解析接口"
        UpdatePreview.LIQUID_GLASS -> "液态玻璃主题 全覆盖"
        UpdatePreview.BOOKSHELF -> "书库页面功能升级"
    }
    val subtitle = when (preview) {
        UpdatePreview.MINERU -> "MinerU 使得 PDF OCR 解析更加准确。"
        UpdatePreview.LIQUID_GLASS -> "精心设计和适配的液态玻璃主题，在 LUMI 的\n各个场景下都有极其出色的交互体验。"
        UpdatePreview.BOOKSHELF -> "多选书籍以及搜索书籍功能得到增强，\n书籍再多也不怕。"
    }
    val textPrimary = if (isDark) Color.White else Color.Black
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val frameColor = if (isDark) Color(0xFF242426) else Color(0xFFF2F2F3)
    val titleProgress by animateFloatAsState(
        targetValue = if (hasEntered) 1f else 0f,
        animationSpec = tween(360, easing = AppEasing.Decelerate),
        label = "previewTitleProgress"
    )
    val artworkProgress by animateFloatAsState(
        targetValue = if (hasEntered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 330f),
        label = "previewArtworkProgress"
    )
    val buttonsProgress by animateFloatAsState(
        targetValue = if (hasEntered) 1f else 0f,
        animationSpec = tween(360, delayMillis = 110, easing = AppEasing.Decelerate),
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
                text = if (liquidGlassSelected) "已设置为液态玻璃" else "将主题设置为液态玻璃",
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
                text = "上一步",
                onClick = onBack,
                primary = false,
                forceLiquidGlass = useLiquidGlassButtons,
                textPrimary = textPrimary,
                enabled = themeSwitchStage == 0,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            )
            WelcomeActionButton(
                text = "下一步",
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
            description = "MinerU 云端解析功能预览",
            frameColor = frameColor,
            modifier = modifier
        )

        UpdatePreview.BOOKSHELF -> PhonePreviewFrame(
            imageRes = R.drawable.welcome_bookshelf_preview,
            description = "升级后的书库功能预览",
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
            description = "液态玻璃首页导航预览",
            alignment = Alignment.BottomCenter,
            modifier = Modifier.weight(1f)
        )
        LiquidGlassPreviewTile(
            imageRes = R.drawable.welcome_glass_dialog_preview,
            description = "液态玻璃对话框预览",
            alignment = Alignment.Center,
            modifier = Modifier.weight(1f)
        )
        LiquidGlassPreviewTile(
            imageRes = R.drawable.welcome_glass_reader_preview,
            description = "液态玻璃阅读器预览",
            alignment = Alignment.BottomCenter,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "不再是简单的纯色和模糊，而是具有玻璃形态的折射效果。",
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
                text = "正在切换",
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
                contentDescription = "Lumi Icon",
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
                val annotatedText = buildAnnotatedString {
                    append("Lumi 是一款本地优先的图书阅读器。阅读数据存储在您的设备中；只有在您主动启用并选择 MinerU 云端解析时，所选 PDF 才会上传至第三方服务。Lumi 坚持最小权限原则，不使用分析或广告追踪。点击\"继续\"按钮，即表示您已阅读并同意")

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

                    append("。")
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
    val lines = content.lines()
    var index = 0

    while (index < lines.size) {
        val line = lines[index].trim()

        when {
            // 第一行是主标题（如"Lumi 隐私政策"、"Lumi 用户协议"）
            index == 0 && line.isNotEmpty() -> {
                Text(
                    text = line,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            // 空行
            line.isEmpty() -> {
                Spacer(modifier = Modifier.height(8.dp))
            }
            // 一级标题（一、二、三...或阿拉伯数字开头的标题）
            line.matches(Regex("^[一二三四五六七八九十]+、.*")) ||
            line.matches(Regex("^\\d+\\.\\s*[^\\d].*")) && !line.matches(Regex("^\\d+\\.\\d+.*")) -> {
                Text(
                    text = line,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }
            // 二级标题（3.1, 3.2, 4.1 等）
            line.matches(Regex("^\\d+\\.\\d+\\s+.*")) -> {
                Text(
                    text = line,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            // 列表项（以 • 或 - 开头）
            line.startsWith("•") || line.startsWith("-") || line.startsWith("❌") -> {
                Text(
                    text = line,
                    fontSize = 14.sp,
                    color = textColor,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                )
            }
            // 元信息行（生效日期、最近更新、应用名称、包名、开发者、邮箱）
            line.startsWith("生效日期") || line.startsWith("最近更新") ||
            line.startsWith("应用名称") || line.startsWith("包名") ||
            line.startsWith("开发者") || line.startsWith("联系邮箱") -> {
                Text(
                    text = line,
                    fontSize = 13.sp,
                    color = secondaryColor,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            // 核心原则（加粗显示）
            line.startsWith("核心原则") -> {
                Text(
                    text = line,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            // 普通段落
            else -> {
                Text(
                    text = line,
                    fontSize = 14.sp,
                    color = textColor,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        index++
    }
}

private fun getPrivacyPolicyContent(): String {
    return """
Lumi 隐私政策

生效日期：2026年6月29日
最近更新：2026年7月21日
应用名称：Lumi
包名：com.huangder.lumibooks
开发者：huangder
联系邮箱：huangder0104@126.com

一、引言

Lumi（以下简称"本应用"）是一款开源的本地电子书阅读器。Lumi 原创代码以 MIT 许可证发布，第三方组件遵循各自许可证。我们深知隐私对您的重要性，本隐私政策旨在向您说明我们如何处理您的个人信息。

核心原则：本应用坚持本地优先。只有在您主动启用并选择 MinerU 云端解析时，您选定的 PDF 才会上传至第三方服务；本应用不会在后台自动上传文件。

二、开源声明

本应用为开源软件，源代码托管于 GitHub。您可以自由查看、修改和分发本应用的源代码。开源不意味着放弃隐私保护——我们依然严格遵守最小数据收集原则。

三、权限说明

本应用仅申请以下 Android 系统权限，且每项权限均有明确的用途说明：

• READ_EXTERNAL_STORAGE（Android 12 及以下）- 读取您设备上的电子书文件（EPUB、TXT、PDF）
• READ_MEDIA_IMAGES（Android 13 及以上）- 读取电子书中的封面图片资源

• INTERNET — 检查应用更新及条款/政策变更；在用户主动选择时连接 MinerU 云端解析

本应用不申请以下权限：

• ❌ 位置信息权限
• ❌ 相机/麦克风权限
• ❌ 通讯录权限
• ❌ 电话状态权限
• ❌ 写入外部存储权限
• ❌ 任何其他敏感权限

四、数据处理说明

4.1 本应用处理的数据

阅读数据和应用设置存储在您的本地设备上；主动使用 MinerU 时的文件传输除外：

• 电子书文件 - 您主动选择打开的 EPUB、TXT、PDF 文件
• 阅读进度 - 当前阅读章节和页码位置
• 书签记录 - 您手动添加的书签
• 阅读时长 - 每次阅读的开始时间、结束时间、持续时长
• 阅读偏好 - 字号、主题、行距等显示设置
• MinerU 设置 - 模式、同意版本和时间；个人 Token 使用 Android Keystore 加密
• 最近阅读 - 最近打开的书籍列表及时间

4.2 本应用不处理的数据

• ❌ 不收集您的个人身份信息（姓名、邮箱、电话等）
• ❌ 不收集您的设备标识信息（IMEI、Android ID、MAC 地址等）
• ❌ 不收集或接收您的阅读内容；启用 MinerU 时文件由设备直接发送给第三方
• ❌ 不使用任何分析或追踪工具
• ❌ 不使用任何广告 SDK
• ❌ 除您明确发起的 MinerU 解析外，不进行文件上传或云同步

五、数据存储与安全

5.1 存储方式

所有数据均存储在应用的私有沙盒目录中，遵循 Android 系统的应用隔离机制。其他应用无法直接访问本应用的私有数据。

5.2 数据安全

• 应用仅在检查更新及您主动选择 MinerU 云端解析时联网
• 数据存储在 Android 应用沙盒中，受系统级隔离保护
• 本应用不收集任何可识别您身份的信息

5.3 数据保留

• 您可以随时在应用内清除阅读历史、书签等数据
• 卸载应用将自动删除所有应用数据
• 电子书源文件由您自行管理，本应用不会修改或删除您的原始文件

六、第三方服务

本应用不集成分析、广告、推送、云同步或崩溃报告服务。您可以主动启用独立第三方 OpenDataLab/MinerU 云端解析；使用受其服务协议和个人信息保护政策约束。

• ❌ 无第三方分析服务
• ❌ 无第三方广告服务
• ❌ 无第三方推送服务
• ❌ 无云同步服务
• ❌ 无崩溃报告服务

七、儿童隐私

本应用不针对 13 岁以下儿童，不会故意收集儿童的个人信息。如果您是儿童的监护人，发现儿童可能向本应用提供了个人信息，请联系我们，我们将及时删除相关信息。

八、联系我们

如果您对本隐私政策有任何疑问、意见或建议，请通过以下方式联系我们：

• 开发者：huangder
• 联系邮箱：huangder0104@126.com
• GitHub Issues：欢迎在项目仓库提交问题反馈

九、隐私政策的变更

如果我们决定更新本隐私政策，我们将在应用内发布更新后的版本，并更新"最近更新"日期。重大变更将以应用内通知的方式告知您。

本隐私政策最后更新于 2026年7月21日。
    """.trimIndent()
}

private fun getTermsOfServiceContent(): String {
    return """
Lumi 用户协议

生效日期：2026年6月29日
最近更新：2026年7月21日
应用名称：Lumi
包名：com.huangder.lumibooks
开发者：huangder
联系邮箱：huangder0104@126.com

一、协议的接受

欢迎使用 Lumi（以下简称"本应用"）。在使用本应用之前，请仔细阅读并充分理解本用户协议。

下载、安装、打开或使用本应用，即表示您已阅读、理解并同意受本协议的约束。如果您不同意本协议的任何条款，请停止使用本应用。

二、应用说明

2.1 应用功能

Lumi 是一款本地电子书阅读器，主要功能包括：

• 阅读 EPUB、TXT、PDF 格式的电子书
• 管理本地电子书文件
• 记录阅读进度和书签
• 自定义阅读界面（字号、主题、行距等）
• 查看阅读时长统计
• 在用户主动启用后，通过第三方 MinerU 服务将 PDF 解析为电子书

2.2 应用性质

本应用为本地优先应用：

• 本地阅读和本地解析不上传文件；检查更新时连接 GitHub
• MinerU 云端解析默认关闭，仅在您主动启用并选择后上传所选 PDF
• 本应用不提供用户账号注册或登录功能
• 阅读数据存储在本地；第三方解析数据由 MinerU 按其协议处理

2.3 开源许可

Lumi 原创代码以 MIT 许可证开源发布，第三方组件及改编代码遵循各自许可证。在遵守对应许可证的前提下，您可以：

• 自由查看源代码
• 在个人或商业项目中使用本应用
• 修改源代码并重新分发
• 在保留版权声明的前提下进行二次开发

三、用户权利与义务

3.1 您的权利

• 您有权免费使用本应用的所有功能
• 您有权在应用内自由管理您的阅读数据
• 您有权随时卸载本应用并删除所有数据
• 您有权查看、修改和分发本应用的源代码
• 您有权对本应用提出反馈和建议

3.2 您的义务

• 您应确保使用本应用阅读的电子书文件不侵犯他人知识产权
• 您不应利用本应用从事任何违反法律法规的活动
• 您在二次开发或分发时应遵守适用的开源许可证，并保留原始版权和归属声明

四、知识产权

4.1 应用知识产权

Lumi 原创源代码、原创界面设计、图标及品牌标识的相关权利归开发者 huangder 所有，原创代码依 MIT 许可证开放使用。第三方依赖及改编代码的权利归各自权利人所有，并继续受其许可证约束；具体归属见“开放源代码许可”页面。

4.2 用户内容

您通过本应用阅读的电子书文件及其内容的知识产权归原始版权人所有。本应用仅提供阅读功能，不主张对您阅读内容的任何权利。

五、免责声明

5.1 数据安全

• 本应用尽最大努力保护您的数据安全，但不对因设备故障、系统崩溃等原因导致的数据丢失承担责任
• 建议您定期备份重要的电子书文件和阅读数据
• 卸载应用将导致应用数据不可恢复地删除

5.2 文件兼容性

• 本应用支持 EPUB、TXT、PDF 格式，但不保证对所有同格式文件的完美兼容
• 部分电子书文件可能因格式不规范而无法正常显示

5.3 使用风险

• 您应自行承担使用本应用的一切风险
• 本应用按"现状"提供，不作任何明示或暗示的保证
• 开源代码按"原样"提供，不附带任何形式的担保

六、隐私保护

本应用高度重视您的隐私保护。核心要点：

• 本应用不使用分析、广告或用户追踪 SDK
• 只有在您主动选择 MinerU 时，所选 PDF 才会发送至第三方
• 阅读数据和加密后的个人 Token 存储在您的本地设备上
• 源代码公开透明，欢迎审查

七、协议的变更

我们保留随时修改本协议的权利。修改后的协议将在应用内发布，并更新"最近更新"日期。继续使用本应用即表示您接受修改后的协议。

八、适用法律

本协议的解释和执行适用中华人民共和国法律。因本协议引起的或与本协议有关的任何争议，应友好协商解决；协商不成的，任何一方均可向开发者所在地人民法院提起诉讼。

九、联系我们

如果您对本用户协议有任何疑问、意见或建议，请通过以下方式联系我们：

• 开发者：huangder
• 联系邮箱：huangder0104@126.com
• GitHub Issues：欢迎在项目仓库提交问题反馈

本用户协议最后更新于 2026年7月21日。
    """.trimIndent()
}
