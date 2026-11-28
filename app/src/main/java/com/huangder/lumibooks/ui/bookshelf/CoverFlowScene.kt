package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.ui.theme.LocalReduceTransparency
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.sync.BookDownloadState
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.ui.animation.BookCoverTitleColor
import com.huangder.lumibooks.ui.animation.BookCoverTitleStyle
import com.huangder.lumibooks.ui.animation.BookReaderPresentation
import com.huangder.lumibooks.ui.animation.CoverFlowReaderMotion
import com.huangder.lumibooks.ui.animation.CoverFlowEntranceMotion
import com.huangder.lumibooks.ui.animation.LocalCoverFlowEntrance
import com.huangder.lumibooks.ui.animation.LocalBookReaderAnchorScope
import com.huangder.lumibooks.ui.animation.bookCoverMemoryCacheKey
import com.huangder.lumibooks.ui.animation.bookCoverTransitionAnchor
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.AppType
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

internal data class CoverFlowHero(val bounds: Rect, val rotation: Float, val camera: Float)

/** Live coordinate references, not observable per-frame bounding rectangles. */
internal class CoverFlowSceneLayout {
    var coordinates: LayoutCoordinates? = null
    var geometry = CoverFlowGeometry(1f, 1f)
    var direction = 1f
    var centerOffsetY = 0f
    val covers = mutableMapOf<String, LayoutCoordinates>()

    fun hero(index: Int, position: Float): CoverFlowHero? {
        val root = coordinates?.takeIf { it.isAttached } ?: return null
        val center = root.localToRoot(Offset(root.size.width / 2f, root.size.height / 2f + centerOffsetY))
        val pose = geometry.transform((index - position) * direction)
        val w = geometry.coverWidth * pose.scale
        val h = geometry.coverHeight * pose.scale
        return CoverFlowHero(Rect(center.x + pose.x - w / 2f, center.y - h / 2f,
            center.x + pose.x + w / 2f, center.y + h / 2f), pose.rotationY, pose.cameraDistance)
    }

    fun hit(point: Offset, books: List<Book>, position: Float, menuBookId: String?): Int? {
        val stage = coordinates?.takeIf { it.isAttached } ?: return null
        return CoverFlowPhysics.visibleRange(position, books.size)
            .sortedBy { abs(it - position) }
            .firstOrNull { index ->
                if (menuBookId != null && books[index].id != menuBookId) return@firstOrNull false
                geometry.contains((index - position) * direction,
                    point.x - stage.size.width / 2f, point.y - stage.size.height / 2f - centerOffsetY)
            }
    }

    fun coverBounds(id: String): Rect? = covers[id]?.takeIf { it.isAttached }?.boundsInRoot()
}

