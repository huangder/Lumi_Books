package com.huangder.lumibooks.ui.bookshelf.spine

import android.view.HapticFeedbackConstants
import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.layout.onSizeChanged
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalReduceTransparency
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.LibraryFolder
import com.huangder.lumibooks.ui.bookshelf.CoverFlowBookActions
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.util.FileUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Immutable
internal data class SpineShelfParams(
    val books: List<Book>,
    val folders: List<LibraryFolder>,
    val folderBookCounts: Map<String, Int>,
    val isLoading: Boolean,
    val isEditing: Boolean,
    val selectedBookIds: Set<String>,
    val deletingBookIds: Set<String>,
    val topPadding: Dp,
    val bottomPadding: Dp,
    val enabled: Boolean,
    /** The folder on show; each folder drops its books the first time it opens in a process. */
    val sceneKey: String,
    /** The shelf's own book menu ends in the same operations as Cover Flow's. */
    val actions: CoverFlowBookActions,
    val onFolderClick: (LibraryFolder) -> Unit,
    val onSelectionToggle: (Book) -> Unit,
    val onHaptic: () -> Unit,
    /** True while the book menu is up, so the tab bar and tab swipes stay out of the way. */
    val onMenuVisibleChange: (Boolean) -> Unit
)

private const val FOLDER_PREFIX = "folder:"
private const val SETTLED = 1_000_000f
private const val DROP_TOTAL_MS = 2600
private const val MENU_OPEN_MS = 440
private const val MENU_CLOSE_MS = 360
private const val TICK_GAP_MS = 55L

/** Children own the whole horizontal gesture, so the main tabs never page from the shelf's edge. */
private val HorizontalGestureGuard = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) =
        Offset(available.x, 0f)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = Velocity(available.x, 0f)
}

@Composable
internal fun SpineBookshelf(params: SpineShelfParams, modifier: Modifier = Modifier) {
    // A folder is its own shelf: drop, scroll position and menu all start fresh inside it.
    key(params.sceneKey) { SpineScene(params, modifier) }
}

/** The book whose menu is up: pulled out of the shelf, turned to its cover and parked. */
@Stable
private class SpineMenu {
    var book by mutableStateOf<Book?>(null)
    var spine = Rect.Zero
    var spineAngle = 0f
    var flat = false
    var card by mutableStateOf(Rect.Zero)
    val progress = Animatable(0f)
    var closing by mutableStateOf(false)

    val busy: Boolean get() = book != null
}

private class MenuStages(val stand: Float, val pull: Float, val grow: Float, val panel: Float, val scrim: Float)

private fun menuStages(p: Float): MenuStages {
    val pull = slice(p, 0f, 0.30f)
    return MenuStages(stand = pull, pull = pull, grow = slice(p, 0.15f, 1f),
        panel = slice(p, 0.55f, 1f), scrim = slice(p, 0f, 0.6f))
}

private fun slice(p: Float, from: Float, to: Float): Float =
    FastOutSlowInEasing.transform(((p - from) / (to - from)).coerceIn(0f, 1f))

