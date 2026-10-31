package com.huangder.lumibooks.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.huangder.lumibooks.ui.reader.engine.drawReaderUnderline
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangedIgnoreConsumed
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.LongPressSelectionRange
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.model.SelectionMenuSettings
import com.huangder.lumibooks.domain.model.selectionRangeAt
import com.huangder.lumibooks.ui.components.ConfigurableBackHandler
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal data class PdfTextSelection(val model: PdfTextPage, val start: Int, val end: Int) {
    val locator get() = model.locator(start, end)
    val text get() = model.text.substring(start, end)
}

@Stable
internal class PdfTextAnnotationController(val session: PdfTextSession) {
    var selection by mutableStateOf<PdfTextSelection?>(null)
    var dragging by mutableStateOf(false)
    var holding by mutableStateOf(false)
    var actionSheetOpen by mutableStateOf(false)
    var viewportTransform by mutableStateOf(Triple(1f, 0f, 0f))
    var bounds by mutableStateOf(Rect.Zero)
    var enabled by mutableStateOf(true)
    var notes by mutableStateOf<List<Note>>(emptyList())
    var range by mutableStateOf(LongPressSelectionRange.DEFAULT)
    var editing by mutableStateOf(false)
    var showNotes by mutableStateOf(false)
    var editingNote by mutableStateOf<Note?>(null)
    var noteText by mutableStateOf("")
    var noteTags by mutableStateOf<List<String>>(emptyList())
    var flash by mutableStateOf<Note?>(null)
    var menuRevision by mutableIntStateOf(0)
    var onSelectionStarted: () -> Unit = {}
    var gestureRevision = 0L

    fun clear() { selection = null; dragging = false; bounds = Rect.Zero; menuRevision++ }
    fun select(model: PdfTextPage, start: Int, end: Int) {
        val snapped = model.snap(start, end) ?: return
        selection = PdfTextSelection(model, snapped.first, snapped.last + 1)
        menuRevision++
    }
    fun beginEdit() {
        val state = menuState() ?: return
        editingNote = state.existingNote
        noteText = state.existingNote?.note.orEmpty()
        noteTags = state.existingNote?.tags.orEmpty()
        editing = true
    }
    fun menuState(): SelectionState? {
        val selected = selection ?: return null
        val locator = selected.locator?.encode() ?: return null
        val overlapping = notes.filter { note ->
            note.chapterIndex == selected.model.page && !note.isStale &&
                note.startPosition < selected.end && note.endPosition > selected.start &&
                PdfTextLocator.decode(note.startLocatorJson)?.matches(selected.model) == true
        }
        return SelectionState(selected.model.page, 0, selected.start, selected.end, selected.text,
            bounds.center.x, bounds.top,
            overlapping.filter { it.type == "highlight" }, overlapping.filter { it.type == "underline" },
            bounds.top, bounds.bottom, bounds.left, bounds.right,
            startLocatorJson = locator, endLocatorJson = locator)
    }
}

internal val LocalPdfTextAnnotations = staticCompositionLocalOf<PdfTextAnnotationController?> { null }

@Composable
internal fun rememberPdfTextAnnotations(context: Context, location: String?, enabled: Boolean, revision: Long = 0L): PdfTextAnnotationController? {
    val controller = remember(location, enabled, revision) {
        if (enabled && location != null) PdfTextAnnotationController(PdfTextSession(context.applicationContext, location)) else null
    }
    DisposableEffect(controller) { onDispose { controller?.session?.close() } }
    return controller
}