@Composable
internal fun CoverFlowScene(
    books: List<Book>,
    state: CoverFlowState,
    layout: CoverFlowSceneLayout,
    motionEnabled: Boolean,
    enabled: Boolean,
    hiddenHeroId: String?,
    downloadStates: Map<String, BookDownloadState>,
    deletingIds: Set<String>,
    onOpen: (Book, Rect?) -> Unit,
    onHaptic: () -> Unit,
    modifier: Modifier = Modifier,
    sceneAlpha: () -> Float = { 1f },
    menusEnabled: Boolean = true,
    lightColors: List<Color> = emptyList(),
    /** Commits a drag on the stage with the new order of [books]' ids; null turns dragging off. */
    onReorder: ((List<String>) -> Unit)? = null,
    /** Folder stand-ins in [books] (see [COVER_FLOW_FOLDER_PREFIX]) and what their cards show. */
    folderCards: Map<String, CoverFlowFolderCardInfo> = emptyMap()
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val reduceTransparency = LocalReduceTransparency.current
    val eInk = LocalEInkMode.current
    val entrance = LocalCoverFlowEntrance.current
    val coversLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val readerTransition = LocalBookReaderAnchorScope.current?.transitionState
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val currentOpen by rememberUpdatedState(onOpen)
    val currentHaptic by rememberUpdatedState(onHaptic)
    val currentReorder by rememberUpdatedState(onReorder)
    val reorder = remember { CoverFlowReorder() }
    // While a book is dragged the stage shows the draft order.
    val shown = reorder.draft ?: books
    // The cover whose menu a hold opened shrinks back only once that menu has closed.
    LaunchedEffect(reorder, state) {
        var menuSeen = false
        snapshotFlow { state.menuBookId }.collect { id ->
            if (id != null) {
                menuSeen = true
            } else if (menuSeen) {
                menuSeen = false
                reorder.menuHeldId = null
            }
        }
    }
    val previousLabel = stringResource(R.string.coverflow_previous)
    val nextLabel = stringResource(R.string.coverflow_next)
    val openLabel = stringResource(R.string.coverflow_open)
    val menuLabel = stringResource(R.string.more_options)
    val menuRetreat by androidx.compose.animation.core.animateFloatAsState(
        if (state.menuBookId != null) 1f else 0f,
        animationSpec = com.huangder.lumibooks.ui.animation.VisualMotion.tween(if (motionEnabled) 320 else 80,
            easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "coverFlowMenuRetreat")
    var retainedMenuId by remember { mutableStateOf<String?>(null) }
    SideEffect { state.menuBookId?.let { retainedMenuId = it } }
    val center by remember(state) { derivedStateOf { state.focusedIndex } }
    // Distance ordering only changes at integer and half-integer crossings, not every frame.
    val orderBucket by remember(state) { derivedStateOf { floor(state.position * 2f).toInt() } }
    BoxWithConstraints(modifier.testTag("cover_flow_scene").onGloballyPositioned { layout.coordinates = it }) {
        val reflectionFraction = if (eInk) 0f else 0.28f
        // Room for the pedestal's front face below the polished top surface.
        val pedestalFront = if (eInk) 0.dp else 10.dp
        val width = minOf(maxWidth * 0.5f, 260.dp,
            ((maxHeight - 24.dp - pedestalFront).coerceAtLeast(0.dp)) * 0.75f / (1f + reflectionFraction))
            .coerceAtLeast(1.dp)
        val height = width / 0.75f
        val reflectionHeight = height * reflectionFraction
        val stageDrop = minOf(12.dp, maxHeight * 0.025f)
        val centerOffsetY = with(density) { -reflectionHeight.toPx() / 2f + stageDrop.toPx() - pedestalFront.toPx() / 2f }
        val floorY = with(density) {
            (maxHeight - reflectionHeight + height).toPx() / 2f + stageDrop.toPx() - pedestalFront.toPx() / 2f
        }
        val geometry = remember(width, height, density) {
            with(density) { CoverFlowGeometry(width.toPx(), height.toPx()) }
        }
        SideEffect {
            layout.geometry = geometry
            layout.direction = direction
            layout.centerOffsetY = centerOffsetY
        }
        val range = remember(center, shown.size) { CoverFlowPhysics.visibleRange(center.toFloat(), shown.size) }
        val order = remember(range, orderBucket) { range.sortedByDescending { abs(it - (orderBucket / 2f + 0.25f)) } }
        val blurEffects = remember(density, motionEnabled, reduceTransparency) {
            if (Build.VERSION.SDK_INT >= 31 && motionEnabled && !reduceTransparency) {
                List(13) { step ->
                    if (step == 0) null else android.graphics.RenderEffect.createBlurEffect(
                        step * 0.25f * density.density, step * 0.25f * density.density,
                        android.graphics.Shader.TileMode.DECAL
                    ).asComposeRenderEffect()
                }
            } else emptyList()
        }
        DisposableEffect(center, books, geometry) {
            val requests = range.mapNotNull { shown.getOrNull(it) }.mapNotNull { book ->
                book.coverPath?.let { path ->
                    context.imageLoader.enqueue(ImageRequest.Builder(context).data(path)
                        .memoryCacheKey(bookCoverMemoryCacheKey(book.id, path))
                        .size(geometry.coverWidth.roundToInt().coerceAtLeast(1), geometry.coverHeight.roundToInt().coerceAtLeast(1))
                        .build())
                }
            }
            onDispose { requests.forEach { it.dispose() } }
        }
        if (!eInk) {
            CoverFlowAtmosphere(
                floorY = floorY,
                coverWidth = geometry.coverWidth,
                pedestalDepth = with(density) { reflectionHeight.toPx() },
                pedestalFront = with(density) { pedestalFront.toPx() },
                colors = lightColors,
                modifier = Modifier.fillMaxSize()
            ) {
                val departure = if (readerTransition?.presentation == BookReaderPresentation.CoverFlow &&
                    readerTransition.activeBookId != null) {
                    CoverFlowReaderMotion.sideExit(readerTransition.coverFlowProgressSnapshot.value)
                } else 0f
                sceneAlpha() * (1f - departure)
            }
            CoverFlowReflection(coversLayer, floorY, motionEnabled,
                Modifier.fillMaxWidth().height(reflectionHeight)
                    .offset { IntOffset(0, (floorY + 4.dp.toPx()).roundToInt()) })
        }
        Box(
            Modifier.fillMaxSize()
                .then(if (!eInk) Modifier.drawWithContent {
                    coversLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(coversLayer)
                } else Modifier)
                .then(if (enabled) Modifier.pointerInput(state, books, geometry, direction, motionEnabled, menusEnabled,
                    onReorder != null) {
                    coverFlowGestures(books, state, layout, scope, motionEnabled, menusEnabled,
                        onOpen = { book, rect -> currentOpen(book, rect) }, onHaptic = { currentHaptic() },
                        reorder = reorder.takeIf { onReorder != null },
                        onReorder = { ids -> currentReorder?.invoke(ids) })
                } else Modifier)
                .then(if (enabled && books.isNotEmpty()) Modifier.semantics(mergeDescendants = true) {
                    val focused = books.getOrNull(center)
                    contentDescription = listOfNotNull(focused?.title, focused?.author).joinToString(", ")
                    stateDescription = "${center + 1} / ${books.size}"
                    horizontalScrollAxisRange = ScrollAxisRange({ state.position }, { (books.size - 1).toFloat() }, direction < 0f)
                    customActions = listOf(
                        CustomAccessibilityAction(previousLabel) { state.settle(scope, center - 1, motionEnabled); true },
                        CustomAccessibilityAction(nextLabel) { state.settle(scope, center + 1, motionEnabled); true }
                    )
                    onClick(openLabel) {
                        focused?.let {
                            if (state.isCentered(center)) currentOpen(it, layout.coverBounds(it.id))
                            else state.settle(scope, center, motionEnabled)
                        }
                        true
                    }
                    if (menusEnabled) onLongClick(menuLabel) { state.settle(scope, center, motionEnabled, showMenuAfter = true); true }
                } else Modifier.clearAndSetSemantics { })
        ) {
            order.forEachIndexed { rank, index ->
                val book = shown[index]
                key(book.id) {
                    // Slot the cover is drawn at: glides to a new index while a book is dragged
                    // through the order, so neighbours make way instead of jumping.
                    val slot = remember { androidx.compose.animation.core.Animatable(index.toFloat()) }
                    LaunchedEffect(index) {
                        if (reorder.gliding && motionEnabled) {
                            slot.animateTo(index.toFloat(), androidx.compose.animation.core.spring(
                                dampingRatio = 0.86f, stiffness = 420f))
                        } else slot.snapTo(index.toFloat())
                    }
                    val held = reorder.liftedId == book.id
                    val menuHeld = reorder.menuHeldId == book.id
                    val dragged = reorder.draggedId == book.id
                    // After the long-press tick a still hold grows the cover steadily; when its
                    // menu opens it carries on outwards a little, then springs back to its normal
                    // size (the menu shows covers unscaled); after the menu it stays there.
                    val liftAnim = remember { androidx.compose.animation.core.Animatable(1f) }
                    LaunchedEffect(held, menuHeld, dragged, motionEnabled) {
                        when {
                            !motionEnabled -> liftAnim.snapTo(1f)
                            held -> liftAnim.animateTo(1.12f, androidx.compose.animation.core.tween(
                                HOLD_TO_MENU_MILLIS.toInt(), easing = androidx.compose.animation.core.LinearEasing))
                            menuHeld -> liftAnim.animateTo(1f,
                                androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 240f),
                                initialVelocity = HOLD_POP_VELOCITY)
                            dragged -> liftAnim.animateTo(1.08f,
                                androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 500f))
                            else -> liftAnim.animateTo(1f,
                                androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 300f))
                        }
                    }
                    DisposableEffect(book.id) { onDispose { layout.covers.remove(book.id) } }
                    val deletionAlpha by androidx.compose.animation.core.animateFloatAsState(
                        if (book.id in deletingIds) 0f else 1f,
                        com.huangder.lumibooks.ui.animation.VisualMotion.tween(if (motionEnabled) 220 else 0), label = "coverFlowDelete")
                    // Keep anchors stable while the focus crosses books. Adding/removing a
                    // position callback at each crossing can miss a layout-only registration.
                    val folderCard = folderCards[book.id]
                    val anchor = if (folderCard != null) Modifier else Modifier.bookCoverTransitionAnchor(
                        bookId = book.id, cornerRadiusDp = 2f,
                        titleStyle = BookCoverTitleStyle(14f, 2, 8f, BookCoverTitleColor.Secondary, false, true, 8),
                        presentation = BookReaderPresentation.CoverFlow
                    )
                    Box(
                        Modifier.align(Alignment.Center).size(width, height)
                            .zIndex(if (dragged || held) order.size.toFloat() else rank.toFloat())
                            .graphicsLayer {
                                // The dragged book stays in the middle; the others glide.
                                val at = when {
                                    dragged -> state.position
                                    reorder.gliding -> slot.value
                                    else -> index.toFloat()
                                }
                                val pose = geometry.transform((at - state.position) * direction)
                                val readerDeparture = if (readerTransition?.presentation == BookReaderPresentation.CoverFlow &&
                                    readerTransition.activeBookId != null && readerTransition.activeBookId != book.id) {
                                    CoverFlowReaderMotion.sideExit(readerTransition.coverFlowProgressSnapshot.value)
                                } else 0f
                                val retainedId = state.menuBookId ?: retainedMenuId
                                val departure = maxOf(readerDeparture, if (book.id != retainedId) menuRetreat else 0f)
                                val entry = CoverFlowEntranceMotion.cover(entrance?.sceneProgress?.value ?: 1f, (index - center).toFloat())
                                val animateEntry = entrance?.motionEnabled == true
                                translationX = pose.x * sceneAlpha() + kotlin.math.sign(pose.x) * geometry.coverWidth *
                                    (0.65f * departure + if (animateEntry && index != center) 1.4f * (1f - entry) else 0f)
                                translationY = centerOffsetY - if (animateEntry && index == center) (1f - entry) * 28.dp.toPx() else 0f
                                rotationY = pose.rotationY
                                scaleX = pose.scale * (0.88f + 0.12f * sceneAlpha()) * (1f - 0.08f * departure) *
                                    if (animateEntry && index == center) 1f + 0.24f * (1f - entry) else 1f
                                // Read here, in the layer: the lift animates without recomposing.
                                scaleX *= liftAnim.value
                                scaleY = scaleX
                                cameraDistance = pose.cameraDistance
                                alpha = if (book.id == hiddenHeroId) 0f else pose.alpha * sceneAlpha() * deletionAlpha *
                                    (1f - departure) * (entry * 3f).coerceAtMost(1f)
                                renderEffect = blurEffects.getOrNull((pose.blurDp * 4f).roundToInt().coerceIn(0, 12))
                            }
                            .then(anchor)
                            .onGloballyPositioned { layout.covers[book.id] = it }
                            .clearAndSetSemantics { }
                    ) {
                        if (folderCard != null) {
                            CoverFlowFolderCardFace(folderCard, Modifier.fillMaxSize())
                        } else {
                            // Read only by the gloss layer's graphicsLayer: no recomposition or redraw.
                            CoverFlowCover(book, Modifier.fillMaxSize(), tilt = {
                                geometry.transform((index - state.position) * direction).rotationY / 62f
                            })
                        }
                    }
                }
            }
        }
    }
}

