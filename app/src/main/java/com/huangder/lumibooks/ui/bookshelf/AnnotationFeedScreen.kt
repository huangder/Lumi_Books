package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.ui.icons.ControlIcon

import com.huangder.lumibooks.ui.components.LocalCollapsingPageTopPaddingValues
import com.huangder.lumibooks.ui.components.CollapsingPageScaffold
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.huangder.lumibooks.ui.animation.LumiMotion
import com.huangder.lumibooks.ui.components.LocalLiquidGlassBackdrop
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.LocalLumiBackgroundBackdrop
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.ui.components.AnnotationListFilters
import com.huangder.lumibooks.ui.components.AnnotationTagSheet
import com.huangder.lumibooks.ui.components.ConfigurableBackHandler
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.excerpt.ExcerptLauncher
import com.huangder.lumibooks.ui.excerpt.ExcerptRequest
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.reader.NoteInputSheet
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun AnnotationFeedScreen(
    onNavigateBack: () -> Unit,
    viewModel: AnnotationFeedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val displayEnvironment = rememberAnnotationFeedDisplayEnvironment()
    LaunchedEffect(displayEnvironment) { viewModel.setDisplayEnvironment(displayEnvironment) }
    com.huangder.lumibooks.ui.components.ReconcileAnnotationTagDraft(listOfNotNull(uiState.selectedTag)) {
        viewModel.setTag(it.firstOrNull())
    }
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val excerptDark = LocalIsDarkTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val glassBackdrop = rememberLayerBackdrop()
    val segmentedControlBackdrop = rememberLayerBackdrop()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }
    var editingNoteTags by remember { mutableStateOf<Note?>(null) }
    var editingBookmarkTags by remember { mutableStateOf<Bookmark?>(null) }
    var editingBookmarkRemark by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkRemarkText by remember { mutableStateOf("") }
    var bookmarkRemarkTags by remember { mutableStateOf<List<String>>(emptyList()) }

    // The field owns the text so typing never waits on filtering; re-sync after process death.
    LaunchedEffect(Unit) { if (searchOpen) viewModel.setSearchText(searchText) }
    val openSearch: () -> Unit = {
        searchText = ""
        searchOpen = true
    }
    // The old text stays in the shrinking capsule; only the filter clears at once.
    val closeSearch: () -> Unit = {
        focusManager.clearFocus()
        searchOpen = false
        viewModel.setSearchText("")
    }
    ConfigurableBackHandler(enabled = searchOpen) { closeSearch() }

    val shareEntry: (AnnotationFeedEntry) -> Unit = { entry ->
        scope.launch {
            runCatching {
                val request = when (entry) {
                    is AnnotationFeedEntry.NoteEntry -> ExcerptRequest.fromNote(entry.book, entry.note,
                        context.getString(R.string.chapter_number, entry.note.chapterIndex + 1))
                    is AnnotationFeedEntry.BookmarkEntry -> ExcerptRequest.fromBookmark(entry.book, entry.bookmark,
                        context.getString(R.string.chapter_number, entry.bookmark.chapterIndex + 1))
                }
                ExcerptLauncher.open(context, viewModel.excerptTheme(entry.book.id, request, excerptDark))
            }.onFailure { Toast.makeText(context, R.string.excerpt_failed, Toast.LENGTH_SHORT).show() }
        }
    }
    // Same order as AnnotationFeedType.
    val tabs = listOf(
        stringResource(R.string.annotation_feed_tab_all),
        stringResource(R.string.tab_highlights),
        stringResource(R.string.tab_underline),
        stringResource(R.string.tab_notes),
        stringResource(R.string.tab_bookmarks)
    )

    LiquidGlassMenuHost(
        // Controls sample only the background; the host captures the completed page
        // separately for menus. Sampling glassBackdrop here would capture themselves.
        backdrop = segmentedControlBackdrop.takeIf { isLiquidGlass },
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.PageBg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isLiquidGlass) Modifier.layerBackdrop(segmentedControlBackdrop) else Modifier)
                .background(AppColors.PageBg)
        )
        CollapsingPageScaffold(
            title = stringResource(R.string.annotation_feed_title),
            drawContentBehindHeader = true,
            onBack = onNavigateBack,
            modifier = Modifier.fillMaxSize()
                .then(if (isLiquidGlass) Modifier.layerBackdrop(glassBackdrop) else Modifier),
            actions = {
                LiquidGlassIconButton(
                    imageVector = AppIcons.MagnifyingGlass,
                    contentDescription = stringResource(R.string.search),
                    size = com.huangder.lumibooks.ui.components.PageToolbarButtonSize,
                    iconSize = com.huangder.lumibooks.ui.components.PageToolbarIconSize,
                    onClick = { if (searchOpen) closeSearch() else openSearch() }
                )
                com.huangder.lumibooks.edition.EditionAnnotationFeedExportButton(
                    entries = uiState.entries,
                    enabled = !uiState.isLoading && uiState.entries.isNotEmpty()
                )
                com.huangder.lumibooks.edition.EditionTagManagementButton(
                    com.huangder.lumibooks.domain.model.TagTypes.ANNOTATION, toolbar = true
                )
            },
            pinnedContent = {
                Column {
                    AnnotationFeedSearchField(
                        open = searchOpen,
                        text = searchText,
                        onTextChange = { searchText = it; viewModel.setSearchText(it) },
                        onClose = closeSearch,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpace.lg, vertical = AppSpace.sm)
                    )
                    SegmentedTabBar(
                        selectedTab = uiState.type.ordinal,
                        tabs = tabs,
                        onTabSelected = { viewModel.setType(AnnotationFeedType.entries[it]) },
                        modifier = Modifier.padding(horizontal = AppSpace.lg),
                        backdrop = segmentedControlBackdrop.takeIf { isLiquidGlass },
                        labelFontSize = 13.sp
                    )

                    Spacer(Modifier.height(AppSpace.md))

                    AnnotationListFilters(
                        tags = uiState.tags,
                        selectedTag = uiState.selectedTag,
                        onTagSelected = viewModel::setTag,
                        colors = uiState.colors,
                        selectedColor = uiState.selectedColor,
                        onColorSelected = viewModel::setColor,
                        selectedLine = uiState.selectedLine,
                        onLineSelected = viewModel::setLine,
                        showLines = uiState.type == AnnotationFeedType.UNDERLINES,
                        modifier = Modifier.padding(horizontal = AppSpace.lg)
                    )
                }
            }
        ) {
            AnnotationFeedList(
                uiState = uiState,
                listState = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.PageBg),
                onShare = shareEntry,
                onEditNoteTags = { editingNoteTags = it },
                onEditBookmarkTags = { editingBookmarkTags = it },
                onEditBookmarkRemark = { bookmark ->
                    editingBookmarkRemark = bookmark
                    bookmarkRemarkText = bookmark.remark
                    bookmarkRemarkTags = bookmark.tags
                },
                onDeleteNote = viewModel::deleteNote,
                onDeleteBookmark = viewModel::deleteBookmark,
                onToggleRuleHighlights = { viewModel.setIncludeRuleHighlights(!uiState.includeRuleHighlights) }
            )
        }
        NoteInputSheet(
            visible = editingBookmarkRemark != null,
            glassBackdrop = glassBackdrop.takeIf { isLiquidGlass },
            initialText = bookmarkRemarkText,
            onTextChange = { bookmarkRemarkText = it },
            tags = bookmarkRemarkTags,
            availableTags = uiState.availableTags,
            onTagsChange = { bookmarkRemarkTags = it },
            onCreateTag = viewModel::createTag,
            onConfirm = {
                editingBookmarkRemark?.let {
                    viewModel.updateBookmarkRemarkAndTags(it, bookmarkRemarkText, bookmarkRemarkTags)
                }
            },
            onDismiss = { editingBookmarkRemark = null; bookmarkRemarkText = "" },
            title = stringResource(R.string.bookmark_remark_title),
            placeholder = stringResource(R.string.bookmark_remark_placeholder),
            footer = stringResource(R.string.bookmark_remark_setting_hint)
        )
        editingNoteTags?.let { note ->
            AnnotationTagSheet(
                backdrop = glassBackdrop.takeIf { isLiquidGlass },
                selected = note.tags,
                available = uiState.availableTags,
                onSave = { viewModel.updateNoteTags(note, it) },
                onCreate = viewModel::createTag,
                onDismiss = { editingNoteTags = null },
                onRename = viewModel::renameTag,
                onDelete = viewModel::deleteTag
            )
        }
        editingBookmarkTags?.let { bookmark ->
            AnnotationTagSheet(
                backdrop = glassBackdrop.takeIf { isLiquidGlass },
                selected = bookmark.tags,
                available = uiState.availableTags,
                onSave = { viewModel.updateBookmarkTags(bookmark, it) },
                onCreate = viewModel::createTag,
                onDismiss = { editingBookmarkTags = null },
                onRename = viewModel::renameTag,
                onDelete = viewModel::deleteTag
            )
        }
    }
}