@Composable
internal fun BoxScope.PdfPageTextLayer(
    page: Int, visible: Boolean, crop: RasterHorizontalCrop, ratio: Float, fit: Boolean, zoomScale: Float = 1f
) {
    val controller = LocalPdfTextAnnotations.current ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current.density
    val touchTolerance = 14f * density / zoomScale.coerceAtLeast(1f)
    val openingComplete = LocalReaderOpeningComplete.current
    var model by remember(controller, page) { mutableStateOf<PdfTextPage?>(null) }
    var failed by remember(controller, page) { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val transform = remember(ratio, crop, canvasSize, fit) {
        PdfTextTransform(ratio, crop.left, crop.right, canvasSize.width.toFloat().coerceAtLeast(1f),
            canvasSize.height.toFloat().coerceAtLeast(1f), fit)
    }
    suspend fun load(): PdfTextPage? {
        model?.let { return it }
        return try { controller.session.page(page).also { model = it } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            failed = true
            android.util.Log.w("PdfText", "Unable to extract page $page", error)
            null
        }
    }
    LaunchedEffect(controller, page, visible, openingComplete) {
        if (visible && openingComplete && !failed) load()
    }
    val selection = controller.selection?.takeIf { it.model.page == page }
    val currentModel = model
    val notes = controller.notes.filter { it.chapterIndex == page && !it.isInkStroke && !it.isStale }
    val anchoredNotes = remember(currentModel, notes) {
        notes.mapNotNull { note ->
            val locator = PdfTextLocator.decode(note.startLocatorJson)
            if (currentModel != null && locator?.matches(currentModel) == true) note to locator.copy(quads = currentModel.quads(locator.start, locator.end)) else null
        }
    }
    val flashLocator = remember(currentModel, controller.flash) {
        PdfTextLocator.decode(controller.flash?.startLocatorJson)?.takeIf { currentModel != null && it.matches(currentModel) }
            ?.let { it.copy(quads = currentModel!!.quads(it.start, it.end)) }
    }
    val accent = AppColors.Accent
    val eInk = LocalEInkMode.current
    fun updateBounds() {
        val selected = controller.selection?.takeIf { it.model.page == page } ?: return
        val layout = coordinates?.takeIf { it.isAttached } ?: return
        val points = selected.model.quads(selected.start, selected.end).flatMap { it.points }
            .map { p -> transform.display(p).let { layout.localToWindow(Offset(it.x, it.y)) } }
        if (points.isNotEmpty()) controller.bounds = Rect(points.minOf { it.x }, points.minOf { it.y },
            points.maxOf { it.x }, points.maxOf { it.y })
    }
    SideEffect { updateBounds() }
    LaunchedEffect(controller.viewportTransform, selection) {
        withFrameNanos { }
        updateBounds()
    }
    val latestTolerance by rememberUpdatedState(touchTolerance)
    fun annotationAt(position: Offset): Note? {
        // Read the live notes and page model inside the gesture. A remembered
        // local function reference can retain the page's initial fit transform.
        return controller.notes.asReversed().firstOrNull { note ->
            if (note.chapterIndex != page || note.isStale || note.isInkStroke) return@firstOrNull false
            val locator = PdfTextLocator.decode(note.startLocatorJson) ?: return@firstOrNull false
            val active = model
            if (active != null && !locator.matches(active)) return@firstOrNull false
            val quads = active?.quads(locator.start, locator.end) ?: locator.quads
            quads.any { quad ->
                PdfTextQuad(quad.points.map(transform::display))
                    .distanceTo(PdfTextPoint(position.x, position.y)) <= latestTolerance
            }
        }
    }
    Box(Modifier.fillMaxSize().onSizeChanged { canvasSize = it }.onGloballyPositioned {
        coordinates = it
        updateBounds()
    }) {
        Canvas(Modifier.fillMaxSize().testTag("pdf-text-page-$page").then(if (controller.enabled) Modifier.pointerInput(controller, page, transform) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val gestureRevision = ++controller.gestureRevision
                val downInWindow = coordinates?.localToWindow(down.position) ?: down.position
                // Consume annotation taps before the reader's tap-to-turn handler.
                if (annotationAt(down.position) != null || controller.selection != null) down.consume()
                var cancelled = false
                var released = false
                var position = down.position
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        val windowPosition = change?.let { coordinates?.localToWindow(it.position) ?: it.position }
                        if (event.changes.count { it.pressed } > 1 || change == null ||
                            (windowPosition!! - downInWindow).getDistance() > viewConfiguration.touchSlop ||
                            (change.isConsumed && change.positionChangedIgnoreConsumed())) {
                            cancelled = true
                            break
                        }
                        position = change.position
                        // A scrolling ancestor can consume this movement after
                        // our Main pass. The page may also move with the finger,
                        // leaving its local position almost unchanged.
                        val finalChange = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id }
                        if (finalChange == null || (finalChange.isConsumed && finalChange.positionChangedIgnoreConsumed())) {
                            cancelled = true
                            break
                        }
                        if (!change.pressed) { released = true; break }
                    }
                }
                if (!cancelled && !released) {
                    controller.holding = true
                    currentEvent.changes.forEach { it.consume() }
                    val displayPoint = PdfTextPoint(position.x, position.y)
                    val point = transform.original(displayPoint)
                    val requestRevision = controller.menuRevision
                    if (transform.visible(point)) scope.launch {
                        val active = load()
                        if (!controller.enabled || controller.menuRevision != requestRevision ||
                            controller.gestureRevision != gestureRevision) return@launch
                        if (active == null || active.glyphs.isEmpty()) {
                            Toast.makeText(context, if (active == null) R.string.pdf_text_selection_unavailable
                                else R.string.pdf_page_no_selectable_text, Toast.LENGTH_SHORT).show()
                        } else {
                            active.hitNear(displayPoint, transform, latestTolerance)?.let { glyph ->
                                val range = active.text.selectionRangeAt(glyph.start, controller.range, active.lineRange(glyph))
                                if (range != null) {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                    controller.onSelectionStarted()
                                    controller.select(active, range.first, range.last + 1)
                                }
                            }
                        }
                    }
                    try {
                        do { val event = awaitPointerEvent(); event.changes.forEach { it.consume() } }
                        while (event.changes.any { it.pressed })
                    } finally { controller.holding = false }
                } else if (!cancelled && released) {
                    val hit = annotationAt(position)
                    val active = model
                    if (hit != null) {
                        currentEvent.changes.forEach { it.consume() }
                        val requestRevision = controller.menuRevision
                        scope.launch {
                            val loaded = active ?: load()
                            if (loaded != null && controller.enabled && controller.menuRevision == requestRevision &&
                                controller.gestureRevision == gestureRevision &&
                                PdfTextLocator.decode(hit.startLocatorJson)?.matches(loaded) == true) {
                                controller.onSelectionStarted()
                                controller.select(loaded, hit.startPosition, hit.endPosition)
                            }
                        }
                    } else if (controller.selection != null) {
                        if (active?.hitNear(PdfTextPoint(position.x, position.y), transform, latestTolerance) == null) {
                            controller.clear()
                        }
                        currentEvent.changes.forEach { it.consume() }
                    }
                }
            }
        } else Modifier)) {
            val clipStart = transform.display(PdfTextPoint(crop.left, 0f))
            val clipEnd = transform.display(PdfTextPoint(crop.right, 1f))
            clipRect(clipStart.x, clipStart.y, clipEnd.x, clipEnd.y) {
                anchoredNotes.forEach { (note, locator) ->
                    val color = runCatching { Color(android.graphics.Color.parseColor(resolveReaderHighlightColor(note.color))) }
                        .getOrDefault(accent)
                    locator.quads.forEach { quad ->
                        drawPdfTextMark(quad, transform, if (eInk) Color.Black else color,
                            note.type == "underline", noteUnderlineMode(note), eInk, zoomScale = zoomScale,
                            neighboringLines = currentModel?.lineQuads.orEmpty())
                    }
                }
                selection?.model?.quads(selection.start, selection.end)?.forEach {
                    drawPdfTextMark(it, transform, accent.copy(alpha = 0.28f), false, 0, false, zoomScale,
                        selection.model.lineQuads)
                }
                flashLocator?.let { locator ->
                    locator.quads.forEach {
                        drawPdfTextMark(it, transform, accent.copy(alpha = 0.4f), false, 0, false, zoomScale,
                            currentModel?.lineQuads.orEmpty())
                    }
                }
            }
        }
        if (selection != null && controller.enabled && !controller.editing) {
            val glyphs = selection.model.glyphs.filter { it.start < selection.end && it.end > selection.start }
            val first = glyphs.firstOrNull()
            val last = glyphs.lastOrNull()
            if (first != null && last != null) {
                PdfSelectionHandle(transform.display(first.quad.points[3]), true, controller, transform, accent, zoomScale)
                PdfSelectionHandle(transform.display(last.quad.points[2]), false, controller, transform, accent, zoomScale)
            }
        }
    }
}