/**
 * A cover dressed as a physical book: paper block on the fore-edge, hinge crease at the spine and
 * a gloss that follows [tilt]. All of it is recorded into the stage layer, so the reflection
 * shows the same book without another request or composition.
 */
@Composable
internal fun CoverFlowCover(book: Book, modifier: Modifier = Modifier, tilt: (() -> Float)? = null) {
    val context = LocalContext.current
    val eInk = LocalEInkMode.current
    val dark = com.huangder.lumibooks.ui.theme.LocalIsDarkTheme.current
    val gloss = !eInk && !LocalReduceTransparency.current
    // Spine corners stay crisp; the fore-edge is slightly softer, like a worn board.
    val shape = AppRoundedCornerShape(topStart = 1.5.dp, topEnd = 3.dp, bottomEnd = 3.dp, bottomStart = 1.5.dp)
    val request = remember(book.id, book.coverPath, context) {
        ImageRequest.Builder(context).data(book.coverPath)
            .memoryCacheKey(bookCoverMemoryCacheKey(book.id, book.coverPath)).build()
    }
    Box(modifier
        .then(if (eInk) Modifier else Modifier.coverFlowBookBody(dark))
        .then(if (eInk) Modifier else Modifier.shadow(8.dp, shape, clip = false,
            ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.16f)))
        .clip(shape).background(AppColors.BgGray)
        .then(if (eInk) Modifier.border(1.dp, Color.Black, shape) else Modifier.coverFlowBookSurface(dark)),
        contentAlignment = Alignment.Center) {
        // The fallback remains beneath the image, including when an old cover file is unavailable.
        Text(book.title.take(40), fontSize = AppType.BodySmall, fontWeight = FontWeight.SemiBold,
            color = AppColors.TextSecondary, textAlign = TextAlign.Center, maxLines = 4,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(12.dp))
        if (book.coverPath != null) AsyncImage(request, contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (gloss) CoverFlowSheen(dark, tilt)
        com.huangder.lumibooks.ui.components.BookCoverReadingProgress(book, compact = true)
    }
}