@Composable
private fun SpineScene(params: SpineShelfParams, modifier: Modifier) {
    val density = LocalDensity.current
    val view = LocalView.current
    val eInk = LocalEInkMode.current
    val motion = LocalMotionEnabled.current && !eInk
    val scope = rememberCoroutineScope()
    val scene = params.sceneKey
    val books = params.books
    val bookById = remember(books) { books.associateBy { it.id } }
    val folderById = remember(params.folders) { params.folders.associateBy { FOLDER_PREFIX + it.id } }
    val entries = remember(params.folders, books) {
        params.folders.map { SpineEntry(FOLDER_PREFIX + it.id, isFolder = true) } + books.map { SpineEntry(it.id) }
    }
    val origin = remember { arrayOf(Offset.Zero) }
    val bayOrigins = remember { HashMap<Int, Offset>() }
    val menu = remember { SpineMenu() }
    val panelHeight = remember { mutableIntStateOf(0) }
    val glass = LocalAppTheme.current == "liquid_glass" && !eInk
    val blurAllowed = !eInk && !LocalReduceTransparency.current && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val menuBackdrop = rememberLayerBackdrop()
    val blurSteps = remember { BlurSteps() }
    val dropClock = remember { Animatable(if (SpineShelfSession.dropPlayed(scene) || !motion) SETTLED else 0f) }
    val dark = AppColors.PageBg.luminance() < 0.4f
    val accent = AppColors.Accent

    fun closeMenu(then: (() -> Unit)? = null) {
        if (!menu.busy || menu.closing) return
        menu.closing = true
        scope.launch {
            try {
                then?.invoke()
                if (motion) menu.progress.animateTo(0f, tween(MENU_CLOSE_MS, easing = LinearEasing))
            } finally {
                menu.book = null
                menu.closing = false
                params.onMenuVisibleChange(false)
            }
        }
    }
    BackHandler(enabled = menu.busy) { closeMenu() }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin[0] = it.positionInRoot() }
    ) {
        val bounds = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val edge = 14.dp
        // Bays meet edge to edge; full rows are spread to the edge, so keep only a hairline inset.
        val inner = 4.dp
        val bayWidth = maxWidth - edge * 2
        val shelfWidth = bayWidth - inner * 2
        val top = params.topPadding
        val available = (maxHeight - top - params.bottomPadding).coerceAtLeast(240.dp)
        val rows = SpineLayout.rowCount(available.value)
        val rowHeight = available / rows
        val board = 10.dp
        val clearance = 16.dp
        val maxBookHeight = rowHeight - board - clearance
        val pages = remember(entries, shelfWidth, maxBookHeight, rows) {
            SpineLayout.paginate(entries, SpineShelfSession.seed, shelfWidth.value, maxBookHeight.value, rows)
        }
        val placedById = remember(pages) {
            pages.flatMap { page -> page.rows.flatMap { it.items } }.associateBy { it.id }
        }
        val bayOfId = remember(pages) {
            buildMap { pages.forEachIndexed { bay, page -> page.rows.forEach { row -> row.items.forEach { put(it.id, bay) } } } }
        }
        val rowOfId = remember(pages) {
            buildMap { pages.forEach { page -> page.rows.forEachIndexed { r, row -> row.items.forEach { put(it.id, r) } } } }
        }
        val saved = remember { SpineShelfSession.scrollOf(scene) }
        val listState = rememberLazyListState(saved.first.coerceIn(0, pages.lastIndex), saved.second)
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (index, offset) -> SpineShelfSession.saveScroll(scene, index, offset) }
        }

        fun rowTopPx(row: Int): Float = with(density) { (top + rowHeight * row).toPx() }

        // First visit to this shelf in this process: the books fall once they have loaded, with a
        // light tick as they land (one per short interval, so a busy shelf does not buzz).
        LaunchedEffect(params.isLoading, entries.isEmpty()) {
            if (SpineShelfSession.dropPlayed(scene) || params.isLoading || entries.isEmpty()) return@LaunchedEffect
            SpineShelfSession.markDropPlayed(scene)
            if (!motion) {
                dropClock.snapTo(SETTLED)
                return@LaunchedEffect
            }
            val firstBay = listState.firstVisibleItemIndex
            val landings = pages.drop(firstBay).take(2).flatMap { page ->
                page.rows.flatMapIndexed { row, shelf -> shelf.items.map { landingTime(it, row) } }
            }.sorted()
            dropClock.snapTo(0f)
            launch {
                var next = 0
                var lastTick = 0L
                while (next < landings.size) {
                    withFrameNanos { }
                    val clock = dropClock.value
                    if (clock >= SETTLED) break
                    var landed = false
                    while (next < landings.size && landings[next] <= clock) {
                        next++
                        landed = true
                    }
                    val now = System.currentTimeMillis()
                    if (landed && now - lastTick >= TICK_GAP_MS) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        lastTick = now
                    }
                }
            }
            dropClock.animateTo(DROP_TOTAL_MS.toFloat(), tween(DROP_TOTAL_MS, easing = LinearEasing))
            dropClock.snapTo(SETTLED)
        }

        fun spineRect(id: String): Rect? {
            val placed = placedById[id] ?: return null
            val bay = bayOfId[id] ?: return null
            val row = rowOfId[id] ?: return null
            val bayOrigin = bayOrigins[bay] ?: return null
            val px = density.density
            val left = bayOrigin.x + (inner.value + placed.x) * px
            val bottom = rowTopPx(row) + with(density) { (rowHeight - board).toPx() } - placed.lift * px
            return Rect(left, bottom - placed.height * px, left + placed.width * px, bottom)
        }

        // The menu stands on the bottom safe edge (the tab bar hides while it is up); the
        // cover is sized and centred in whatever space is left above it.
        val navInsetPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        fun bottomSafePx(): Float {
            val below = (view.height - (origin[0].y + bounds.height)).coerceAtLeast(0f)
            return (navInsetPx - below).coerceAtLeast(0f) + with(density) { 16.dp.toPx() }
        }
        fun cardRect(): Rect {
            val panel = panelHeight.intValue.takeIf { it > 0 }?.toFloat() ?: with(density) { 430.dp.toPx() }
            val areaTop = with(density) { (top + 12.dp).toPx() }
            val areaBottom = bounds.height - bottomSafePx() - panel - with(density) { 20.dp.toPx() }
            val area = (areaBottom - areaTop).coerceAtLeast(with(density) { 120.dp.toPx() })
            var width = min(bounds.width * 0.42f, with(density) { 200.dp.toPx() })
            var height = width / 0.68f
            if (height > area) {
                height = area
                width = height * 0.68f
            }
            val left = (bounds.width - width) / 2f
            val cardTop = areaTop + (area - height) / 2f
            return Rect(left, cardTop, left + width, cardTop + height)
        }
        // The first measured menu height corrects the estimate before the book starts to move.
        LaunchedEffect(panelHeight.intValue) {
            if (menu.busy && !menu.closing) menu.card = cardRect()
        }

        val onLongPress: (Book) -> Unit = longPress@{ book ->
            if (params.isEditing || !params.enabled || menu.busy) return@longPress
            val spine = spineRect(book.id) ?: return@longPress
            val placed = placedById[book.id]
            params.onHaptic()
            menu.book = book
            menu.spine = spine
            menu.spineAngle = placed?.angle ?: 0f
            menu.flat = placed?.pose == SpinePose.Flat
            menu.card = cardRect()
            menu.closing = false
            params.onMenuVisibleChange(true)
            scope.launch {
                menu.progress.snapTo(if (motion) 0f else 1f)
                if (motion) menu.progress.animateTo(1f, tween(MENU_OPEN_MS, easing = LinearEasing))
            }
        }

        val onClick: (Book) -> Unit = click@{ book ->
            if (params.isEditing) {
                params.onSelectionToggle(book)
                return@click
            }
            if (!params.enabled || menu.busy) return@click
            params.actions.open(book, spineRect(book.id)?.translate(origin[0]))
        }

        // The shelf blurs behind the menu. The blurred result is recorded as a sibling backdrop
        // for the glass panel (outside the blur layer, so the glass samples it already soft and
        // then blurs further); sampling the outer bookshelf backdrop from inside would be cyclic.
        Box(
            Modifier
                .fillMaxSize()
                .then(if (glass) Modifier.layerBackdrop(menuBackdrop) else Modifier)
                .graphicsLayer {
                    val dim = if (menu.busy) menuStages(menu.progress.value).scrim else 0f
                    scaleX = 1f - 0.04f * dim
                    scaleY = 1f - 0.04f * dim
                    renderEffect = if (blurAllowed && dim > 0f) blurSteps.at(dim, this.density) else null
                }
        ) {
        LazyRow(
            state = listState,
            userScrollEnabled = params.enabled && !menu.busy,
            contentPadding = PaddingValues(horizontal = edge),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(HorizontalGestureGuard)
        ) {
            items(pages.size, key = { it }) { bayIndex ->
                val page = pages[bayIndex]
                Box(
                    Modifier
                        .width(bayWidth)
                        .fillMaxHeight()
                        .onGloballyPositioned { bayOrigins[bayIndex] = it.positionInRoot() - origin[0] }
                ) {
                    BayFrame(top = top, rowHeight = rowHeight, rows = page.rows.size, board = board,
                        dark = dark, eInk = eInk, leftEnd = bayIndex == 0, rightEnd = bayIndex == pages.lastIndex)
                    page.rows.forEachIndexed { rowIndex, row ->
                        val rowTop = top + rowHeight * rowIndex
                        val rowTopPx = with(density) { rowTop.toPx() }
                        row.items.forEach { item ->
                            key(item.id) {
                                ShelfItem(
                                    item = item,
                                    book = if (item.isFolder) null else bookById[item.id],
                                    folder = if (item.isFolder) folderById[item.id] else null,
                                    x = inner + item.x.dp,
                                    y = rowTop + rowHeight - board - item.lift.dp - item.height.dp,
                                    rowIndex = rowIndex,
                                    rowTopPx = rowTopPx,
                                    params = params,
                                    accent = accent,
                                    dropClock = dropClock,
                                    hidden = { menu.book?.id == item.id },
                                    onClick = onClick,
                                    onLongPress = onLongPress
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        menu.book?.let { book ->
            // Scrim over the shelf; a tap anywhere outside the book and its menu puts it back.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = menuStages(menu.progress.value).scrim }
                    .background(AppColors.PageBg.copy(alpha = if (blurAllowed) 0.30f else 0.80f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        closeMenu()
                    }
            )
            LiftedBook(book = book, menu = menu, bounds = bounds)
            SpineBookMenu(
                // The live copy, so a favourite toggled here shows at once.
                book = bookById[book.id] ?: book,
                actions = params.actions,
                stage = { menuStages(menu.progress.value).panel },
                maxWidth = maxWidth,
                backdrop = menuBackdrop.takeIf { glass },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = with(density) { bottomSafePx().toDp() })
                    .onSizeChanged { panelHeight.intValue = it.height },
                onOpen = {
                    val cover = menu.card.translate(origin[0])
                    menu.book = null
                    params.onMenuVisibleChange(false)
                    params.actions.open(book, cover)
                },
                // Details covers this Activity and returns to the same selected book; other
                // page and sheet actions dismiss the menu before they run.
                onAction = { action ->
                    if (!action.keepMenuOpen) {
                        menu.book = null
                        params.onMenuVisibleChange(false)
                    }
                    action.run(book)
                }
            )
        }
    }
}

private fun landingTime(item: PlacedSpine, row: Int): Float =
    (min(item.order * 26, 720) + row * 80 + item.traits.dropJitterMs).toFloat() + 360f + row * 25f

@Composable
private fun ShelfItem(
    item: PlacedSpine,
    book: Book?,
    folder: LibraryFolder?,
    x: Dp,
    y: Dp,
    rowIndex: Int,
    rowTopPx: Float,
    params: SpineShelfParams,
    accent: Color,
    dropClock: Animatable<Float, *>,
    hidden: () -> Boolean,
    onClick: (Book) -> Unit,
    onLongPress: (Book) -> Unit
) {
    if (book == null && folder == null) return
    val deleting = book != null && book.id in params.deletingBookIds
    val selected = book != null && params.isEditing && book.id in params.selectedBookIds
    val gone by animateFloatAsState(if (deleting) 0f else 1f, tween(260), label = "spine-delete")
    val raised by animateFloatAsState(if (selected) 1f else 0f, tween(160), label = "spine-select")
    val label = book?.title ?: folder?.name.orEmpty()
    val width = item.width.dp
    val height = item.height.dp
    Box(
        Modifier
            .offset(x = x, y = y)
            .size(width, height)
            .graphicsLayer {
                dropTransform(item, rowIndex, rowTopPx, dropClock.value)
                translationY -= 6.dp.toPx() * raised
                alpha *= gone * if (hidden()) 0f else 1f
            }
            .semantics { contentDescription = label }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    when {
                        book != null -> onClick(book)
                        folder != null && !params.isEditing && params.enabled -> params.onFolderClick(folder)
                    }
                },
                onLongClick = { if (book != null) onLongPress(book) }
            )
            .then(if (selected) Modifier.border(2.dp, accent, RoundedCornerShape(3.dp)) else Modifier)
    ) {
        when {
            book != null -> BookSpineFace(book, horizontal = item.pose == SpinePose.Flat, width = width,
                height = height, modifier = Modifier.fillMaxSize())
            folder != null -> FolderSpineFace(
                folder = folder,
                countLabel = stringResource(R.string.spine_shelf_folder_count, params.folderBookCounts[folder.id] ?: 0),
                width = width,
                height = height,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * The lifted book as a box: a spine plane and a cover plane hinged on their shared edge. Turning
 * the box from spine-forward (−90°) to cover-forward (0°) while it rises to its place reads as
 * taking the book off the shelf and turning it over in the hand.
 */
@Composable
private fun LiftedBook(book: Book, menu: SpineMenu, bounds: Size) {
    val density = LocalDensity.current
    val spine = menu.spine
    val card = menu.card
    if (spine.width <= 0f || card.width <= 0f || bounds.width <= 0f) return
    val pullPx = with(density) { 18.dp.toPx() }
    val flat = menu.flat
    // A flat book is stood up first; its box starts from the standing rectangle.
    val base = if (flat) Rect(spine.center.x - spine.height / 2f, spine.center.y - spine.width / 2f,
        spine.center.x + spine.height / 2f, spine.center.y + spine.width / 2f) else spine
    val startAngle = if (flat) -90f else menu.spineAngle
    // Leaning books rest on their bottom-left corner (see SpineLayout).
    val pivot = if (flat) spine.center else Offset(spine.left, spine.bottom)
    val baseSize = with(density) { DpSize(base.width.toDp(), base.height.toDp()) }
    val cardSize = with(density) { DpSize(card.width.toDp(), card.height.toDp()) }
    val camera = 12f

    class Pose(val hinge: Float, val top: Float, val height: Float, val theta: Float)
    fun pose(): Pose {
        val stage = menuStages(menu.progress.value)
        val startTop = base.top - pullPx * stage.pull
        return Pose(
            hinge = lerp(base.right, card.left, stage.grow),
            top = lerp(startTop, card.top, stage.grow),
            height = lerp(base.height, card.height, stage.grow),
            theta = -90f + 90f * stage.grow
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val stage = menuStages(menu.progress.value)
                transformOrigin = TransformOrigin(pivot.x / bounds.width, pivot.y / bounds.height)
                rotationZ = startAngle * (1f - stage.stand)
            }
    ) {
        // Spine plane: its right edge is the hinge.
        Box(
            Modifier
                .size(baseSize)
                .graphicsLayer {
                    val p = pose()
                    hinged(base.height, p.height, pivotRight = true)
                    translationX = p.hinge - base.width
                    translationY = p.top + p.height / 2f - base.height / 2f
                    rotationY = p.theta + 90f
                    cameraDistance = camera * this.density
                    alpha = if (rotationY < 89.5f) 1f else 0f
                }
        ) {
            BookSpineFace(book, horizontal = false, width = baseSize.width, height = baseSize.height,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = if (flat) menuStages(menu.progress.value).stand else 1f
                })
            if (flat) {
                // Lying on the shelf the title runs across; it hands over as the book stands up.
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .requiredSize(width = baseSize.height, height = baseSize.width)
                        .rotate(90f)
                        .graphicsLayer { alpha = 1f - menuStages(menu.progress.value).stand }
                ) {
                    BookSpineFace(book, horizontal = true, width = baseSize.height, height = baseSize.width,
                        modifier = Modifier.fillMaxSize())
                }
            }
        }

        // Cover plane: its left edge is the hinge.
        Box(
            Modifier
                .size(cardSize)
                .graphicsLayer {
                    val p = pose()
                    hinged(card.height, p.height, pivotRight = false)
                    translationX = p.hinge
                    translationY = p.top + p.height / 2f - card.height / 2f
                    rotationY = p.theta
                    cameraDistance = camera * this.density
                    alpha = if (rotationY > -89.5f) 1f else 0f
                    shadowElevation = 14.dp.toPx() * ((p.theta + 90f) / 90f)
                    shape = RoundedCornerShape(3.dp)
                }
        ) {
            BookCoverFace(book, modifier = Modifier.fillMaxSize())
        }
    }
}

private class MenuAction(val icon: ImageVector, val label: String, val destructive: Boolean = false,
    val keepMenuOpen: Boolean = false,
    val run: (Book) -> Unit)

/**
 * Under the parked cover: title, author, a primary read button with the progress in it, and a
 * calm grid of round actions. Favourite stays on screen (it only flips the heart).
 */
@Composable
private fun SpineBookMenu(
    book: Book,
    actions: CoverFlowBookActions,
    stage: () -> Float,
    maxWidth: Dp,
    backdrop: Backdrop?,
    modifier: Modifier,
    onOpen: () -> Unit,
    onAction: (MenuAction) -> Unit
) {
    val density = LocalDensity.current
    val items = buildList {
        add(MenuAction(AppIcons.Info, stringResource(R.string.coverflow_info), keepMenuOpen = true,
            run = actions.details))
        add(MenuAction(AppIcons.Heart.resolve(book.isFavorite), stringResource(R.string.favorite),
            keepMenuOpen = true, run = actions.favorite))
        add(MenuAction(AppIcons.Bookmark.regular, stringResource(R.string.spine_menu_notes), run = actions.notes))
        add(MenuAction(AppIcons.Tag, stringResource(R.string.spine_menu_tags), run = actions.tags))
        add(MenuAction(AppIcons.PencilSimple, stringResource(R.string.spine_menu_edit), run = actions.editInfo))
        add(MenuAction(AppIcons.Image, stringResource(R.string.spine_menu_cover), run = actions.customCover))
        if (FileUtils.isCustomCover(book.coverPath)) {
            add(MenuAction(AppIcons.ArrowCounterClockwise, stringResource(R.string.spine_menu_restore_cover),
                run = actions.removeCustomCover))
        }
        add(MenuAction(AppIcons.FolderSimple, stringResource(R.string.spine_menu_move), run = actions.move))
        actions.hide?.let { hide ->
            add(MenuAction(AppIcons.EyeSlash, stringResource(R.string.spine_menu_hide), run = hide))
        }
        actions.archive?.let { archive ->
            add(MenuAction(AppIcons.Archive, stringResource(R.string.spine_menu_archive), run = archive))
        }
        add(MenuAction(AppIcons.Trash, stringResource(R.string.delete), destructive = true, run = actions.delete))
    }
    val progress = book.readingProgress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val percent = (progress * 100).roundToInt()
    val readLabel = when {
        percent >= 1 -> "${stringResource(R.string.book_detail_continue)} · $percent%"
        progress > 0f -> stringResource(R.string.book_detail_continue)
        else -> stringResource(R.string.book_detail_start)
    }
    // Same clean-up as the spine: no edition notes or download-site tags in the heading.
    val heading = remember(book.title) { SpineText.mainTitle(book.title) }
    val panelWidth = minOf(maxWidth - 40.dp, 380.dp)
    val accent = AppColors.Accent
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = stage()
                alpha = p
                translationY = (1f - p) * 20.dp.toPx()
            }
    ) {
        Text(
            text = heading,
            color = AppColors.TextPrimary,
            fontFamily = spineFontFor(book.title),
            fontWeight = FontWeight.SemiBold,
            fontSize = 19.sp,
            lineHeight = 25.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(panelWidth)
        )
        if (book.author.isNotBlank()) {
            Text(
                text = book.author,
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp).width(panelWidth)
            )
        }
        // Primary action: a pill that fills with the reading progress.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 18.dp)
                .width(panelWidth.coerceAtMost(260.dp))
                .height(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(accent.copy(alpha = 0.16f))
                .clickable(onClick = onOpen)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(if (progress > 0f) progress.coerceAtLeast(0.08f) else 1f)
                    .background(if (progress > 0f) accent.copy(alpha = 0.22f) else accent)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ink = if (progress > 0f) accent else Color.White
                Icon(AppIcons.BookOpen, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
                Text(readLabel, color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp))
            }
        }
        LiquidGlassSurface(
            shape = AppRoundedCornerShape(28.dp),
            fallbackColor = AppColors.CardBg,
            backdrop = backdrop,
            forceFallback = backdrop == null,
            contentScrimColor = AppColors.CardBg.copy(alpha = 0.52f),
            controlEdge = true,
            interactive = false,
            blurBoost = 10.dp,
            modifier = Modifier.padding(top = 22.dp).width(panelWidth)
        ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(horizontal = 8.dp, vertical = 14.dp)
        ) {
            items.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { action ->
                        val tint = if (action.destructive) AppColors.Destructive else AppColors.TextPrimary
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onAction(action) }
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(tint.copy(alpha = if (action.destructive) 0.10f else 0.06f))
                            ) {
                                Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
                            }
                            Text(
                                text = action.label,
                                color = if (action.destructive) tint else AppColors.TextSecondary,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
                            )
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        }
    }
}