@Composable
private fun PdfSelectionHandle(
    anchor: PdfTextPoint, startHandle: Boolean, controller: PdfTextAnnotationController,
    transform: PdfTextTransform, color: Color, zoomScale: Float
) {
    val inverseZoom = 1f / zoomScale.coerceAtLeast(1f)
    val radius = with(LocalDensity.current) { 22.dp.toPx() * inverseZoom }
    val latestAnchor by rememberUpdatedState(anchor)
    Box(Modifier.offset { IntOffset((anchor.x - radius).roundToInt(), (anchor.y - radius).roundToInt()) }
        .size((44f * inverseZoom).dp).testTag(if (startHandle) "pdf-selection-start" else "pdf-selection-end")
        .pointerInput(controller, transform, startHandle) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                down.consume()
                val initial = controller.selection ?: return@awaitEachGesture
                controller.dragging = true
                var position = latestAnchor
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } > 1) { controller.clear(); break }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        position = PdfTextPoint(position.x + change.position.x - change.previousPosition.x,
                            position.y + change.position.y - change.previousPosition.y)
                        val point = transform.original(position)
                        val glyph = initial.model.hit(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f), nearest = true)
                        if (glyph != null) {
                            if (startHandle) controller.select(initial.model, minOf(glyph.start, initial.end - 1), initial.end)
                            else controller.select(initial.model, initial.start, maxOf(glyph.end, initial.start + 1))
                        }
                        change.consume()
                    } while (event.changes.any { it.pressed })
                } finally { controller.dragging = false }
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawLine(color, center - Offset(0f, 8.dp.toPx() * inverseZoom), center + Offset(0f, 6.dp.toPx() * inverseZoom), 2.dp.toPx() * inverseZoom)
            drawCircle(color, 6.dp.toPx() * inverseZoom, center + Offset(if (startHandle) -4.dp.toPx() * inverseZoom else 4.dp.toPx() * inverseZoom, 7.dp.toPx() * inverseZoom))
        }
    }
}

