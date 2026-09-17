package com.huangder.lumibooks.ui.bookshelf

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.huangder.lumibooks.ui.components.BookCoverProgressOverlay
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import kotlinx.coroutines.CoroutineScope
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
    sceneAlpha: () -> Float = { 1f }
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val eInk = LocalEInkMode.current
    val entrance = LocalCoverFlowEntrance.current
    val coversLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val readerTransition = LocalBookReaderAnchorScope.current?.transitionState
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val currentOpen by rememberUpdatedState(onOpen)
    val currentHaptic by rememberUpdatedState(onHaptic)
    val previousLabel = stringResource(R.string.coverflow_previous)
    val nextLabel = stringResource(R.string.coverflow_next)
    val openLabel = stringResource(R.string.coverflow_open)
    val menuLabel = stringResource(R.string.more_options)
    val menuRetreat by androidx.compose.animation.core.animateFloatAsState(
        if (state.menuBookId != null) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(if (motionEnabled) 320 else 80,
            easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "coverFlowMenuRetreat")
    var retainedMenuId by remember { mutableStateOf<String?>(null) }
    SideEffect { state.menuBookId?.let { retainedMenuId = it } }
    val center by remember(state) { derivedStateOf { state.focusedIndex } }
    // Distance ordering only changes at integer and half-integer crossings, not every frame.
    val orderBucket by remember(state) { derivedStateOf { floor(state.position * 2f).toInt() } }
    BoxWithConstraints(modifier.testTag("cover_flow_scene").onGloballyPositioned { layout.coordinates = it }) {
        val reflectionFraction = if (eInk) 0f else 0.28f
        val width = minOf(maxWidth * 0.5f, 260.dp,
            ((maxHeight - 24.dp).coerceAtLeast(0.dp)) * 0.75f / (1f + reflectionFraction))
            .coerceAtLeast(1.dp)
        val height = width / 0.75f
        val reflectionHeight = height * reflectionFraction
        val stageDrop = minOf(12.dp, maxHeight * 0.025f)
        val centerOffsetY = with(density) { -reflectionHeight.toPx() / 2f + stageDrop.toPx() }
        val floorY = with(density) { (maxHeight - reflectionHeight + height).toPx() / 2f + stageDrop.toPx() }
        val geometry = remember(width, height, density) {
            with(density) { CoverFlowGeometry(width.toPx(), height.toPx()) }
        }
        SideEffect {
            layout.geometry = geometry
            layout.direction = direction
            layout.centerOffsetY = centerOffsetY
        }
        val range = remember(center, books.size) { CoverFlowPhysics.visibleRange(center.toFloat(), books.size) }
        val order = remember(range, orderBucket) { range.sortedByDescending { abs(it - (orderBucket / 2f + 0.25f)) } }
        val blurEffects = remember(density, motionEnabled) {
            if (Build.VERSION.SDK_INT >= 31 && motionEnabled) {
                List(13) { step ->
                    if (step == 0) null else android.graphics.RenderEffect.createBlurEffect(
                        step * 0.25f * density.density, step * 0.25f * density.density,
                        android.graphics.Shader.TileMode.DECAL
                    ).asComposeRenderEffect()
                }
            } else emptyList()
        }
        DisposableEffect(center, books, geometry) {
            val requests = range.mapNotNull { books.getOrNull(it) }.mapNotNull { book ->
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
            CoverFlowAtmosphere(floorY, Modifier.fillMaxSize()) {
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
                .then(if (enabled) Modifier.pointerInput(state, books, geometry, direction, motionEnabled) {
                    coverFlowGestures(books, state, layout, scope, motionEnabled,
                        onOpen = { book, rect -> currentOpen(book, rect) }, onHaptic = { currentHaptic() })
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
                    onLongClick(menuLabel) { state.settle(scope, center, motionEnabled, showMenuAfter = true); true }
                } else Modifier.clearAndSetSemantics { })
        ) {
            order.forEachIndexed { rank, index ->
                val book = books[index]
                key(book.id) {
                    DisposableEffect(book.id) { onDispose { layout.covers.remove(book.id) } }
                    val deletionAlpha by androidx.compose.animation.core.animateFloatAsState(
                        if (book.id in deletingIds) 0f else 1f,
                        androidx.compose.animation.core.tween(if (motionEnabled) 220 else 0), label = "coverFlowDelete")
                    // Keep anchors stable while the focus crosses books. Adding/removing a
                    // position callback at each crossing can miss a layout-only registration.
                    val anchor = Modifier.bookCoverTransitionAnchor(
                        bookId = book.id, cornerRadiusDp = 2f,
                        titleStyle = BookCoverTitleStyle(14f, 2, 8f, BookCoverTitleColor.Secondary, false, true, 8),
                        presentation = BookReaderPresentation.CoverFlow
                    )
                    Box(
                        Modifier.align(Alignment.Center).size(width, height).zIndex(rank.toFloat())
                            .graphicsLayer {
                                val pose = geometry.transform((index - state.position) * direction)
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
                        CoverFlowCover(book, downloadStates[book.id], Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
internal fun CoverFlowCover(book: Book, downloadState: BookDownloadState?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(2.dp)
    val request = remember(book.id, book.coverPath, context) {
        ImageRequest.Builder(context).data(book.coverPath)
            .memoryCacheKey(bookCoverMemoryCacheKey(book.id, book.coverPath)).build()
    }
    Box(modifier.shadow(12.dp, shape, clip = false,
        ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.20f))
        .clip(shape).background(AppColors.BgGray), contentAlignment = Alignment.Center) {
        // The fallback remains beneath the image, including when an old cover file is unavailable.
        Text(book.title.take(40), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = AppColors.TextSecondary, textAlign = TextAlign.Center, maxLines = 4,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(12.dp))
        if (book.coverPath != null) AsyncImage(request, contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        BookCoverProgressOverlay(book, downloadState, showReadingProgress = false)
    }
}

private suspend fun PointerInputScope.coverFlowGestures(
    books: List<Book>, state: CoverFlowState, layout: CoverFlowSceneLayout,
    scope: CoroutineScope, motionEnabled: Boolean,
    onOpen: (Book, Rect?) -> Unit, onHaptic: () -> Unit
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
        if (decision == null) {
            if (hit != null) {
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