/** Quantised blur effects, reused in both directions instead of allocating one per frame. */
private class BlurSteps {
    private val effects = arrayOfNulls<androidx.compose.ui.graphics.RenderEffect>(25)

    fun at(fraction: Float, density: Float): androidx.compose.ui.graphics.RenderEffect? {
        val step = (fraction * 24f).roundToInt().coerceIn(0, 24)
        if (step == 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return effects[step] ?: android.graphics.RenderEffect.createBlurEffect(
            step * 0.75f * density, step * 0.75f * density, android.graphics.Shader.TileMode.CLAMP
        ).asComposeRenderEffect().also { effects[step] = it }
    }
}

/** Uniform scale about the plane's hinge edge (left, or right for the spine). */
private fun GraphicsLayerScope.hinged(baseHeight: Float, height: Float, pivotRight: Boolean) {
    transformOrigin = TransformOrigin(if (pivotRight) 1f else 0f, 0.5f)
    val scale = if (baseHeight > 0f) height / baseHeight else 1f
    scaleX = scale
    scaleY = scale
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

/**
 * Fall from above the shelf with gravity, squash slightly on landing, and for leaning books tip
 * over onto the neighbour afterwards. A settled clock leaves every book at rest.
 */
private fun GraphicsLayerScope.dropTransform(item: PlacedSpine, row: Int, rowTopPx: Float, clock: Float) {
    val leaning = item.pose == SpinePose.Leaning
    transformOrigin = if (leaning) TransformOrigin(0f, 1f) else TransformOrigin(0.5f, 1f)
    if (clock >= SETTLED) {
        rotationZ = item.angle
        return
    }
    val delay = (min(item.order * 26, 720) + row * 80 + item.traits.dropJitterMs).toFloat()
    val fall = 360f + row * 25f
    val t = clock - delay
    val heightPx = item.height * density
    val start = -(rowTopPx + heightPx + 80f * density)
    if (t < fall) {
        val u = (t / fall).coerceIn(0f, 1f)
        translationY = start * (1f - u * u)
        rotationZ = item.traits.dropTilt * (1f - u)
        alpha = if (t < 0f) 0f else 1f
        return
    }
    val v = ((t - fall) / 220f).coerceIn(0f, 1f)
    val bounce = sin(PI.toFloat() * v) * (1f - v)
    scaleY = 1f - 0.07f * bounce
    scaleX = 1f + 0.035f * bounce
    rotationZ = if (leaning) item.angle * easeOutBack(((t - fall - 80f) / 260f).coerceIn(0f, 1f)) else 0f
}

private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val p = x - 1f
    return 1f + c3 * p * p * p + c1 * p * p
}

/**
 * One bay of the bookcase. Boards run edge to edge into the next bay, so the shelf reads as one
 * long case; only its two outer ends get an upright.
 */
@Composable
private fun BayFrame(top: Dp, rowHeight: Dp, rows: Int, board: Dp, dark: Boolean, eInk: Boolean,
    leftEnd: Boolean, rightEnd: Boolean) {
    val woodTop = if (eInk) Color.Black else if (dark) Color(0xFF6B4A33) else Color(0xFFC89B6D)
    val woodBottom = if (eInk) Color.Black else if (dark) Color(0xFF452E1F) else Color(0xFF94653F)
    val upright = if (eInk) Color.Black else if (dark) Color(0xFF55392A) else Color(0xFFB0835A)
    val frameTop = top - 6.dp
    val frameHeight = rowHeight * rows + 6.dp
    val boardBrush = remember(woodTop, woodBottom) { Brush.verticalGradient(listOf(woodTop, woodBottom)) }
    val shadowBrush = remember { Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.16f), Color.Transparent)) }
    Box(Modifier.fillMaxSize()) {
        repeat(rows) { row ->
            val y = top + rowHeight * (row + 1) - board
            Box(Modifier.offset(y = y).fillMaxWidth().height(board).background(boardBrush))
            if (!eInk) {
                Box(
                    Modifier.offset(y = y).fillMaxWidth().height(1.dp)
                        .background(Color.White.copy(alpha = if (dark) 0.10f else 0.28f))
                )
                Box(Modifier.offset(y = y + board).fillMaxWidth().height(8.dp).background(shadowBrush))
            }
        }
        if (leftEnd) Box(Modifier.offset(x = (-6).dp, y = frameTop).width(6.dp).height(frameHeight).background(upright))
        if (rightEnd) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = frameTop).width(6.dp).height(frameHeight).background(upright))
        }
    }
}