internal fun DrawScope.drawPdfTextMark(
    quad: PdfTextQuad, transform: PdfTextTransform, color: Color,
    underline: Boolean, style: Int, eInk: Boolean, zoomScale: Float = 1f,
    neighboringLines: List<PdfTextQuad> = emptyList()
) {
    val p = quad.points.map { transform.display(it).let { point -> Offset(point.x, point.y) } }
    val advance = p[1] - p[0]
    val length = advance.getDistance().coerceAtLeast(0.1f)
    val height = (p[3] - p[0]).getDistance().coerceAtLeast(0.1f)
    // Work in line coordinates so rotated text uses exactly the reader's shapes.
    val screenDensity = density / zoomScale.coerceAtLeast(1f)
    val unit = advance / length
    val normal = Offset(-unit.y, unit.x)
    val padding = minOf(0.6f * screenDensity, height * 0.04f)
    var top = -padding
    var bottom = height + padding
    val gap = minOf(screenDensity, height * 0.1f)
    if (!underline) neighboringLines.forEach { neighbor ->
        val corners = neighbor.points.map { transform.display(it).let { point -> Offset(point.x, point.y) } }
        val direction = corners[1] - corners[0]
        if (direction.getDistance() <= 0f || kotlin.math.abs(
                (direction.x * unit.x + direction.y * unit.y) / direction.getDistance()) < 0.98f) return@forEach
        val along = corners.map { (it - p[0]).let { d -> d.x * unit.x + d.y * unit.y } }
        if (along.max() <= 0f || along.min() >= length) return@forEach
        val across = corners.map { (it - p[0]).let { d -> d.x * normal.x + d.y * normal.y } }
        val center = (across.min() + across.max()) / 2f
        val distance = center - height / 2f
        if (kotlin.math.abs(distance) <= height * 0.1f) return@forEach
        val boundary = (center + height / 2f) / 2f
        if (distance < 0f) top = maxOf(top, boundary + gap / 2f)
        else bottom = minOf(bottom, boundary - gap / 2f)
    }
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val save = native.save()
        native.translate(p[0].x, p[0].y)
        native.rotate(Math.toDegrees(kotlin.math.atan2(advance.y, advance.x).toDouble()).toFloat())
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.argb(
                ((if (eInk && !underline) 0.14f else if (!underline && color.alpha == 1f) 0.25f else color.alpha) * 255).roundToInt(),
                (color.red * 255).roundToInt(), (color.green * 255).roundToInt(), (color.blue * 255).roundToInt())
        }
        if (!underline) {
            if (bottom > top) {
                val horizontalPadding = minOf(1.5f * screenDensity, height * 0.08f)
                val radius = minOf(2f * screenDensity, (bottom - top) * 0.12f, length * 0.15f)
                native.drawRoundRect(-horizontalPadding, top, length + horizontalPadding,
                    bottom, radius, radius, paint)
            }
        } else {
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = screenDensity
            paint.strokeCap = android.graphics.Paint.Cap.ROUND
            drawReaderUnderline(native, paint, style, 0f, length, height + 2f * screenDensity, false, screenDensity)
        }
        native.restoreToCount(save)
    }
}

