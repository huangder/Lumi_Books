package com.huangder.lumibooks.ui.bookshelf

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.LumiEasterEgg
import com.huangder.lumibooks.ui.animation.LumiMotion
import com.huangder.lumibooks.ui.animation.VisualMotion
import com.huangder.lumibooks.ui.components.ConfigurableActivityBack
import com.huangder.lumibooks.ui.components.LiquidGlassDialogHost
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.components.LocalLiquidGlassBackdrop
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.icons.directionalIcon
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalAppThemeVariant
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.LumiBackgroundScene
import com.huangder.lumibooks.ui.theme.effectiveAppTheme
import com.huangder.lumibooks.ui.theme.rememberLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt
import javax.inject.Inject

@AndroidEntryPoint
class BookReviewActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_BOOK_ID = "bookId"
        private const val EXTRA_BOOK_TITLE = "bookTitle"
        private const val EXTRA_BOOK_AUTHOR = "bookAuthor"
        private const val EXTRA_BOOK_COVER = "bookCover"

        fun start(context: Context, bookId: String, title: String, author: String, coverPath: String?) {
            context.startActivity(
                Intent(context, BookReviewActivity::class.java)
                    .putExtra(EXTRA_BOOK_ID, bookId)
                    .putExtra(EXTRA_BOOK_TITLE, title)
                    .putExtra(EXTRA_BOOK_AUTHOR, author)
                    .putExtra(EXTRA_BOOK_COVER, coverPath)
            )
        }
    }

    @Inject lateinit var dataStoreManager: DataStoreManager

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val bookId = intent.getStringExtra(EXTRA_BOOK_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_BOOK_TITLE).orEmpty()
        val author = intent.getStringExtra(EXTRA_BOOK_AUTHOR).orEmpty()
        val coverPath = intent.getStringExtra(EXTRA_BOOK_COVER)
        val launchTheme = com.huangder.lumibooks.util.LaunchThemeController.themeSnapshot(this)
        setContent {
            val appTheme by dataStoreManager.appTheme.collectAsState(initial = launchTheme.appTheme)
            val accent by dataStoreManager.appAccentColor.collectAsState(initial = launchTheme.appAccentColor)
            val appFontSize by dataStoreManager.appFontSize.collectAsState(initial = launchTheme.appFontSize)
            val font by dataStoreManager.globalFontMode.collectAsState(initial = launchTheme.globalFontMode)
            val transparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = launchTheme.liquidGlassTransparency)
            val hdr by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = launchTheme.liquidGlassHdrHighlightEnabled)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = launchTheme.darkMode)
            val eInk by dataStoreManager.eInkModeEnabled.collectAsState(initial = launchTheme.eInkModeEnabled)
            val predictiveBack by dataStoreManager.predictiveBackEnabled.collectAsState(initial = launchTheme.predictiveBackEnabled)
            val motion by dataStoreManager.motionPreference.collectAsState(initial = launchTheme.motionPreference)
            val animationStyle by dataStoreManager.menuAnimationStyle.collectAsState(initial = launchTheme.menuAnimationStyle)
            val isDark = if (eInk) false else when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
            val capability = rememberLiquidGlassCapability(eInk, LocalView.current)
            val resolvedTheme = effectiveAppTheme(appTheme, capability)
            val liquidGlassStyle by dataStoreManager.liquidGlassStyle
                .collectAsState(initial = launchTheme.liquidGlassStyle)
            val reduceTransparencyEnabled by dataStoreManager.reduceTransparencyEnabled
                .collectAsState(initial = launchTheme.reduceTransparencyEnabled)
            EBookReaderTheme(
                liquidGlassStyle = liquidGlassStyle,
                reduceTransparencyEnabled = reduceTransparencyEnabled,
                menuAnimationStyle = com.huangder.lumibooks.domain.model.MenuAnimationStyle.fromStoredValue(animationStyle),
                motionPreference = com.huangder.lumibooks.ui.theme.MotionPreference.fromStoredValue(motion),
                darkTheme = isDark,
                dynamicColor = resolvedTheme == "material3",
                appTheme = appTheme,
                lumiBackgroundScene = LumiBackgroundScene.HOME,
                appAccentColor = accent,
                liquidGlassTransparency = transparency,
                liquidGlassHdrHighlightEnabled = hdr && !eInk,
                eInkMode = eInk,
                appFontSize = appFontSize,
                globalFontMode = font
            ) {
                ConfigurableActivityBack(predictiveBackEnabled = predictiveBack, onBack = { finish() })
                // The theme already hosts the Lumi artwork, so this page only adds its own surface.
                Surface(modifier = Modifier.fillMaxSize(), color = AppColors.PageBg) {
                    LiquidGlassDialogHost(modifier = Modifier.fillMaxSize()) {
                        BookReviewScreen(
                            bookId = bookId,
                            bookTitle = title,
                            bookAuthor = author,
                            coverPath = coverPath,
                            dataStoreManager = dataStoreManager,
                            onBack = { finish() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookReviewScreen(
    bookId: String,
    bookTitle: String,
    bookAuthor: String,
    coverPath: String?,
    dataStoreManager: DataStoreManager,
    onBack: () -> Unit
) {
    val storedReview by dataStoreManager.bookReview(bookId).collectAsState(initial = "")
    val storedRating by dataStoreManager.bookReviewRating(bookId).collectAsState(initial = 0f)
    var review by remember(bookId, storedReview) { mutableStateOf(storedReview) }
    var rating by remember(bookId, storedRating) { mutableStateOf(storedRating) }
    // Saving removes a blank review, so blank text counts as "no review".
    val dirty = review.ifBlank { "" } != storedReview || rating != storedRating
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val hasLumiArtwork = LocalAppThemeVariant.current == LumiEasterEgg.THEME
    // The Lumi artwork already provides glass with a backdrop; other glass themes sample this page.
    val ownsBackdrop = isLiquidGlass && !hasLumiArtwork
    val pageBackdrop = rememberLayerBackdrop()
    val (actionColor, onActionColor) = reviewActionColors()

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (ownsBackdrop) Modifier.layerBackdrop(pageBackdrop) else Modifier)
                .background(AppColors.PageBg)
        )
        CompositionLocalProvider(
            LocalLiquidGlassBackdrop provides if (ownsBackdrop) pageBackdrop else LocalLiquidGlassBackdrop.current
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = AppSpace.md, end = AppSpace.md, top = AppSpace.sm, bottom = AppSpace.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidGlassIconButton(
                        directionalIcon(AppIcons.ArrowLeft, AppIcons.ArrowRight),
                        stringResource(R.string.back),
                        onBack
                    )
                    Spacer(Modifier.width(AppSpace.sm))
                    Text(
                        stringResource(R.string.book_review),
                        modifier = Modifier.weight(1f),
                        fontSize = AppType.Title,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(AppSpace.sm))
                    // Lives in the top bar so it stays reachable while the keyboard is open.
                    LiquidGlassTextButton(
                        text = stringResource(R.string.book_review_save),
                        onClick = {
                            focusManager.clearFocus()
                            scope.launch {
                                dataStoreManager.saveBookReview(bookId, review, rating)
                                Toast.makeText(context, R.string.book_review_saved, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = dirty,
                        tintedColor = if (dirty) actionColor else AppColors.BgGray,
                        contentColor = if (dirty) onActionColor else AppColors.TextSecondary
                    )
                }
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .imePadding()
                ) {
                    val editorHeight = (maxHeight * 0.65f).coerceIn(160.dp, 480.dp)
                    Column(
                        Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .padding(start = AppSpace.md, end = AppSpace.md, top = AppSpace.xs, bottom = AppSpace.lg)
                    ) {
                        BookReviewHero(
                            title = bookTitle,
                            author = bookAuthor,
                            coverPath = coverPath,
                            rating = rating,
                            onRatingChange = { rating = it }
                        )
                        Spacer(Modifier.height(AppSpace.md))
                        ReviewEditor(
                            review = review,
                            onReviewChange = { review = it.take(BOOK_REVIEW_MAX_LENGTH) },
                            editorHeight = editorHeight
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookReviewHero(
    title: String,
    author: String,
    coverPath: String?,
    rating: Float,
    onRatingChange: (Float) -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val hasLumiArtwork = LocalAppThemeVariant.current == LumiEasterEgg.THEME
    val headingColor = if (hasLumiArtwork && !isDark) Color(0xFF17353B) else AppColors.TextPrimary
    val headingSubtle = if (hasLumiArtwork && !isDark) Color(0xFF536F76) else AppColors.TextSecondary
    // The Lumi artwork has its own palette and e-ink stays monochrome, so neither gets a cover wash.
    val showWash = !hasLumiArtwork && !LocalEInkMode.current
    val swatches by produceState<CoverSwatches?>(null, coverPath, showWash) {
        value = if (showWash) loadCoverSwatches(context.applicationContext, coverPath) else null
    }
    val shape = AppRoundedCornerShape(if (isLiquidGlass) 20.dp else AppRadius.lg)
    Box(
        modifier = Modifier.fillMaxWidth().clip(shape).background(AppColors.CardBg)
    ) {
        if (showWash) ReviewHeroWash(swatches, shape, Modifier.matchParentSize())
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppSpace.md, end = AppSpace.md, top = AppSpace.lg, bottom = AppSpace.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ReviewCover(title, coverPath)
            Spacer(Modifier.height(AppSpace.md))
            Text(
                title,
                color = headingColor,
                fontSize = AppType.Section,
                lineHeight = AppType.Section * 1.4f,
                fontWeight = FontWeight.Bold,
                fontFamily = resolveAppFontFamily(KaiTi),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (author.isNotBlank()) {
                Spacer(Modifier.height(AppSpace.xs))
                Text(
                    author,
                    color = headingSubtle,
                    fontSize = AppType.BodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(AppSpace.md))
            ReviewRatingBar(rating = rating, onRatingChange = onRatingChange)
            ReviewScoreLine(rating = rating, headingColor = headingColor, subtleColor = headingSubtle)
        }
    }
}

@Composable
private fun ReviewCover(title: String, coverPath: String?) {
    val shape = AppRoundedCornerShape(AppRadius.sm)
    Box(
        Modifier
            .size(104.dp, 148.dp)
            .shadow(10.dp, shape, ambientColor = AppColors.CardShadow, spotColor = AppColors.CardShadow)
            .clip(shape)
            .background(AppColors.BgGray)
            .border(0.5.dp, AppColors.Divider, shape),
        contentAlignment = Alignment.Center
    ) {
        if (coverPath != null) {
            // The title is announced right below, so the cover itself stays silent.
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(coverPath).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                title,
                modifier = Modifier.padding(AppSpace.sm).clearAndSetSemantics {},
                color = AppColors.TextSecondary,
                fontSize = AppType.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = resolveAppFontFamily(KaiTi),
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Visual echo of the score; the rating bar already announces it to TalkBack. */
@Composable
private fun ReviewScoreLine(rating: Float, headingColor: Color, subtleColor: Color) {
    val label = reviewRatingLabel(rating)
    val unit = stringResource(R.string.book_review_score_unit)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 28.dp).clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        if (label == null) {
            Text(stringResource(R.string.book_review_rating_hint), color = subtleColor, fontSize = AppType.Caption)
        } else {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = AppType.Section, fontWeight = FontWeight.Bold, color = headingColor)) {
                        append(formatReviewScore(rating))
                    }
                    withStyle(SpanStyle(fontSize = AppType.Caption, color = subtleColor)) { append(" $unit") }
                    withStyle(SpanStyle(fontSize = AppType.BodySmall, fontWeight = FontWeight.SemiBold, color = headingColor)) {
                        append(" · $label")
                    }
                }
            )
        }
    }
}

/** A tap picks a whole star, a drag keeps tenths, and TalkBack adjusts in half stars. */
@Composable
private fun ReviewRatingBar(rating: Float, onRatingChange: (Float) -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptic = LocalHapticFeedback.current
    val motionEnabled = LocalMotionEnabled.current
    val latestOnChange by rememberUpdatedState(onRatingChange)
    // Gesture callbacks can outrun recomposition, so they compare against the last value sent.
    val lastSent = remember { floatArrayOf(rating) }
    SideEffect { lastSent[0] = rating }
    // Stars only pop for the user's own input, never for the stored rating loading in.
    var interacted by remember { mutableStateOf(false) }

    fun emit(value: Float, forceTick: Boolean = false) {
        val previous = lastSent[0]
        if (value == previous && !forceTick) return
        interacted = true
        lastSent[0] = value
        if (value != previous) latestOnChange(value)
        if (forceTick || ceil(value) != ceil(previous)) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    val ratingLabel = stringResource(R.string.book_review_rating_label)
    val stateText = reviewRatingLabel(rating)
        ?.let { stringResource(R.string.book_review_rating_state, formatReviewScore(rating), it) }
        ?: stringResource(R.string.book_review_unrated)
    Row(
        modifier = Modifier
            .width(240.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = ratingLabel
                stateDescription = stateText
                progressBarRangeInfo = ProgressBarRangeInfo(rating, 0f..5f, steps = 9)
                setProgress { target ->
                    emit(((target * 2f).roundToInt() / 2f).coerceIn(0f, 5f))
                    true
                }
            }
            .pointerInput(rtl) {
                detectTapGestures { offset ->
                    emit(reviewRatingAt(starFraction(offset.x, size.width, rtl), wholeStar = true), forceTick = true)
                }
            }
            .pointerInput(rtl) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        emit(reviewRatingAt(starFraction(offset.x, size.width, rtl), wholeStar = false))
                    }
                ) { change, _ ->
                    change.consume()
                    emit(reviewRatingAt(starFraction(change.position.x, size.width, rtl), wholeStar = false))
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            ReviewStarSlot(fill = (rating - index).coerceIn(0f, 1f), popEnabled = interacted && motionEnabled)
        }
    }
}

private fun starFraction(x: Float, width: Int, rtl: Boolean): Float {
    if (width <= 0) return 0f
    val fraction = (x / width).coerceIn(0f, 1f)
    return if (rtl) 1f - fraction else fraction
}

@Composable
private fun RowScope.ReviewStarSlot(fill: Float, popEnabled: Boolean) {
    val lit = fill >= 1f
    val scale = remember { Animatable(1f) }
    LaunchedEffect(lit) {
        if (lit && popEnabled) {
            scale.snapTo(0.72f)
            scale.animateTo(1f, LumiMotion.GestureSpring)
        } else {
            scale.snapTo(1f)
        }
    }
    Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
        ReviewStar(
            fill = fill,
            emptyColor = reviewStarEmptyColor,
            fillColor = reviewStarFillColor,
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
        )
    }
}

/** Two soft cover-colored glows behind the cover; the brushes are built once per size. */
@Composable
private fun ReviewHeroWash(swatches: CoverSwatches?, shape: Shape, modifier: Modifier) {
    val isDark = LocalIsDarkTheme.current
    val accent = AppColors.Accent
    val motionEnabled = LocalMotionEnabled.current
    val base = reviewAmbientTone(swatches?.base ?: swatches?.glow, accent, isDark)
    val glow = reviewAmbientTone(swatches?.glow ?: swatches?.base, accent, isDark)
    // Fade in once the palette resolves, so the card never flashes the fallback color first.
    val alpha by animateFloatAsState(
        targetValue = if (swatches != null) 1f else 0f,
        animationSpec = VisualMotion.tween(if (motionEnabled) LumiMotion.PageEnterMillis else 100),
        label = "reviewHeroWash"
    )
    Box(
        modifier
            .clip(shape)
            .drawWithCache {
                val wash = Brush.verticalGradient(
                    0f to base.copy(alpha = if (isDark) 0.42f else 0.30f),
                    0.65f to base.copy(alpha = 0f)
                )
                val haloCenter = Offset(size.width / 2f, size.height * 0.27f)
                val haloRadius = size.width * 0.62f
                val halo = Brush.radialGradient(
                    listOf(glow.copy(alpha = if (isDark) 0.36f else 0.32f), glow.copy(alpha = 0f)),
                    center = haloCenter,
                    radius = haloRadius
                )
                onDrawBehind {
                    drawRect(wash, alpha = alpha)
                    drawCircle(halo, haloRadius, haloCenter, alpha = alpha)
                }
            }
    )
}

@Composable
internal fun ReviewEditor(review: String, onReviewChange: (String) -> Unit,
    editorHeight: androidx.compose.ui.unit.Dp = 320.dp) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val (actionColor, _) = reviewActionColors()
    val writingFont = resolveAppFontFamily(KaiTi)
    Surface(
        shape = AppRoundedCornerShape(if (isLiquidGlass) 20.dp else AppRadius.lg),
        color = AppColors.CardBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(AppSpace.md)) {
            BasicTextField(
                value = review,
                onValueChange = onReviewChange,
                // Keep the editor within the viewport. The text field scrolls
                // internally, including when the keyboard is visible.
                modifier = Modifier.fillMaxWidth().heightIn(min = minOf(240.dp, editorHeight), max = editorHeight),
                textStyle = TextStyle(
                    color = AppColors.TextPrimary,
                    fontSize = AppType.Body,
                    fontFamily = writingFont,
                    lineHeight = AppType.Body * 1.7f
                ),
                cursorBrush = SolidColor(actionColor),
                decorationBox = { innerTextField ->
                    if (review.isEmpty()) {
                        Text(
                            stringResource(R.string.book_review_hint),
                            color = AppColors.TextSecondary,
                            fontSize = AppType.Body,
                            fontFamily = writingFont,
                            lineHeight = AppType.Body * 1.7f
                        )
                    }
                    innerTextField()
                }
            )
            Spacer(Modifier.height(AppSpace.sm))
            Text(
                stringResource(R.string.book_review_count, review.length),
                modifier = Modifier.align(Alignment.End),
                // Turns to the action color in the last 10%, before input starts being cut off.
                color = if (review.length >= BOOK_REVIEW_MAX_LENGTH * 9 / 10) actionColor else AppColors.TextSecondary,
                fontSize = AppType.Caption
            )
        }
    }
}
