package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.ui.components.LocalCollapsingPageTopInset
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
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import java.util.Date
import java.util.Locale

@Composable
internal fun AnnotationFeedScreen(
    onNavigateBack: () -> Unit,
    viewModel: AnnotationFeedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
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
                topPadding = LocalCollapsingPageTopInset.current,
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
    topPadding: Dp,
    modifier: Modifier = Modifier,
    onShare: (AnnotationFeedEntry) -> Unit,
    onEditNoteTags: (Note) -> Unit,
    onEditBookmarkTags: (Bookmark) -> Unit,
    onEditBookmarkRemark: (Bookmark) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit,
    onToggleRuleHighlights: () -> Unit
) {
    // Room answers quickly; showing nothing first avoids flashing the empty message.
    if (uiState.isLoading) return
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    val timeFormat = remember(locale) { android.text.format.DateFormat.getTimeFormat(context) }
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val groups = remember(uiState.entries, zone) { uiState.entries.groupedByDay(zone) }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = AppSpace.lg,
            top = topPadding + AppSpace.sm,
            end = AppSpace.lg,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + AppSpace.xl
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
    ) {
        if (uiState.ruleHighlightCount > 0) {
            item(key = "rule_toggle") {
                RuleHighlightToggle(
                    count = uiState.ruleHighlightCount,
                    shown = uiState.includeRuleHighlights,
                    onToggle = onToggleRuleHighlights
                )
            }
        }
        if (groups.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.no_content),
                    fontSize = AppType.Body,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 96.dp)
                )
            }
        }
        groups.forEach { (day, entries) ->
            item(key = "day-$day") { DayHeader(day = day, today = today, locale = locale) }
            items(entries, key = { it.key }) { entry ->
                val time = timeFormat.format(Date(entry.createdAt))
                when (entry) {
                    is AnnotationFeedEntry.NoteEntry -> HighlightNoteItem(
                        note = entry.note,
                        onShare = { onShare(entry) },
                        onEditTags = { onEditNoteTags(entry.note) },
                        onDelete = { onDeleteNote(entry.note) },
                        header = { FeedBookHeader(entry.book) },
                        dateText = time,
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
                        dateText = time,
                        cornerRadius = AppRadius.lg
                    )
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
                        Icon(AppIcons.X, stringResource(R.string.close), tint = AppColors.TextPrimary,
                            modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate, today: LocalDate, locale: Locale) {
    val label = when (day) {
        today -> stringResource(R.string.annotation_feed_today)
        today.minusDays(1) -> stringResource(R.string.annotation_feed_yesterday)
        else -> {
            val skeleton = if (day.year == today.year) "MMMMdEEE" else "yMMMMdEEE"
            val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
            SimpleDateFormat(pattern, locale).format(Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant()))
        }
    }
    Text(
        text = label,
        fontSize = AppType.Caption,
        fontWeight = FontWeight.SemiBold,
        color = AppColors.TextSecondary,
        modifier = Modifier.padding(top = AppSpace.sm, start = AppSpace.xs)
    )
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