@Composable
internal fun PdfTextAnnotationOverlays(
    controller: PdfTextAnnotationController, viewModel: ReaderViewModel, menuSettings: SelectionMenuSettings,
    availableTags: List<String>, backdrop: Backdrop?, onEditTags: (Note) -> Unit,
    onJump: (Note) -> Unit, pageCount: Int
) {
    val context = LocalContext.current
    val state = controller.menuState()
    val actions = remember(controller) { PdfSelectionActions() }
    SideEffect {
        controller.actionSheetOpen = actions.sheetOpen
        if (controller.selection == null) actions.externalDictionary = null
    }
    fun excerpt(text: String, note: String, page: Int) {
        val book = viewModel.uiState.value.book ?: return
        actions.excerpt(book, text, note, context.getString(R.string.pdf_page_number, page + 1))
        controller.showNotes = false
        controller.clear()
    }
    ConfigurableBackHandler(enabled = controller.selection != null && !controller.editing && !controller.showNotes && !actions.sheetOpen) { actions.externalDictionary = null; controller.clear() }
    fun mark(type: String, slot: Int = 0) {
        val selected = controller.selection ?: return
        viewModel.editPdfAnnotation(selected.model, selected.start, selected.end, type, readerHighlightColorReference(slot, type))
        controller.clear()
    }
    fun color(type: String, slot: Int) {
        val target = (if (type == "underline") state?.overlappingUnderlines else state?.overlappingHighlights)?.firstOrNull()
        if (target == null) mark(type, slot) else {
            viewModel.updateNote(target.copy(color = readerHighlightColorReference(slot, type), updatedAt = System.currentTimeMillis()))
            controller.clear()
        }
    }
    SelectionMenuOverlay(
        state = state.takeUnless { controller.editing || controller.showNotes || actions.sheetOpen || controller.bounds == Rect.Zero },
        readerTheme = "light", glassBackdrop = backdrop, isDragging = controller.dragging,
        reappearKey = controller.menuRevision, selectionMenuItems = menuSettings,
        supportedActions = PdfSelectionSupportedActions,
        showDictionaryAppPicker = actions.externalDictionary != null,
        dictionaryAppOptions = actions.externalDictionary?.apps.orEmpty(),
        onDismiss = { actions.externalDictionary = null; controller.clear() }, onHighlight = { mark("highlight") }, onUnderline = { mark("underline") },
        onNote = controller::beginEdit, onViewNote = controller::beginEdit,
        onCopy = {
            controller.selection?.let { selected ->
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("", selected.text))
            }
            controller.clear()
        },
        onExcerpt = { state?.let { excerpt(it.selectedText, it.existingNote?.note.orEmpty(), it.chapterIndex) } },
        onSearch = { state?.let { actions.search(it.selectedText) }; controller.clear() },
        onWebSearch = { actions.webQuery = state?.selectedText; controller.clear() },
        onDictionary = { actions.dictionary = state?.selectedText },
        onDictionaryAppSelected = { app ->
            val request = actions.externalDictionary
            if (request != null && launchDictionaryLookup(context, request.normalizedText, app)) {
                actions.externalDictionary = null
                controller.clear()
            }
        },
        onChangeHighlightColor = { color("highlight", it) }, onChangeUnderlineColor = { color("underline", it) },
        onChangeUnderlineStyle = { mode -> state?.let { updateUnderlineStyle(viewModel, it, mode) }; controller.clear() },
        onEditHighlightTags = { state?.overlappingHighlights?.firstOrNull()?.let(onEditTags); controller.clear() },
        onEditUnderlineTags = { state?.overlappingUnderlines?.firstOrNull()?.let(onEditTags); controller.clear() },
        onDeleteHighlight = { state?.overlappingHighlights?.firstOrNull()?.let(viewModel::deleteNote); controller.clear() },
        onDeleteUnderline = { state?.overlappingUnderlines?.firstOrNull()?.let(viewModel::deleteNote); controller.clear() },
        onMenuSettings = { DetailActivitySelectionMenu.open(context); controller.clear() }
    )
    NoteInputSheet(
        visible = controller.editing, glassBackdrop = backdrop, keepReaderViewport = true,
        initialText = controller.noteText, onTextChange = { controller.noteText = it },
        tags = controller.noteTags, availableTags = availableTags, onTagsChange = { controller.noteTags = it },
        onCreateTag = viewModel::createAnnotationTag,
        onConfirm = {
            val existing = controller.editingNote
            if (existing != null) viewModel.updateNote(existing.copy(note = controller.noteText,
                tags = controller.noteTags, isNote = true, updatedAt = System.currentTimeMillis()))
            else controller.selection?.let { selected ->
                val locator = selected.locator?.encode()
                if (locator != null) viewModel.addNote(selected.text, controller.noteText, selected.model.page,
                    selected.start, selected.end, readerHighlightColorReference(0, "highlight"), locator, locator,
                    isNote = true, tags = controller.noteTags)
            }
            controller.editing = false
            controller.editingNote = null
            controller.clear()
        },
        onDismiss = { controller.editing = false; controller.editingNote = null; controller.clear() }
    )
    NotesListSheet(visible = controller.showNotes, glassBackdrop = backdrop, usePageLabels = true,
        notes = controller.notes.filterNot { it.isInkStroke }, onNoteClick = onJump,
        onShareNote = { note ->
            excerpt(note.selectedText, note.note, note.chapterIndex)
        }, onEditTags = onEditTags, onDeleteNote = viewModel::deleteNote,
        onDismiss = { controller.showNotes = false })
    PdfSelectionActionSheets(actions, controller, pageCount, backdrop, onJump)
}

private object DetailActivitySelectionMenu {
    fun open(context: Context) {
        context.startActivity(android.content.Intent(context, com.huangder.lumibooks.ui.settings.DetailActivity::class.java)
            .putExtra("category", "selection_menu"))
    }
}
