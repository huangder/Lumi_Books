package com.huangder.lumibooks.ui.reader

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextPaint
import android.text.TextWatcher
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
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
import androidx.core.widget.NestedScrollView
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.ConfigurableBottomSheetBackHandler
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
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
import kotlin.math.roundToInt

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
    var textFieldValue by remember { mutableStateOf(TextFieldValue()) }
    var editorHostRef by remember { mutableStateOf<OemTxtEditorHost?>(null) }
    var editTextRef by remember { mutableStateOf<OemTxtEditText?>(null) }
    var screenBottomPx by remember { mutableStateOf(0f) }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    var occluderTopPx by remember { mutableStateOf(0f) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showReplaceAllDialog by remember { mutableStateOf(false) }
    var initialRevealVisible by remember { mutableStateOf(false) }
    val initialRevealAlpha = remember { Animatable(0f) }

    fun currentCursor(): Int = editTextRef?.selectionStart
        ?.takeIf { it >= 0 }
        ?: textFieldValue.selection.start
    fun currentScrollPosition(): Int = editorHostRef?.scrollY ?: 0

    val statusBarTopDp = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val topBarHeightDp = if (topBarHeightPx > 0) {
        with(density) { topBarHeightPx.toDp() }
    } else {
        statusBarTopDp + AppSpace.sm + 40.dp + AppSpace.sm
    }
    val bottomSpacerDp = with(density) {
        val fallback = 96.dp.toPx()
        val covered = if (screenBottomPx > 0f && occluderTopPx > 0f) {
            screenBottomPx - occluderTopPx
        } else fallback
        (covered + 24.dp.toPx()).coerceAtLeast(fallback).toDp()
    }

    LaunchedEffect(uiState.chapterRevision) {
        if (uiState.chapterRevision <= 0) return@LaunchedEffect
        val start = uiState.targetSelectionStart.coerceIn(0, uiState.chapterText.length)
        val end = uiState.targetSelectionEnd.coerceIn(start, uiState.chapterText.length)
        textFieldValue = TextFieldValue(uiState.chapterText, TextRange(start, end))
        if (uiState.currentMatch == null && uiState.initialRevealRange == null) {
            repeat(3) { attempt ->
                delay(if (attempt == 0) 20 else 60)
                editorHostRef?.let { host ->
                    host.scrollTo(
                        0,
                        uiState.restoreScrollPosition.coerceIn(0, host.maxScrollY())
                    )
                }
            }
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

    LaunchedEffect(
        uiState.initialRevealRange,
        uiState.chapterRevision,
        editorHostRef,
        editTextRef
    ) {
        val range = uiState.initialRevealRange ?: return@LaunchedEffect
        val host = editorHostRef ?: return@LaunchedEffect
        val editText = editTextRef ?: return@LaunchedEffect
        if (range.first < 0) return@LaunchedEffect
        try {
            initialRevealVisible = false
            initialRevealAlpha.snapTo(0f)

            // The keyboard and OEM EditText can both relayout after the text is first applied.
            // Keep the reveal request alive until the native viewport has settled.
            delay(240)
            var stableSamples = 0
            var previousHostHeight = -1
            var previousEditorHeight = -1
            while (stableSamples < 5) {
                val textReady = editText.text.isNotEmpty() && range.last < editText.text.length
                val layoutReady = editText.layout != null && host.height > 0 && editText.height > 0
                if (textReady && layoutReady &&
                    host.height == previousHostHeight && editText.height == previousEditorHeight
                ) {
                    stableSamples++
                } else {
                    stableSamples = 0
                }
                previousHostHeight = host.height
                previousEditorHeight = editText.height
                delay(64)
            }

            var positioned = false
            while (!positioned) {
                positioned = revealEditorRange(host, editText, range)
                delay(48)
                positioned = positioned && isEditorRangeVisible(host, editText, range)
            }
            initialRevealVisible = true
            repeat(2) { flashIndex ->
                revealEditorRange(host, editText, range)
                initialRevealAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(650, easing = FastOutSlowInEasing)
                )
                delay(450)
                initialRevealAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(750, easing = FastOutSlowInEasing)
                )
                if (flashIndex == 0) delay(350)
            }
        } finally {
            initialRevealVisible = false
        }
        viewModel.consumeInitialReveal()
    }

    LaunchedEffect(
        uiState.currentMatch,
        uiState.chapterRevision,
        uiState.sheetMode,
        editorHostRef,
        editTextRef
    ) {
        val match = uiState.currentMatch ?: return@LaunchedEffect
        if (uiState.sheetMode == null) return@LaunchedEffect
        if (match.chapterIndex != uiState.chapterIndex) return@LaunchedEffect
        val host = editorHostRef ?: return@LaunchedEffect
        val editText = editTextRef ?: return@LaunchedEffect
        if (editText.text.isEmpty() || match.start >= editText.text.length) return@LaunchedEffect
        repeat(4) { attempt ->
            delay(if (attempt == 0) 32 else 90)
            val sheetTop = occluderTopPx
            val layout = editText.layout
            if (sheetTop <= 0f || layout == null) {
                return@repeat
            }
            val startLine = layout.getLineForOffset(match.start.coerceAtMost(editText.text.length - 1))
            val endLine = layout.getLineForOffset(
                (match.endExclusive - 1).coerceIn(match.start, editText.text.length - 1)
            )
            val editorLocation = IntArray(2)
            editText.getLocationInWindow(editorLocation)
            val editorTop = editorLocation[1].toFloat()
            val targetTop = editorTop + editText.totalPaddingTop +
                layout.getLineTop(startLine)
            val targetBottom = editorTop + editText.totalPaddingTop +
                layout.getLineBottom(endLine)
            val safeTop = topBarHeightPx + with(density) { 16.dp.toPx() }
            val lineHeight = (targetBottom - targetTop).coerceAtLeast(1f)
            val desiredBottom = (sheetTop - with(density) { 20.dp.toPx() })
                .coerceAtLeast(safeTop + lineHeight)
            val requestedDelta = (targetBottom - desiredBottom).roundToInt()
            val targetScroll = (host.scrollY + requestedDelta).coerceIn(0, host.maxScrollY())
            if (targetScroll != host.scrollY) {
                host.scrollTo(0, targetScroll)
            }
        }
    }

    LaunchedEffect(
        uiState.sheetMode,
        uiState.searchQuery,
        uiState.searchScope,
        uiState.matchCase
    ) {
        if (uiState.sheetMode == null || uiState.searchQuery.isBlank()) return@LaunchedEffect
        viewModel.markSearchPending()
        delay(if (uiState.searchScope == TxtSearchScope.BOOK) 450 else 220)
        viewModel.updatePosition(currentCursor(), currentScrollPosition())
        viewModel.search(textFieldValue.text, currentCursor())
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
        viewModel.updatePosition(currentCursor(), currentScrollPosition())
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

    val searchHighlightRange = uiState.currentMatch
        ?.takeIf { it.chapterIndex == uiState.chapterIndex }
        ?.let { it.start until it.endExclusive }
    val accentColor = AppColors.Accent
    val editorTextColor = AppColors.TextPrimary.toArgb()
    val editorHintColor = AppColors.TextSecondary.copy(alpha = 0.5f).toArgb()
    val editorHighlightRange = searchHighlightRange
        ?: uiState.initialRevealRange?.takeIf { initialRevealVisible }
    val editorHighlightColor = accentColor.copy(
        alpha = if (searchHighlightRange != null) {
            0.32f
        } else {
            0.46f * initialRevealAlpha.value
        }
    ).toArgb()

    Box(
        Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
            .onGloballyPositioned { screenBottomPx = it.boundsInWindow().bottom }
    ) {
        AndroidView(
            factory = { androidContext ->
                OemTxtEditorHost(androidContext).also { host ->
                    editorHostRef = host
                    editTextRef = host.editor
                    val editText = host.editor
                    editText.onUserTextChanged = { text, start, end ->
                        if (text != textFieldValue.text) viewModel.invalidateSearchResult()
                        textFieldValue = TextFieldValue(text, TextRange(start, end))
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
            update = { host ->
                val editText = host.editor
                editText.onUserTextChanged = { text, start, end ->
                    if (text != textFieldValue.text) viewModel.invalidateSearchResult()
                    textFieldValue = TextFieldValue(text, TextRange(start, end))
                }
                editText.applyEditorState(
                    text = textFieldValue.text,
                    selectionStart = textFieldValue.selection.start,
                    selectionEnd = textFieldValue.selection.end,
                    selectionRevision = uiState.chapterRevision,
                    highlightRange = editorHighlightRange,
                    textColor = editorTextColor,
                    hintColor = editorHintColor,
                    highlightColor = editorHighlightColor
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = AppSpace.md,
                    top = topBarHeightDp + AppSpace.sm,
                    end = AppSpace.md,
                    bottom = bottomSpacerDp
                )
        )

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
                        currentCursor(),
                        currentScrollPosition()
                    ) { structureChanged ->
                        if (structureChanged) {
                            Toast.makeText(
                                context,
                                R.string.txt_editor_structure_changed_warning,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        onNavigateBack(true)
                    }
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
                        currentCursor(),
                        currentScrollPosition()
                    )
                },
                onNext = {
                    viewModel.switchChapter(
                        1,
                        textFieldValue.text,
                        currentCursor(),
                        currentScrollPosition()
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
                    viewModel.updatePosition(currentCursor(), currentScrollPosition())
                    viewModel.search(textFieldValue.text, currentCursor())
                },
                onPrevious = {
                    viewModel.updatePosition(currentCursor(), currentScrollPosition())
                    viewModel.findPrevious(textFieldValue.text, currentCursor())
                },
                onNext = {
                    viewModel.updatePosition(currentCursor(), currentScrollPosition())
                    viewModel.findNext(textFieldValue.text, currentCursor())
                },
                onReplaceCurrent = {
                    viewModel.updatePosition(currentCursor(), currentScrollPosition())
                    viewModel.replaceCurrent(textFieldValue.text, currentCursor())
                },
                onReplaceAll = {
                    if (uiState.searchScope == TxtSearchScope.BOOK) {
                        showReplaceAllDialog = true
                    } else {
                        viewModel.replaceAll(textFieldValue.text, currentCursor())
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
                            viewModel.replaceAll(textFieldValue.text, currentCursor())
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
            .onGloballyPositioned { onTopChanged(it.boundsInWindow().top) },
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
        forceFallback = true,
        highlightColor = Color.White,
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

    Box(Modifier.fillMaxSize().imePadding()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(AppColors.Scrim.copy(alpha = 0.18f * (1f - sheetOffset.value)))
        )
        val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onGloballyPositioned { onTopChanged(it.boundsInWindow().top) }
                .materialBottomSheetMotion(sheetOffset.value, predictiveBackProgress)
                .shadow(
                    elevation = 20.dp,
                    shape = sheetShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.14f),
                    spotColor = Color.Black.copy(alpha = 0.20f)
                )
                .clip(sheetShape)
                .background(AppColors.CardBg)
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 18.dp)
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
                val searchOrNext = {
                    if (uiState.currentMatch != null && uiState.totalMatches > 0) {
                        onNext()
                    } else {
                        onSearch()
                    }
                }
                TxtEditorInput(
                    value = uiState.searchQuery,
                    placeholder = stringResource(R.string.txt_editor_search_placeholder),
                    onValueChange = onQueryChange,
                    onSearch = { searchOrNext() },
                    modifier = Modifier.focusRequester(queryFocusRequester),
                    trailing = {
                        IconButton(
                            onClick = searchOrNext,
                            enabled = uiState.searchQuery.isNotBlank() && !uiState.isSearching
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = stringResource(
                                    if (uiState.currentMatch != null) {
                                        R.string.txt_editor_next_match
                                    } else {
                                        R.string.txt_editor_search
                                    }
                                ),
                                tint = AppColors.TextPrimary
                            )
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
                val resultText = when {
                    uiState.isSearching -> stringResource(R.string.txt_editor_searching)
                    uiState.searchFailed -> stringResource(R.string.txt_editor_search_failed)
                    uiState.totalMatches > 0 -> stringResource(
                        R.string.txt_editor_match_count,
                        uiState.currentMatchOrdinal,
                        uiState.totalMatches
                    )
                    else -> stringResource(R.string.txt_editor_no_matches)
                }
                Text(resultText, fontSize = 13.sp, color = AppColors.TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onPrevious,
                        enabled = uiState.totalMatches > 0 && !uiState.isSearching,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, AppColors.Divider)
                    ) {
                        Icon(
                            Icons.Outlined.KeyboardArrowUp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.txt_editor_previous_match))
                    }
                    Button(
                        onClick = onNext,
                        enabled = uiState.totalMatches > 0 && !uiState.isSearching,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
                    ) {
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.txt_editor_next_match), color = Color.White)
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

private class OemTxtEditorHost(context: Context) : NestedScrollView(context) {
    val editor = OemTxtEditText(context)

    init {
        isFillViewport = true
        isSmoothScrollingEnabled = true
        isNestedScrollingEnabled = true
        isVerticalScrollBarEnabled = false
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        addView(
            editor,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    fun maxScrollY(): Int {
        val child = getChildAt(0) ?: return 0
        val viewportHeight = (height - paddingTop - paddingBottom).coerceAtLeast(0)
        return (child.height - viewportHeight).coerceAtLeast(0)
    }
}

private fun revealEditorRange(
    host: OemTxtEditorHost,
    editText: OemTxtEditText,
    range: IntRange
): Boolean {
    val layout = editText.layout ?: return false
    if (editText.text.isEmpty() || host.height <= 0 || range.first !in editText.text.indices) {
        return false
    }
    val startOffset = range.first.coerceIn(0, editText.text.length - 1)
    val endOffset = range.last.coerceIn(startOffset, editText.text.length - 1)
    val startLine = layout.getLineForOffset(startOffset)
    val endLine = layout.getLineForOffset(endOffset)

    val targetTop = editText.top + editText.totalPaddingTop + layout.getLineTop(startLine)
    val targetBottom = editText.top + editText.totalPaddingTop + layout.getLineBottom(endLine)
    val viewportHeight = (host.height - host.paddingTop - host.paddingBottom).coerceAtLeast(1)
    val targetCenter = (targetTop + targetBottom) / 2
    val targetScroll = (targetCenter - host.paddingTop - viewportHeight / 2)
        .coerceIn(0, host.maxScrollY())
    if (targetScroll != host.scrollY) host.scrollTo(0, targetScroll)
    return isEditorRangeVisible(host, editText, range)
}

private fun isEditorRangeVisible(
    host: OemTxtEditorHost,
    editText: OemTxtEditText,
    range: IntRange
): Boolean {
    val layout = editText.layout ?: return false
    if (editText.text.isEmpty() || host.height <= 0 || range.first !in editText.text.indices) {
        return false
    }
    val startOffset = range.first.coerceIn(0, editText.text.length - 1)
    val endOffset = range.last.coerceIn(startOffset, editText.text.length - 1)
    val targetTop = editText.top + editText.totalPaddingTop +
        layout.getLineTop(layout.getLineForOffset(startOffset))
    val targetBottom = editText.top + editText.totalPaddingTop +
        layout.getLineBottom(layout.getLineForOffset(endOffset))
    val viewportTop = host.scrollY + host.paddingTop
    val viewportBottom = host.scrollY + host.height - host.paddingBottom
    return targetBottom > viewportTop && targetTop < viewportBottom
}

private class OemTxtEditText(context: Context) : EditText(context) {
    var onUserTextChanged: ((String, Int, Int) -> Unit)? = null
    private var applyingState = false
    private var appliedSelectionRevision = Int.MIN_VALUE
    private var appliedHighlightStart = -1
    private var appliedHighlightEnd = -1
    private var appliedHighlightColor = 0
    private var appliedHighlightSpan: TxtSearchHighlightSpan? = null

    init {
        background = null
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setSingleLine(false)
        setHorizontallyScrolling(false)
        isClickable = true
        isLongClickable = true
        isFocusableInTouchMode = true
        showSoftInputOnFocus = true
        customSelectionActionModeCallback = null
        customInsertionActionModeCallback = null
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

    fun applyEditorState(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        selectionRevision: Int,
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
                appliedSelectionRevision = Int.MIN_VALUE
                appliedHighlightStart = -1
                appliedHighlightEnd = -1
                appliedHighlightColor = 0
                appliedHighlightSpan = null
            }
            if (selectionRevision != appliedSelectionRevision) {
                val safeStart = selectionStart.coerceIn(0, this.text.length)
                val safeEnd = selectionEnd.coerceIn(safeStart, this.text.length)
                if (this.selectionStart != safeStart || this.selectionEnd != safeEnd) {
                    setSelection(safeStart, safeEnd)
                }
                appliedSelectionRevision = selectionRevision
            }
            val highlightStart = highlightRange?.first
                ?.takeIf { it >= 0 && it < this.text.length }
                ?: -1
            val highlightEnd = if (highlightStart >= 0) {
                (highlightRange!!.last + 1).coerceIn(highlightStart + 1, this.text.length)
            } else {
                -1
            }
            val highlightRangeChanged = highlightStart != appliedHighlightStart ||
                highlightEnd != appliedHighlightEnd
            if (highlightRangeChanged || (highlightStart >= 0 && appliedHighlightSpan == null)) {
                this.text.getSpans(0, this.text.length, TxtSearchHighlightSpan::class.java)
                    .forEach(this.text::removeSpan)
                appliedHighlightSpan = null
                if (highlightStart >= 0) {
                    val span = TxtSearchHighlightSpan(highlightColor)
                    this.text.setSpan(
                        span,
                        highlightStart,
                        highlightEnd,
                        Editable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    appliedHighlightSpan = span
                }
                appliedHighlightStart = highlightStart
                appliedHighlightEnd = highlightEnd
                appliedHighlightColor = highlightColor
            } else if (highlightColor != appliedHighlightColor) {
                appliedHighlightSpan?.color = highlightColor
                appliedHighlightColor = highlightColor
                invalidate()
            }
        } finally {
            applyingState = false
        }
    }
}

private class TxtSearchHighlightSpan(var color: Int) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(textPaint: TextPaint) {
        textPaint.bgColor = color
    }
}