@Composable
private fun AnnotationFeedList(
    uiState: AnnotationFeedUiState,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onShare: (AnnotationFeedEntry) -> Unit,
    onEditNoteTags: (Note) -> Unit,
    onEditBookmarkTags: (Bookmark) -> Unit,
    onEditBookmarkRemark: (Bookmark) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit,
    onToggleRuleHighlights: () -> Unit
) {
    val collapsingTopPadding = LocalCollapsingPageTopPaddingValues.current
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + AppSpace.xl
    val contentPadding = remember(collapsingTopPadding, bottomPadding) {
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: androidx.compose.ui.unit.LayoutDirection) = AppSpace.lg
            override fun calculateRightPadding(layoutDirection: androidx.compose.ui.unit.LayoutDirection) = AppSpace.lg
            override fun calculateTopPadding() = collapsingTopPadding.calculateTopPadding() + AppSpace.sm
            override fun calculateBottomPadding() = bottomPadding
        }
    }
    // Room usually answers within a frame, so stay blank first (no flash) and only show the
    // skeleton when loading genuinely takes a moment.
    if (uiState.isLoading) {
        var showSkeleton by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(250); showSkeleton = true }
        if (showSkeleton) LazyColumn(state = listState, modifier = modifier, contentPadding = contentPadding) {
            item(key = "loading", contentType = "loading") {
                com.huangder.lumibooks.ui.components.LumiListSkeleton(rows = 5, rowHeight = 92.dp)
            }
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
    ) {
        if (uiState.ruleHighlightCount > 0) {
            item(key = "rule_toggle", contentType = "rule-toggle") {
                RuleHighlightToggle(
                    count = uiState.ruleHighlightCount,
                    shown = uiState.includeRuleHighlights,
                    onToggle = onToggleRuleHighlights
                )
            }
        }
        if (uiState.entries.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                com.huangder.lumibooks.ui.components.EmptyState(
                    message = stringResource(R.string.no_content),
                    icon = com.huangder.lumibooks.ui.icons.AppIcons.PencilSimple,
                    modifier = Modifier.fillMaxWidth().padding(top = 72.dp)
                )
            }
        }
        items(uiState.rows, key = { it.key }, contentType = { it.contentType }) { row ->
            when (row) {
                is AnnotationFeedRow.Day -> Box(Modifier.animateItem()) { DayHeader(row.label) }
                is AnnotationFeedRow.Entry -> {
                    val entry = row.entry
                    // Filters, tabs and deletions move rows instead of snapping them.
                    Box(Modifier.animateItem()) {
                        when (entry) {
                            is AnnotationFeedEntry.NoteEntry -> HighlightNoteItem(
                                note = entry.note,
                                onShare = { onShare(entry) },
                                onEditTags = { onEditNoteTags(entry.note) },
                                onDelete = { onDeleteNote(entry.note) },
                                header = { FeedBookHeader(entry.book) },
                                dateText = row.timeText,
                                expandable = true,
                                cornerRadius = AppRadius.lg
                            )
                            is AnnotationFeedEntry.BookmarkEntry -> BookmarkItem(
                                bookmark = entry.bookmark,
                                onShare = { onShare(entry) },
                                onEditRemark = { onEditBookmarkRemark(entry.bookmark) },
                                onEditTags = { onEditBookmarkTags(entry.bookmark) },
                                onDelete = { onDeleteBookmark(entry.bookmark) },
                                header = { FeedBookHeader(entry.book) },
                                dateText = row.timeText,
                                cornerRadius = AppRadius.lg
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Keep the transition composed while closed so both entering and leaving have a visible ramp. */
@Composable
internal fun AnnotationFeedSearchField(
    open: Boolean,
    text: String,
    onTextChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motionEnabled = LocalMotionEnabled.current
    val eInk = LocalEInkMode.current
    AnimatedVisibility(
        visible = open,
        modifier = Modifier.fillMaxWidth(),
        enter = when {
            eInk -> EnterTransition.None
            motionEnabled -> expandVertically(animationSpec = tween(LumiMotion.MenuEnterMillis), expandFrom = Alignment.Top) +
                fadeIn(tween(LumiMotion.MenuEnterMillis))
            else -> fadeIn(tween(100))
        },
        exit = when {
            eInk -> ExitTransition.None
            motionEnabled -> shrinkVertically(animationSpec = tween(LumiMotion.MenuExitMillis), shrinkTowards = Alignment.Top) +
                fadeOut(tween(LumiMotion.MenuExitMillis))
            else -> fadeOut(tween(100))
        }
    ) {
        Box(modifier.then(if (open) Modifier else Modifier.clearAndSetSemantics {})) {
            LiquidGlassSurface(
                controlEdge = true,
                shape = CircleShape,
                backdrop = LocalLiquidGlassBackdrop.current ?: LocalLumiBackgroundBackdrop.current,
                fallbackColor = if (LocalAppTheme.current == "liquid_glass") AppColors.CardBg else AppColors.BgGray,
                contentScrimColor = AppColors.CardBg.copy(alpha = if (LocalIsDarkTheme.current) 0.60f else 0.72f),
                modifier = Modifier.fillMaxWidth().height(SearchButtonSize)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        AnnotationSearchInput(text = text, onTextChange = onTextChange, enabled = open)
                    }
                    Box(
                        modifier = Modifier.size(SearchButtonSize).clip(CircleShape)
                            .clickable(enabled = open, onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        ControlIcon(AppIcons.X, stringResource(R.string.close), tint = AppColors.TextPrimary,
                            modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(label: String) {
    Text(
        text = label,
        fontSize = AppType.Caption,
        fontWeight = FontWeight.SemiBold,
        color = AppColors.TextSecondary,
        modifier = Modifier.padding(top = AppSpace.sm, start = AppSpace.xs)
    )
}

@Composable
private fun rememberAnnotationFeedDisplayEnvironment(): AnnotationFeedDisplayEnvironment {
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val locale = LocalConfiguration.current.locales[0]
    val todayLabel = stringResource(R.string.annotation_feed_today)
    val yesterdayLabel = stringResource(R.string.annotation_feed_yesterday)
    var revision by remember { mutableIntStateOf(0) }

    DisposableEffect(context, lifecycle) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                revision++
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_TIME_CHANGED)
            addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
            addAction(android.content.Intent.ACTION_DATE_CHANGED)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) revision++
        }
        lifecycle.addObserver(lifecycleObserver)
        val timeFormatObserver = object : android.database.ContentObserver(
            android.os.Handler(android.os.Looper.getMainLooper())
        ) {
            override fun onChange(selfChange: Boolean) { revision++ }
        }
        context.contentResolver.registerContentObserver(
            android.provider.Settings.System.getUriFor(android.provider.Settings.System.TIME_12_24),
            false,
            timeFormatObserver
        )
        onDispose {
            context.unregisterReceiver(receiver)
            lifecycle.removeObserver(lifecycleObserver)
            context.contentResolver.unregisterContentObserver(timeFormatObserver)
        }
    }

    return remember(locale, todayLabel, yesterdayLabel, revision) {
        val zone = ZoneId.systemDefault()
        val timePattern =
            (android.text.format.DateFormat.getTimeFormat(context) as? SimpleDateFormat)?.toPattern()
                ?: if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        AnnotationFeedDisplayEnvironment(
            locale = locale,
            zone = zone,
            today = LocalDate.now(zone),
            timePattern = timePattern,
            dayPattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMMdEEE"),
            otherYearPattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMMdEEE"),
            todayLabel = todayLabel,
            yesterdayLabel = yesterdayLabel
        )
    }
}

@Composable
private fun FeedBookHeader(book: Book) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = book.coverPath,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp, 24.dp)
                .clip(AppRoundedCornerShape(3.dp))
                .background(AppColors.BgGray),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(AppSpace.sm))
        val secondary = AppColors.TextSecondary
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(book.title) }
                if (book.author.isNotBlank()) withStyle(SpanStyle(color = secondary)) { append(" · ${book.author}") }
            },
            fontSize = AppType.Caption,
            color = AppColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Rule highlights arrive in batches, so the feed hides them until asked. */
@Composable
private fun RuleHighlightToggle(count: Int, shown: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(AppRoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = AppSpace.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(
                if (shown) R.string.annotation_feed_rule_shown else R.string.annotation_feed_rule_hidden,
                count
            ),
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(if (shown) R.string.annotation_feed_rule_hide else R.string.annotation_feed_rule_show),
            fontSize = AppType.Caption,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.Accent
        )
    }
}

@Composable
private fun AnnotationSearchInput(text: String, onTextChange: (String) -> Unit, enabled: Boolean) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Keyed on enabled so reopening during the close animation focuses again.
    LaunchedEffect(enabled) { if (enabled) runCatching { focusRequester.requestFocus() } }
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .padding(start = AppSpace.md),
        singleLine = true,
        textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = AppType.BodySmall),
        cursorBrush = SolidColor(AppColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.annotation_feed_search_hint),
                        color = AppColors.TextSecondary,
                        fontSize = AppType.BodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                innerTextField()
            }
        }
    )
}

private val SearchButtonSize = 44.dp
