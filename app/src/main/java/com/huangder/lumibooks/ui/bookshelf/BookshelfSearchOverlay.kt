package com.huangder.lumibooks.ui.bookshelf

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.animation.HorizontalOverscrollBounce
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.util.FileUtils
import kotlinx.coroutines.delay

@Composable
internal fun BookshelfSearchLauncher(
    onClick: () -> Unit,
    onBoundsChanged: (Rect) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val shape = CircleShape
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    LiquidGlassSurface(
        shape = shape,
        fallbackColor = AppColors.CardBg,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.72f),
        onClick = onClick.takeIf { isLiquidGlass },
        interactive = isLiquidGlass,
        effectPadding = 1.dp,
        decorationModifier = Modifier.shadow(
            elevation = 16.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.10f),
            spotColor = Color.Black.copy(alpha = 0.13f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
            .then(
                if (isLiquidGlass) {
                    Modifier
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.library_search_placeholder),
                color = AppColors.TextSecondary,
                fontSize = AppType.BodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(R.string.search),
                tint = AppColors.TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
internal fun BookshelfSearchOverlay(
    visible: Boolean,
    query: String,
    books: List<Book>,
    tagNamesByBook: Map<String, List<String>>,
    expandedBookId: String?,
    deletingBookIds: Set<String>,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onExpandedBookChange: (String?) -> Unit,
    onBookClick: (Book) -> Unit,
    onEditInfo: (Book) -> Unit,
    onDelete: (Book) -> Unit,
    onFavorite: (Book) -> Unit,
    onCustomCover: (Book) -> Unit,
    onRemoveCustomCover: (Book) -> Unit,
    onTags: (Book) -> Unit,
    onBookmarksNotes: (Book) -> Unit,
    launcherBounds: Rect,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val positionDampingRatio = if (isLiquidGlass) 0.68f else 0.82f
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val backgroundInteraction = remember { MutableInteractionSource() }
    val results = remember(books, query, tagNamesByBook) {
        filterBookshelfSearchResults(books, query, tagNamesByBook)
    }
    var activeFieldCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val activeFieldBounds = activeFieldCoordinates
        ?.takeIf { it.isAttached }
        ?.boundsInRoot()
        ?: Rect.Zero
    val hasReturnTarget = launcherBounds != Rect.Zero && activeFieldBounds != Rect.Zero
    val returnOffsetX by animateFloatAsState(
        targetValue = if (!visible && hasReturnTarget) {
            launcherBounds.left - activeFieldBounds.left
        } else {
            0f
        },
        animationSpec = spring(dampingRatio = positionDampingRatio, stiffness = 220f),
        label = "bookshelfSearchReturnX"
    )
    val returnOffsetY by animateFloatAsState(
        targetValue = if (!visible && hasReturnTarget) {
            launcherBounds.top - activeFieldBounds.top
        } else {
            0f
        },
        animationSpec = spring(dampingRatio = positionDampingRatio, stiffness = 220f),
        label = "bookshelfSearchReturnY"
    )
    val activeFieldHeight by animateDpAsState(
        targetValue = if (visible) 62.dp else 48.dp,
        animationSpec = spring(
            dampingRatio = if (isLiquidGlass) 0.74f else 0.82f,
            stiffness = 240f
        ),
        label = "bookshelfSearchReturnHeight"
    )
    val resultContentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 160 else 110),
        label = "bookshelfSearchResultExit"
    )

    BackHandler(enabled = visible) {
        keyboardController?.hide()
        onDismiss()
    }
    LaunchedEffect(visible) {
        if (visible) {
            delay(140)
            focusRequester.requestFocus()
        } else {
            keyboardController?.hide()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + slideInVertically(
            initialOffsetY = { (it * 0.14f).toInt() },
            animationSpec = spring(
                dampingRatio = if (isLiquidGlass) 0.68f else 0.84f,
                stiffness = if (isLiquidGlass) 220f else 190f
            )
        ),
        exit = fadeOut(tween(durationMillis = 70, delayMillis = 430)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = backgroundInteraction
                ) {
                    if (isKeyboardVisible) {
                        keyboardController?.hide()
                    } else {
                        onDismiss()
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                ActiveBookshelfSearchField(
                    query = query,
                    onQueryChange = {
                        onQueryChange(it)
                        onExpandedBookChange(null)
                    },
                    focusRequester = focusRequester,
                    onSearch = { keyboardController?.hide() },
                    height = activeFieldHeight,
                    translationX = returnOffsetX,
                    translationY = returnOffsetY,
                    onPositioned = { activeFieldCoordinates = it },
                    modifier = Modifier.padding(
                        start = AppSpace.lg,
                        top = 18.dp,
                        end = AppSpace.lg
                    )
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = resultContentAlpha }
                ) {
                if (query.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.library_search_results),
                        color = AppColors.TextPrimary,
                        fontSize = AppType.BodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(
                            start = AppSpace.lg,
                            top = 28.dp,
                            bottom = 10.dp
                        )
                    )

                    if (results.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.search_no_results),
                                color = AppColors.TextSecondary,
                                fontSize = AppType.Body
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 14.dp,
                                end = 14.dp,
                                bottom = 128.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(results, key = { it.id }) { book ->
                                BookshelfSearchResultItem(
                                    book = book,
                                    tagNames = tagNamesByBook[book.id].orEmpty(),
                                    expanded = expandedBookId == book.id,
                                    isDeleting = book.id in deletingBookIds,
                                    onExpandedChange = {
                                        onExpandedBookChange(if (expandedBookId == book.id) null else book.id)
                                    },
                                    onClick = { onBookClick(book) },
                                    onEditInfo = { onEditInfo(book) },
                                    onDelete = { onDelete(book) },
                                    onFavorite = { onFavorite(book) },
                                    onCustomCover = { onCustomCover(book) },
                                    onRemoveCustomCover = { onRemoveCustomCover(book) },
                                    onTags = { onTags(book) },
                                    onBookmarksNotes = { onBookmarksNotes(book) }
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun ActiveBookshelfSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
    height: androidx.compose.ui.unit.Dp,
    translationX: Float,
    translationY: Float,
    onPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = CircleShape
    LiquidGlassSurface(
        shape = shape,
        fallbackColor = AppColors.CardBg,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.78f),
        effectPadding = 1.dp,
        decorationModifier = Modifier.shadow(
            elevation = 20.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.12f),
            spotColor = Color.Black.copy(alpha = 0.16f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onGloballyPositioned(onPositioned)
            .graphicsLayer {
                this.translationX = translationX
                this.translationY = translationY
            },
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .padding(horizontal = 20.dp),
            singleLine = true,
            textStyle = TextStyle(
                color = AppColors.TextPrimary,
                fontSize = AppType.Body,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(AppColors.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.library_search_placeholder),
                                color = AppColors.TextSecondary,
                                fontSize = AppType.Body,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        innerTextField()
                    }
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.search),
                        tint = AppColors.TextPrimary,
                        modifier = Modifier.size(27.dp)
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookshelfSearchResultItem(
    book: Book,
    tagNames: List<String>,
    expanded: Boolean,
    isDeleting: Boolean,
    onExpandedChange: () -> Unit,
    onClick: () -> Unit,
    onEditInfo: () -> Unit,
    onDelete: () -> Unit,
    onFavorite: () -> Unit,
    onCustomCover: () -> Unit,
    onRemoveCustomCover: () -> Unit,
    onTags: () -> Unit,
    onBookmarksNotes: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isDeleting) 0.92f else 1f,
        animationSpec = tween(280),
        label = "searchResultDeleteScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isDeleting) 0f else 1f,
        animationSpec = tween(280),
        label = "searchResultDeleteAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
    ) {
        BookshelfSearchResultCard(
            book = book,
            tagNames = tagNames,
            modifier = Modifier.combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onExpandedChange()
                }
            )
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 300f),
                clip = false
            ) + fadeIn(tween(130)),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(190),
                clip = false
            ) + fadeOut(tween(140))
        ) {
            Column {
                Spacer(Modifier.height(10.dp))
                SearchResultActionRow(
                    book = book,
                    visible = expanded,
                    onEditInfo = onEditInfo,
                    onDelete = onDelete,
                    onFavorite = onFavorite,
                    onCustomCover = onCustomCover,
                    onRemoveCustomCover = onRemoveCustomCover,
                    onTags = onTags,
                    onBookmarksNotes = onBookmarksNotes
                )
            }
        }
    }
}

