package com.huangder.lumibooks.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.FangSong
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TxtEditorScreen(
    onNavigateBack: (saved: Boolean) -> Unit,
    viewModel: TxtEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val isDark = LocalIsDarkTheme.current
    var showDiscardDialog by remember { mutableStateOf(false) }

    var textFieldValue by remember { mutableStateOf(TextFieldValue("", TextRange(0))) }
    var textInitialized by remember { mutableStateOf(false) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var didScrollToCursor by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.chapterText) {
        if (!textInitialized && uiState.chapterText.isNotEmpty()) {
            val cur = viewModel.getCursorOffset().coerceIn(0, uiState.chapterText.length)
            textFieldValue = TextFieldValue(uiState.chapterText, TextRange(cur))
            textInitialized = true
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    LaunchedEffect(textLayoutResult, textInitialized) {
        val layout = textLayoutResult ?: return@LaunchedEffect
        if (textInitialized && !didScrollToCursor) {
            val cur = textFieldValue.selection.start
            if (cur > 0 && cur <= layout.layoutInput.text.length) {
                try {
                    val rect = layout.getCursorRect(cur.coerceAtMost(layout.layoutInput.text.length - 1))
                    scope.launch { scrollState.scrollTo(maxOf(0, rect.top.toInt() - 120)) }
                } catch (_: Exception) {}
            }
            didScrollToCursor = true
        }
    }

    val isModified = textInitialized && textFieldValue.text != uiState.initialChapterText
    val handleBack: () -> Unit = { if (isModified) showDiscardDialog = true else onNavigateBack(false) }
    BackHandler { handleBack() }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize().background(AppColors.WindowBg).statusBarsPadding(),
            contentAlignment = Alignment.Center) {
            Text("加载中...", fontSize = AppType.Body, color = AppColors.TextSecondary) }
        return
    }
    if (uiState.errorMessage != null && uiState.chapterText.isEmpty()) {
        Box(Modifier.fillMaxSize().background(AppColors.WindowBg).statusBarsPadding(),
            contentAlignment = Alignment.Center) {
            Text(uiState.errorMessage ?: "未知错误", fontSize = AppType.Body, color = AppColors.Accent) }
        return
    }

    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val statusBarTopDp = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val topBarHeightDp = if (topBarHeightPx > 0) with(density) { topBarHeightPx.toDp() }
                        else statusBarTopDp + AppSpace.sm + 40.dp + AppSpace.sm

    Box(Modifier.fillMaxSize().background(AppColors.WindowBg)) {
        Column(Modifier.fillMaxWidth().verticalScroll(scrollState)) {
            Spacer(Modifier.fillMaxWidth().height(topBarHeightDp))
            BasicTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    .padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
                textStyle = TextStyle(fontSize = AppType.Body, fontFamily = FangSong,
                    color = AppColors.TextPrimary, lineHeight = 28.sp),
                cursorBrush = SolidColor(AppColors.Accent),
                onTextLayout = { textLayoutResult = it },
                decorationBox = { inner ->
                    Box {
                        if (textFieldValue.text.isEmpty()) {
                            Text("输入文本内容...", fontSize = AppType.Body, fontFamily = FangSong,
                                color = AppColors.TextSecondary.copy(alpha = 0.5f))
                        }
                        inner()
                    }
                }
            )
            Spacer(Modifier.fillMaxWidth().height(48.dp))
        }

        Box(Modifier.fillMaxWidth().height(topBarHeightDp + 32.dp).background(
            Brush.verticalGradient(colorStops = arrayOf(
                0.00f to AppColors.WindowBg, 0.55f to AppColors.WindowBg,
                0.80f to AppColors.WindowBg.copy(alpha = 0.80f),
                0.92f to AppColors.WindowBg.copy(alpha = 0.30f),
                1.00f to AppColors.WindowBg.copy(alpha = 0f)))))

        Row(modifier = Modifier.fillMaxWidth().onSizeChanged { topBarHeightPx = it.height }
                .statusBarsPadding().padding(horizontal = AppSpace.lg, vertical = AppSpace.sm),
            verticalAlignment = Alignment.CenterVertically) {
            LiquidGlassIconButton(imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.reader_back),
                onClick = handleBack, size = 40.dp, iconSize = 22.dp)
            Text(text = uiState.bookTitle, fontSize = AppType.Section, fontWeight = FontWeight.Bold,
                fontFamily = KaiTi, color = AppColors.TextPrimary, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = AppSpace.sm))
            LiquidGlassIconButton(imageVector = Icons.Outlined.Check,
                contentDescription = stringResource(R.string.confirm),
                onClick = { focusManager.clearFocus()
                    viewModel.save(textFieldValue.text) { onNavigateBack(true) } },
                size = 40.dp, iconSize = 22.dp,
                contentColor = Color.White,
                normalContainerColor = AppColors.Accent,
                liquidContainerColor = AppColors.Accent,
                liquidScrimColor = AppColors.Accent)
        }
    }

    if (showDiscardDialog) {
        val btnShape = RoundedCornerShape(50)
        // LiquidGlassDialog 通过 LiquidGlassDialogHost 渲染：
        // Activity 里已有 layerBackdrop + ProvideLiquidGlassBackdrop(null)
        // → 弹窗拿到真实折射，不触发递归崩溃
        LiquidGlassDialog(
            onDismissRequest = { showDiscardDialog = false },
            backgroundScrimColor = Color.Black.copy(alpha = 0.22f)  // 遮罩不要太暗
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
                Text(stringResource(R.string.txt_editor_discard_title),
                    fontSize = AppType.Section, fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.txt_editor_discard_message),
                    fontSize = AppType.Body, color = AppColors.TextSecondary)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { showDiscardDialog = false },
                        modifier = Modifier.weight(1f), shape = btnShape,
                        border = BorderStroke(1.dp, AppColors.Divider),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.TextSecondary)) {
                        Text(stringResource(R.string.cancel), fontWeight = FontWeight.Medium) }
                    Button(onClick = { showDiscardDialog = false; onNavigateBack(false) },
                        modifier = Modifier.weight(1f), shape = btnShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.Accent, contentColor = Color.White)) {
                        Text(stringResource(R.string.confirm), fontWeight = FontWeight.Medium) }
                }
            }
        }
    }
}
