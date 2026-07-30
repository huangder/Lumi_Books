package com.huangder.lumibooks.ui.reader

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.ConfigurableBottomSheetBackHandler
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassSheetContainer
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.animateBottomSheetIn
import com.huangder.lumibooks.ui.components.animateBottomSheetOut
import com.huangder.lumibooks.ui.components.materialBottomSheetMotion
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TxtEditorScreen(
    onNavigateBack: (saved: Boolean) -> Unit,
    backdrop: Backdrop? = null,
    viewModel: TxtEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var textFieldValue by remember { mutableStateOf(TextFieldValue()) }
    var editTextRef by remember { mutableStateOf<OemTxtEditText?>(null) }
    var textFieldTopPx by remember { mutableStateOf(0f) }
    var screenHeightPx by remember { mutableIntStateOf(0) }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    var occluderTopPx by remember { mutableStateOf(0f) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showReplaceAllDialog by remember { mutableStateOf(false) }

    val statusBarTopDp = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val topBarHeightDp = if (topBarHeightPx > 0) {
        with(density) { topBarHeightPx.toDp() }
    } else {
        statusBarTopDp + AppSpace.sm + 40.dp + AppSpace.sm
    }
    val bottomSpacerDp = with(density) {
        val fallback = 96.dp.toPx()
        val covered = if (screenHeightPx > 0 && occluderTopPx > 0f) {
            screenHeightPx - occluderTopPx
        } else fallback
        (covered + 24.dp.toPx()).coerceAtLeast(fallback).toDp()
    }

    LaunchedEffect(uiState.chapterRevision) {
        if (uiState.chapterRevision <= 0) return@LaunchedEffect
        val start = uiState.targetSelectionStart.coerceIn(0, uiState.chapterText.length)
        val end = uiState.targetSelectionEnd.coerceIn(start, uiState.chapterText.length)
        textFieldValue = TextFieldValue(uiState.chapterText, TextRange(start, end))
        delay(20)
        if (uiState.currentMatch == null) {
            scrollState.scrollTo(uiState.restoreScrollPosition.coerceIn(0, scrollState.maxValue))
        }
    }

    LaunchedEffect(uiState.sheetMode) {
        if (uiState.sheetMode == null) {
            delay(80)
            editTextRef?.let { editText ->
                editText.requestFocus()
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    LaunchedEffect(uiState.currentMatch, editTextRef, occluderTopPx, bottomSpacerDp) {
        val match = uiState.currentMatch ?: return@LaunchedEffect
        if (match.chapterIndex != uiState.chapterIndex) return@LaunchedEffect
        val editText = editTextRef ?: return@LaunchedEffect
        if (editText.text.isEmpty() || match.start >= editText.text.length) return@LaunchedEffect
        delay(40)
        val layout = editText.layout ?: return@LaunchedEffect
        val startLine = layout.getLineForOffset(match.start.coerceAtMost(editText.text.length - 1))
        val endLine = layout.getLineForOffset(
            (match.endExclusive - 1).coerceIn(match.start, editText.text.length - 1)
        )
        val targetTop = textFieldTopPx + layout.getLineTop(startLine)
        val targetBottom = textFieldTopPx + layout.getLineBottom(endLine)
        val safeTop = topBarHeightPx + with(density) { 16.dp.toPx() }
        val safeBottom = (if (occluderTopPx > 0f) occluderTopPx else screenHeightPx.toFloat()) -
            with(density) { 16.dp.toPx() }
        val delta = when {
            targetBottom > safeBottom -> targetBottom - safeBottom
            targetTop < safeTop -> targetTop - safeTop
            else -> 0f
        }
        if (delta != 0f) scrollState.animateScrollBy(delta)
    }

    LaunchedEffect(
        uiState.sheetMode,
        uiState.searchQuery,
        uiState.searchScope,
        uiState.matchCase,
        textFieldValue.text
    ) {
        if (uiState.sheetMode == null || uiState.searchQuery.isBlank()) return@LaunchedEffect
        if (uiState.searchScope != TxtSearchScope.CHAPTER) return@LaunchedEffect
        delay(220)
        viewModel.updatePosition(textFieldValue.selection.start, scrollState.value)
        viewModel.search(textFieldValue.text, textFieldValue.selection.start)
    }

    LaunchedEffect(uiState.lastReplaceCount) {
        val count = uiState.lastReplaceCount ?: return@LaunchedEffect
        Toast.makeText(
            context,
            context.getString(R.string.txt_editor_replaced_count, count),
            Toast.LENGTH_SHORT
        ).show()
        viewModel.consumeReplaceCount()
    }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.clearError()
    }

    val hasChanges = viewModel.hasPendingChanges(textFieldValue.text)
    val handleBack: () -> Unit = {
        viewModel.updatePosition(textFieldValue.selection.start, scrollState.value)
        if (hasChanges) showDiscardDialog = true else onNavigateBack(false)
    }
    BackHandler(enabled = uiState.sheetMode == null) { handleBack() }

    if (uiState.isLoading) {
        Box(
            Modifier.fillMaxSize().background(AppColors.WindowBg).statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.txt_editor_loading), color = AppColors.TextSecondary)
        }
        return
    }
    if (uiState.fatalErrorMessage != null) {
        Box(
            Modifier.fillMaxSize().background(AppColors.WindowBg).statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Text(uiState.fatalErrorMessage.orEmpty(), color = AppColors.Accent)
        }
        return
    }

    val highlightRange = uiState.currentMatch
        ?.takeIf { it.chapterIndex == uiState.chapterIndex }
        ?.let { it.start until it.endExclusive }
    val accentColor = AppColors.Accent
    val editorTextColor = AppColors.TextPrimary.toArgb()
    val editorHintColor = AppColors.TextSecondary.copy(alpha = 0.5f).toArgb()
    val editorHighlightColor = accentColor.copy(alpha = 0.32f).toArgb()

    Box(
        Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
            .onSizeChanged { screenHeightPx = it.height }
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(scrollState)) {
            Spacer(Modifier.fillMaxWidth().height(topBarHeightDp))
            AndroidView(
                factory = { androidContext ->
                    OemTxtEditText(androidContext).also { editText ->
                        editTextRef = editText
                        editText.onUserTextChanged = { text, start, end ->
                            if (text != textFieldValue.text) viewModel.invalidateSearchResult()
                            textFieldValue = TextFieldValue(text, TextRange(start, end))
                        }
                        editText.onUserSelectionChanged = { start, end ->
                            if (textFieldValue.selection.start != start ||
                                textFieldValue.selection.end != end
                            ) {
                                textFieldValue = textFieldValue.copy(selection = TextRange(start, end))
                            }
                        }
                        editText.post {
                            editText.requestFocus()
                            if (uiState.sheetMode == null) {
                                (androidContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                                    ?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
                            }
                        }
                    }
                },
                update = { editText ->
                    editText.onUserTextChanged = { text, start, end ->
                        if (text != textFieldValue.text) viewModel.invalidateSearchResult()
                        textFieldValue = TextFieldValue(text, TextRange(start, end))
                    }
                    editText.onUserSelectionChanged = { start, end ->
                        if (textFieldValue.selection.start != start ||
                            textFieldValue.selection.end != end
                        ) {
                            textFieldValue = textFieldValue.copy(selection = TextRange(start, end))
                        }
                    }
                    editText.applyEditorState(
                        text = textFieldValue.text,
                        selectionStart = textFieldValue.selection.start,
                        selectionEnd = textFieldValue.selection.end,
                        highlightRange = highlightRange,
                        textColor = editorTextColor,
                        hintColor = editorHintColor,
                        highlightColor = editorHighlightColor
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { textFieldTopPx = it.boundsInRoot().top }
                    .padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
            )
            Spacer(Modifier.fillMaxWidth().height(bottomSpacerDp))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(topBarHeightDp + 32.dp)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to AppColors.WindowBg,
                            0.55f to AppColors.WindowBg,
                            0.80f to AppColors.WindowBg.copy(alpha = 0.80f),
                            0.92f to AppColors.WindowBg.copy(alpha = 0.30f),
                            1.00f to AppColors.WindowBg.copy(alpha = 0f)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { topBarHeightPx = it.height }
                .statusBarsPadding()
                .padding(horizontal = AppSpace.lg, vertical = AppSpace.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidGlassIconButton(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.reader_back),
                onClick = handleBack,
                size = 40.dp,
                iconSize = 22.dp
            )
            Text(
                text = uiState.bookTitle,
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                fontFamily = resolveAppFontFamily(KaiTi),
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = AppSpace.sm)
            )
            LiquidGlassIconButton(
                imageVector = Icons.Outlined.Check,
                contentDescription = stringResource(R.string.confirm),
                onClick = {
                    focusManager.clearFocus()
                    viewModel.save(
                        textFieldValue.text,
                        textFieldValue.selection.start,
                        scrollState.value
                    ) { onNavigateBack(true) }
                },
                size = 40.dp,
                iconSize = 22.dp,
                contentColor = Color.White,
                normalContainerColor = AppColors.Accent,
                liquidContainerColor = AppColors.Accent,
                liquidScrimColor = AppColors.Accent
            )
        }

        AnimatedVisibility(
            visible = uiState.sheetMode == null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            TxtEditorBottomActions(
                canGoPrevious = uiState.chapterIndex > 0,
                canGoNext = uiState.chapterIndex < uiState.chapterCount - 1,
                backdrop = backdrop,
                onPrevious = {
                    viewModel.switchChapter(
                        -1,
                        textFieldValue.text,
                        textFieldValue.selection.start,
                        scrollState.value
                    )
                },
                onNext = {
                    viewModel.switchChapter(
                        1,
                        textFieldValue.text,
                        textFieldValue.selection.start,
                        scrollState.value
                    )
                },
                onSearch = {
                    keyboardController?.hide()
                    viewModel.openSheet(TxtEditorSheetMode.SEARCH)
                },
                onReplace = {
                    keyboardController?.hide()
                    viewModel.openSheet(TxtEditorSheetMode.REPLACE)
                },
                onTopChanged = { occluderTopPx = it }
            )
        }

        uiState.sheetMode?.let { mode ->
            TxtEditorSearchSheet(
                mode = mode,
                uiState = uiState,
                backdrop = backdrop,
                onTopChanged = { occluderTopPx = it },
                onQueryChange = viewModel::setSearchQuery,
                onReplacementChange = viewModel::setReplacementText,
                onScopeChange = viewModel::setSearchScope,
                onMatchCaseChange = viewModel::setMatchCase,
                onSearch = {
                    viewModel.updatePosition(textFieldValue.selection.start, scrollState.value)
                    viewModel.search(textFieldValue.text, textFieldValue.selection.start)
                },
                onPrevious = {
                    viewModel.updatePosition(textFieldValue.selection.start, scrollState.value)
                    viewModel.findPrevious(textFieldValue.text, textFieldValue.selection.start)
                },
                onNext = {
                    viewModel.updatePosition(textFieldValue.selection.start, scrollState.value)
                    viewModel.findNext(textFieldValue.text, textFieldValue.selection.start)
                },
                onReplaceCurrent = {
                    viewModel.updatePosition(textFieldValue.selection.start, scrollState.value)
                    viewModel.replaceCurrent(textFieldValue.text, textFieldValue.selection.start)
                },
                onReplaceAll = {
                    if (uiState.searchScope == TxtSearchScope.BOOK) {
                        showReplaceAllDialog = true
                    } else {
                        viewModel.replaceAll(textFieldValue.text, textFieldValue.selection.start)
                    }
                },
                onDismiss = viewModel::closeSheet
            )
        }

        if (uiState.isSaving) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AppColors.Accent)
            }
        }
    }

    if (showReplaceAllDialog) {
        LiquidGlassDialog(
            onDismissRequest = { showReplaceAllDialog = false },
            backgroundScrimColor = Color.Black.copy(alpha = 0.22f)
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text(
                    stringResource(R.string.txt_editor_replace_all_title),
                    fontSize = AppType.Section,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.txt_editor_replace_all_message, uiState.totalMatches),
                    color = AppColors.TextSecondary
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { showReplaceAllDialog = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, AppColors.Divider)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            showReplaceAllDialog = false
                            viewModel.replaceAll(textFieldValue.text, textFieldValue.selection.start)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
                    ) {
                        Text(stringResource(R.string.confirm), color = Color.White)
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        LiquidGlassDialog(
            onDismissRequest = { showDiscardDialog = false },
            backgroundScrimColor = Color.Black.copy(alpha = 0.22f)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
                Text(
                    stringResource(R.string.txt_editor_discard_title),
                    fontSize = AppType.Section,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.txt_editor_discard_message),
                    color = AppColors.TextSecondary
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { showDiscardDialog = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, AppColors.Divider)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = { showDiscardDialog = false; onNavigateBack(false) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
                    ) {
                        Text(stringResource(R.string.confirm), color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun TxtEditorBottomActions(
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    backdrop: Backdrop?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSearch: () -> Unit,
    onReplace: () -> Unit,
    onTopChanged: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .onGloballyPositioned { onTopChanged(it.boundsInRoot().top) },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TxtEditorActionCapsule(
            icon = Icons.Outlined.ChevronLeft,
            contentDescription = stringResource(R.string.txt_editor_previous_chapter),
            enabled = canGoPrevious,
            backdrop = backdrop,
            modifier = Modifier.size(46.dp),
            onClick = onPrevious
        )
        TxtEditorActionCapsule(
            icon = Icons.Outlined.ChevronRight,
            contentDescription = stringResource(R.string.txt_editor_next_chapter),
            enabled = canGoNext,
            backdrop = backdrop,
            modifier = Modifier.size(46.dp),
            onClick = onNext
        )
        TxtEditorActionCapsule(
            icon = Icons.Outlined.Search,
            label = stringResource(R.string.txt_editor_search),
            contentDescription = stringResource(R.string.txt_editor_search),
            backdrop = backdrop,
            modifier = Modifier.weight(1f).height(46.dp),
            onClick = onSearch
        )
        TxtEditorActionCapsule(
            icon = Icons.Outlined.FindReplace,
            label = stringResource(R.string.txt_editor_replace),
            contentDescription = stringResource(R.string.txt_editor_replace),
            backdrop = backdrop,
            modifier = Modifier.weight(1f).height(46.dp),
            onClick = onReplace
        )
    }
}

@Composable
private fun TxtEditorActionCapsule(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier,
    backdrop: Backdrop?,
    label: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = if (label == null) CircleShape else RoundedCornerShape(23.dp)
    LiquidGlassSurface(
        shape = shape,
        fallbackColor = AppColors.BgGray,
        backdrop = backdrop,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.alpha(if (enabled) 1f else 0.42f)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = if (label == null) 0.dp else 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = AppColors.TextPrimary,
                modifier = Modifier.size(19.dp)
            )
            if (label != null) {
                Spacer(Modifier.size(6.dp))
                Text(label, fontSize = 13.sp, color = AppColors.TextPrimary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TxtEditorSearchSheet(
    mode: TxtEditorSheetMode,
    uiState: TxtEditorUiState,
    backdrop: Backdrop?,
    onTopChanged: (Float) -> Unit,
    onQueryChange: (String) -> Unit,
    onReplacementChange: (String) -> Unit,
    onScopeChange: (TxtSearchScope) -> Unit,
    onMatchCaseChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplaceCurrent: () -> Unit,
    onReplaceAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetOffset = remember { Animatable(1f) }
    val queryFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isClosing by remember { mutableStateOf(false) }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler { isClosing = true }

    LaunchedEffect(Unit) {
        sheetOffset.snapTo(1f)
        sheetOffset.animateBottomSheetIn()
        queryFocusRequester.requestFocus()
        keyboardController?.show()
    }
    LaunchedEffect(isClosing) {
        if (isClosing) {
            sheetOffset.animateBottomSheetOut()
            onDismiss()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(AppColors.Scrim.copy(alpha = 0.18f * (1f - sheetOffset.value)))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { isClosing = true }
        )
        LiquidGlassSheetContainer(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onGloballyPositioned { onTopChanged(it.boundsInRoot().top) }
                .materialBottomSheetMotion(sheetOffset.value, predictiveBackProgress),
            contentModifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            backdrop = backdrop,
            fallbackColor = AppColors.CardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(
                            if (mode == TxtEditorSheetMode.SEARCH) {
                                R.string.txt_editor_search
                            } else {
                                R.string.txt_editor_replace
                            }
                        ),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = resolveAppFontFamily(KaiTi),
                        color = AppColors.TextPrimary
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { isClosing = true }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.reader_close),
                            tint = AppColors.TextPrimary
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                TxtEditorInput(
                    value = uiState.searchQuery,
                    placeholder = stringResource(R.string.txt_editor_search_placeholder),
                    onValueChange = onQueryChange,
                    onSearch = { onSearch() },
                    modifier = Modifier.focusRequester(queryFocusRequester),
                    trailing = {
                        IconButton(onClick = onSearch, enabled = uiState.searchQuery.isNotBlank()) {
                            Icon(Icons.Outlined.Search, null, tint = AppColors.TextPrimary)
                        }
                    }
                )
                if (mode == TxtEditorSheetMode.REPLACE) {
                    Spacer(Modifier.height(10.dp))
                    TxtEditorInput(
                        value = uiState.replacementText,
                        placeholder = stringResource(R.string.txt_editor_replace_placeholder),
                        onValueChange = onReplacementChange,
                        onSearch = { onReplaceCurrent() }
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TxtScopeSelector(
                        selected = uiState.searchScope,
                        onSelected = onScopeChange,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        stringResource(R.string.txt_editor_match_case),
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.size(6.dp))
                    Switch(checked = uiState.matchCase, onCheckedChange = onMatchCaseChange)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val resultText = if (uiState.totalMatches > 0) {
                        stringResource(
                            R.string.txt_editor_match_count,
                            uiState.currentMatchOrdinal,
                            uiState.totalMatches
                        )
                    } else {
                        stringResource(R.string.txt_editor_no_matches)
                    }
                    Text(resultText, fontSize = 13.sp, color = AppColors.TextSecondary)
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = onPrevious,
                        enabled = uiState.totalMatches > 0 && !uiState.isSearching
                    ) {
                        Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.txt_editor_previous_match))
                    }
                    IconButton(
                        onClick = onNext,
                        enabled = uiState.totalMatches > 0 && !uiState.isSearching
                    ) {
                        Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.txt_editor_next_match))
                    }
                }
                if (uiState.isSearching) {
                    LinearProgressIndicator(
                        progress = { uiState.searchProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = AppColors.Accent
                    )
                }
                if (mode == TxtEditorSheetMode.REPLACE) {
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onReplaceCurrent,
                            enabled = uiState.currentMatch != null && !uiState.isSearching,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(22.dp),
                            border = BorderStroke(1.dp, AppColors.Divider)
                        ) {
                            Text(stringResource(R.string.txt_editor_replace_current))
                        }
                        Button(
                            onClick = onReplaceAll,
                            enabled = uiState.totalMatches > 0 && !uiState.isSearching,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
                        ) {
                            Text(stringResource(R.string.txt_editor_replace_all), color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TxtEditorInput(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onSearch: KeyboardActionScope.() -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, fontSize = 14.sp) },
        singleLine = true,
        trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = onSearch),
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = AppColors.BgGray,
            unfocusedContainerColor = AppColors.BgGray,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun TxtScopeSelector(
    selected: TxtSearchScope,
    onSelected: (TxtSearchScope) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).background(AppColors.BgGray).padding(3.dp)
    ) {
        TxtScopeOption(
            text = stringResource(R.string.txt_editor_scope_chapter),
            selected = selected == TxtSearchScope.CHAPTER,
            modifier = Modifier.weight(1f),
            onClick = { onSelected(TxtSearchScope.CHAPTER) }
        )
        TxtScopeOption(
            text = stringResource(R.string.txt_editor_scope_book),
            selected = selected == TxtSearchScope.BOOK,
            modifier = Modifier.weight(1f),
            onClick = { onSelected(TxtSearchScope.BOOK) }
        )
    }
}

@Composable
private fun TxtScopeOption(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (selected) AppColors.Accent else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = if (selected) Color.White else AppColors.TextSecondary,
            maxLines = 1
        )
    }
}

private class OemTxtEditText(context: Context) : EditText(context) {
    var onUserTextChanged: ((String, Int, Int) -> Unit)? = null
    var onUserSelectionChanged: ((Int, Int) -> Unit)? = null
    private var applyingState = false

    init {
        background = null
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setSingleLine(false)
        setHorizontallyScrolling(false)
        isVerticalScrollBarEnabled = false
        includeFontPadding = true
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setLineSpacing(0f, 1.32f)
        typeface = Typeface.create("serif", Typeface.NORMAL)
        setPadding(0, 0, 0, 0)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(editable: Editable?) {
                if (applyingState) return
                post {
                    if (!applyingState) {
                        onUserTextChanged?.invoke(
                            editable?.toString().orEmpty(),
                            selectionStart.coerceAtLeast(0),
                            selectionEnd.coerceAtLeast(0)
                        )
                    }
                }
            }
        })
    }

    override fun onSelectionChanged(selectionStart: Int, selectionEnd: Int) {
        super.onSelectionChanged(selectionStart, selectionEnd)
        if (!applyingState && selectionStart >= 0 && selectionEnd >= 0) {
            onUserSelectionChanged?.invoke(selectionStart, selectionEnd)
        }
    }

    fun applyEditorState(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        highlightRange: IntRange?,
        textColor: Int,
        hintColor: Int,
        highlightColor: Int
    ) {
        applyingState = true
        try {
            setTextColor(textColor)
            setHintTextColor(hintColor)
            hint = context.getString(R.string.txt_editor_placeholder)
            if (this.text.toString() != text) {
                setText(text)
            }
            val safeStart = selectionStart.coerceIn(0, this.text.length)
            val safeEnd = selectionEnd.coerceIn(safeStart, this.text.length)
            if (this.selectionStart != safeStart || this.selectionEnd != safeEnd) {
                setSelection(safeStart, safeEnd)
            }
            this.text.getSpans(0, this.text.length, TxtSearchHighlightSpan::class.java)
                .forEach(this.text::removeSpan)
            if (highlightRange != null &&
                highlightRange.first >= 0 &&
                highlightRange.first < this.text.length
            ) {
                this.text.setSpan(
                    TxtSearchHighlightSpan(highlightColor),
                    highlightRange.first,
                    (highlightRange.last + 1).coerceAtMost(this.text.length),
                    Editable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } finally {
            applyingState = false
        }
    }
}

private class TxtSearchHighlightSpan(color: Int) : BackgroundColorSpan(color)