private suspend fun PointerInputScope.coverFlowGestures(
    books: List<Book>, state: CoverFlowState, layout: CoverFlowSceneLayout,
    scope: CoroutineScope, motionEnabled: Boolean, menusEnabled: Boolean,
    onOpen: (Book, Rect?) -> Unit, onHaptic: () -> Unit,
    reorder: CoverFlowReorder? = null,
    onReorder: (List<String>) -> Unit = {}
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        val hit = layout.hit(down.position, books, state.position, state.menuBookId)
        val wasCentered = hit != null && state.isCentered(hit)
        state.beginDrag()
        val velocity = VelocityTracker()
        velocity.addPosition(down.uptimeMillis, down.position)
        var last = down.position
        var result = 0 // 0 waiting, 1 tap, 2 horizontal drag, 3 cancelled
        val slop = viewConfiguration.touchSlop
        val decision = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (result == 0) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || change.isConsumed || event.changes.count { it.pressed } > 1) {
                    result = 3
                } else {
                    velocity.addPosition(change.uptimeMillis, change.position)
                    last = change.position
                    val delta = change.position - down.position
                    when {
                        !change.pressed -> { result = 1; change.consume() }
                        abs(delta.x) > slop && abs(delta.x) >= abs(delta.y) -> {
                            result = 2
                            change.consume()
                            state.dragBy(-delta.x * layout.direction / layout.geometry.dragStep)
                        }
                        abs(delta.y) > slop -> result = 3
                    }
                }
            }
            result
        }
        if (decision == null && reorder != null && menusEnabled && hit != null) {
            holdDragOrMenu(books, hit, down.id, last, state, layout, scope, motionEnabled, onHaptic, reorder, onReorder)
        } else if (decision == null) {
            if (menusEnabled && hit != null) {
                onHaptic()
                if (state.isCentered(hit)) state.showMenu()
                else state.settle(scope, hit, motionEnabled, showMenuAfter = true)
            } else state.release(scope, 0f, motionEnabled)
            do {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        } else if (result == 2) {
            var released = false
            while (!released) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || change.isConsumed || event.changes.count { it.pressed } > 1) {
                    state.release(scope, 0f, motionEnabled)
                    break
                }
                velocity.addPosition(change.uptimeMillis, change.position)
                state.dragBy(-(change.position.x - last.x) * layout.direction / layout.geometry.dragStep)
                last = change.position
                change.consume()
                if (!change.pressed) {
                    released = true
                    state.release(scope, -velocity.calculateVelocity().x * layout.direction / layout.geometry.dragStep, motionEnabled)
                }
            }
        } else if (result == 1 && hit != null) {
            if (wasCentered) onOpen(books[hit], layout.coverBounds(books[hit].id))
            else state.settle(scope, hit, motionEnabled)
        } else state.release(scope, 0f, motionEnabled)
    }
}