@Composable
private fun BookshelfSearchResultCard(
    book: Book,
    tagNames: List<String>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val favoritePink = if (LocalIsDarkTheme.current) Color(0xFFFF8A80) else Color(0xFFE85D5D)
    val shape = RoundedCornerShape(if (isLiquidGlass) 24.dp else 16.dp)
    LiquidGlassSurface(
        shape = shape,
        fallbackColor = AppColors.CardBg,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.76f),
        interactive = false,
        effectPadding = 1.dp,
        decorationModifier = Modifier.shadow(
            elevation = 17.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.10f),
            spotColor = Color.Black.copy(alpha = 0.14f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(116.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(70.dp)
                    .height(92.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.BgGray),
                contentAlignment = Alignment.Center
            ) {
                if (book.coverPath != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(book.coverPath)
                            .memoryCacheKey("search_${book.id}_${book.coverPath}")
                            .build(),
                        contentDescription = book.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = book.title.take(6),
                        color = AppColors.TextSecondary,
                        fontSize = AppType.Caption,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = book.title,
                    color = AppColors.TextPrimary,
                    fontSize = AppType.Section,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = KaiTi,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = book.author,
                    color = AppColors.TextSecondary,
                    fontSize = AppType.BodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (book.isFavorite || tagNames.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                Column(
                    modifier = Modifier.widthIn(min = 58.dp, max = 86.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (book.isFavorite) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = stringResource(R.string.favorite),
                            tint = favoritePink,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    tagNames.take(2).forEachIndexed { index, tagName ->
                        if (book.isFavorite || index > 0) Spacer(Modifier.height(7.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(favoritePink)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = tagName,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultActionRow(
    book: Book,
    visible: Boolean,
    onEditInfo: () -> Unit,
    onDelete: () -> Unit,
    onFavorite: () -> Unit,
    onCustomCover: () -> Unit,
    onRemoveCustomCover: () -> Unit,
    onTags: () -> Unit,
    onBookmarksNotes: () -> Unit
) {
    val hasCustomCover = FileUtils.isCustomCover(book.coverPath)
    val favoritePink = if (LocalIsDarkTheme.current) Color(0xFFFF8A80) else Color(0xFFE85D5D)
    val gap = 4.dp
    HorizontalOverscrollBounce(modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchActionButton(
            icon = Icons.Outlined.Edit,
            contentDescription = stringResource(R.string.edit_book_info),
            visible = visible,
            animationIndex = 0,
            onClick = onEditInfo
        )
        SearchActionButton(
            icon = Icons.Outlined.Delete,
            contentDescription = stringResource(R.string.delete),
            visible = visible,
            animationIndex = 1,
            onClick = onDelete
        )
        SearchActionButton(
            icon = if (book.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(R.string.favorite),
            tintedColor = if (book.isFavorite) favoritePink else Color.Black,
            contentColor = Color.White,
            visible = visible,
            animationIndex = 2,
            onClick = onFavorite
        )

        if (hasCustomCover) {
            SearchActionButton(
                icon = Icons.Outlined.Image,
                contentDescription = stringResource(R.string.custom_cover),
                label = stringResource(R.string.custom_cover),
                width = 78.dp,
                visible = visible,
                animationIndex = 3,
                onClick = onCustomCover
            )
            SearchActionButton(
                icon = Icons.Outlined.Restore,
                contentDescription = stringResource(R.string.remove_custom_cover),
                label = stringResource(R.string.remove_custom_cover),
                width = 110.dp,
                visible = visible,
                animationIndex = 4,
                onClick = onRemoveCustomCover
            )
        } else {
            SearchActionButton(
                icon = Icons.Outlined.Image,
                contentDescription = stringResource(R.string.custom_cover),
                label = stringResource(R.string.custom_cover),
                width = 192.dp,
                visible = visible,
                animationIndex = 3,
                onClick = onCustomCover
            )
        }

        SearchActionButton(
            icon = Icons.AutoMirrored.Outlined.Label,
            contentDescription = stringResource(R.string.add_tag),
            visible = visible,
            animationIndex = if (hasCustomCover) 5 else 4,
            onClick = onTags
        )
        SearchActionButton(
            icon = Icons.Outlined.Bookmark,
            contentDescription = stringResource(R.string.bookmarks_notes),
            visible = visible,
            animationIndex = if (hasCustomCover) 6 else 5,
            onClick = onBookmarksNotes
        )
    }
    }
}

@Composable
private fun SearchActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    label: String? = null,
    width: androidx.compose.ui.unit.Dp = 36.dp,
    tintedColor: Color? = null,
    contentColor: Color = AppColors.TextPrimary,
    visible: Boolean,
    animationIndex: Int
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val isDark = LocalIsDarkTheme.current
    val shape = if (label == null) CircleShape else RoundedCornerShape(50)
    val fallback = tintedColor ?: AppColors.CardBg
    val scrim = tintedColor?.copy(alpha = 0.85f)
        ?: AppColors.CardBg.copy(alpha = if (isDark) 0.80f else 0.72f)
    val visibilityState = remember { MutableTransitionState(false) }
    LaunchedEffect(visible) {
        visibilityState.targetState = visible
    }
    AnimatedVisibility(
        visibleState = visibilityState,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 150,
                delayMillis = animationIndex * 28
            )
        ) + scaleIn(
            initialScale = 0.58f,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = 320f)
        ) + slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = spring(dampingRatio = 0.66f, stiffness = 300f)
        ),
        exit = fadeOut(tween(105)) + scaleOut(
            targetScale = 0.78f,
            animationSpec = tween(140)
        ) + slideOutVertically(
            targetOffsetY = { it / 4 },
            animationSpec = tween(140)
        )
    ) {
    LiquidGlassSurface(
        shape = shape,
        fallbackColor = fallback,
        contentScrimColor = scrim,
        onClick = onClick.takeIf { isLiquidGlass },
        interactive = isLiquidGlass,
        effectPadding = 1.dp,
        decorationModifier = Modifier.shadow(
            elevation = 12.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.10f),
            spotColor = Color.Black.copy(alpha = 0.13f)
        ),
        modifier = Modifier
            .width(width)
            .height(36.dp)
            .then(
                if (isLiquidGlass) {
                    Modifier
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
    ) {
        if (label == null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = label,
                    color = contentColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    }
}

internal fun filterBookshelfSearchResults(
    books: List<Book>,
    query: String,
    tagNamesByBook: Map<String, List<String>>
): List<Book> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return emptyList()
    return books.filter { book ->
        book.title.contains(normalizedQuery, ignoreCase = true) ||
            book.author.contains(normalizedQuery, ignoreCase = true) ||
            tagNamesByBook[book.id].orEmpty().any {
                it.contains(normalizedQuery, ignoreCase = true)
            }
    }
}