/**
 * After the long-press tick on cover [hit]: holding still grows it until its menu opens (with a
 * second tick); sliding sideways first picks it up and drags it through the order instead.
 */
private suspend fun AwaitPointerEventScope.holdDragOrMenu(
    books: List<Book>, hit: Int, pointer: PointerId, downPosition: Offset,
    state: CoverFlowState, layout: CoverFlowSceneLayout, scope: CoroutineScope, motionEnabled: Boolean,
    onHaptic: () -> Unit, reorder: CoverFlowReorder, onReorder: (List<String>) -> Unit
) {
    val book = books[hit]
    val slop = viewConfiguration.touchSlop
    // A menu that never opened (its settle was interrupted) must not leave a cover grown.
    if (state.menuBookId == null) reorder.menuHeldId = null
    onHaptic()
    if (!state.isCentered(hit)) state.settle(scope, hit, motionEnabled)
    reorder.liftedId = book.id
    var last = downPosition
    var travel = Offset.Zero
    val moved = withTimeoutOrNull(HOLD_TO_MENU_MILLIS) {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointer }
            event.changes.forEach { it.consume() }
            if (change == null || !change.pressed) return@withTimeoutOrNull false
            travel += change.position - last
            last = change.position
            if (travel.getDistance() > slop) return@withTimeoutOrNull true
        }
        @Suppress("UNREACHABLE_CODE")
        false
    }
    when (moved) {
        null -> {
            reorder.menuHeldId = book.id
            reorder.liftedId = null
            onHaptic()
            if (state.isCentered(hit)) state.showMenu()
            else state.settle(scope, hit, motionEnabled, showMenuAfter = true)
            consumeRest(pointer)
            return
        }
        false -> {
            reorder.liftedId = null
            return
        }
        true -> if (isCoverFlowFolderCard(book.id)) {
            // Folder cards keep their place ahead of the books; a slide just lets go.
            reorder.liftedId = null
            consumeRest(pointer)
            return
        }
    }

    // Drag: the carousel follows the finger beneath the held book.
    val range = pinnedGroupRange(books, hit) { it.isPinned }
    reorder.start(book, books, range, hit.toFloat())
    state.beginDrag()
    val step = layout.geometry.dragStep
    fun follow(deltaX: Float) = state.dragTo(reorder.moveBy(deltaX * layout.direction / step))
    follow(travel.x)
    reorder.fingerX = last.x
    val width = size.width.toFloat()
    val edgeScroll = scope.launch {
        while (true) {
            withFrameNanos { }
            // The nearer the finger is to an edge, the faster the books flip past: speed rises
            // with the square of the depth into the edge zone.
            val zone = width * EDGE_ZONE_FRACTION
            val x = reorder.fingerX
            val depth = when {
                x < zone -> -(zone - x) / zone
                x > width - zone -> (x - (width - zone)) / zone
                else -> 0f
            }.coerceIn(-1f, 1f)
            if (depth != 0f) {
                val speed = EDGE_MIN_BOOKS_PER_FRAME +
                    (EDGE_MAX_BOOKS_PER_FRAME - EDGE_MIN_BOOKS_PER_FRAME) * depth * depth
                state.dragTo(reorder.moveBy(kotlin.math.sign(depth) * speed * layout.direction))
            }
        }
    }
    try {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointer }
            event.changes.forEach { it.consume() }
            if (change == null) {
                reorder.cancel(scope)
                state.settle(scope, hit, motionEnabled)
                return
            }
            follow(change.position.x - last.x)
            last = change.position
            reorder.fingerX = last.x
            if (!change.pressed) {
                val result = reorder.finish(scope)?.map { it.id } ?: return
                if (result != books.map { it.id }) onReorder(result)
                state.finishReorder(result, book.id)
                state.settle(scope, result.indexOf(book.id), motionEnabled)
                return
            }
        }
    } finally {
        edgeScroll.cancel()
    }
}

/** Edge scrolling while a cover is dragged: zone width and speed range (books per frame). */
private const val EDGE_ZONE_FRACTION = 0.22f
private const val EDGE_MIN_BOOKS_PER_FRAME = 0.025f
private const val EDGE_MAX_BOOKS_PER_FRAME = 0.32f

private suspend fun AwaitPointerEventScope.consumeRest(pointer: PointerId) {
    while (true) {
        val event = awaitPointerEvent()
        event.changes.forEach { it.consume() }
        val change = event.changes.firstOrNull { it.id == pointer } ?: return
        if (!change.pressed) return
    }
}
