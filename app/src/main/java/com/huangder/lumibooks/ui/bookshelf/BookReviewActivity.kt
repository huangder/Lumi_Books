package com.huangder.lumibooks.ui.bookshelf

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.LumiEasterEgg
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.components.ConfigurableActivityBack
import com.huangder.lumibooks.ui.components.LiquidGlassDialogHost
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.icons.directionalIcon
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalAppThemeVariant
import com.huangder.lumibooks.ui.theme.LumiBackgroundHost
import com.huangder.lumibooks.ui.theme.LumiBackgroundScene
import com.huangder.lumibooks.ui.theme.effectiveAppTheme
import com.huangder.lumibooks.ui.theme.rememberLiquidGlassCapability
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import javax.inject.Inject

@AndroidEntryPoint
class BookReviewActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_BOOK_ID = "bookId"
        private const val EXTRA_BOOK_TITLE = "bookTitle"
        private const val EXTRA_BOOK_COVER = "bookCover"

        fun start(context: Context, bookId: String, title: String, coverPath: String?) {
            context.startActivity(
                Intent(context, BookReviewActivity::class.java)
                    .putExtra(EXTRA_BOOK_ID, bookId)
                    .putExtra(EXTRA_BOOK_TITLE, title)
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
                LumiBackgroundHost(scene = LumiBackgroundScene.HOME, modifier = Modifier.fillMaxSize()) {
                    Surface(modifier = Modifier.fillMaxSize(), color = AppColors.PageBg) {
                        LiquidGlassDialogHost(modifier = Modifier.fillMaxSize()) {
                            BookReviewScreen(
                                bookId = bookId,
                                bookTitle = title,
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
}

@Composable
private fun BookReviewScreen(
    bookId: String,
    bookTitle: String,
    coverPath: String?,
    dataStoreManager: DataStoreManager,
    onBack: () -> Unit
) {
    val storedReview by dataStoreManager.bookReview(bookId).collectAsState(initial = "")
    val storedRating by dataStoreManager.bookReviewRating(bookId).collectAsState(initial = 0f)
    var review by remember(bookId, storedReview) { mutableStateOf(storedReview) }
    var rating by remember(bookId, storedRating) { mutableStateOf(storedRating) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val isLiquidGlass = com.huangder.lumibooks.ui.theme.LocalAppTheme.current == "liquid_glass"
    val hasLumiArtwork = LocalAppThemeVariant.current == LumiEasterEgg.THEME

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.PageBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = AppSpace.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidGlassIconButton(
                directionalIcon(AppIcons.ArrowLeft, AppIcons.ArrowRight),
                stringResource(R.string.back),
                onBack
            )
            Spacer(Modifier.width(AppSpace.sm))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.book_review), fontSize = AppType.Title, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                if (bookTitle.isNotBlank()) {
                    Text(bookTitle, fontSize = AppType.Caption, color = AppColors.TextSecondary, maxLines = 1)
                }
            }
        }

        BookReviewHeader(bookTitle, coverPath, hasLumiArtwork)

        LiquidGlassSurface(
            shape = com.huangder.lumibooks.ui.theme.AppRoundedCornerShape(if (isLiquidGlass) 20.dp else AppRadius.md),
            fallbackColor = AppColors.CardBg,
            contentScrimColor = if (isDark) AppColors.CardBg.copy(alpha = 0.68f) else AppColors.CardBg.copy(alpha = 0.82f),
            modifier = Modifier.fillMaxWidth().padding(top = AppSpace.md),
            controlEdge = true
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpace.sm, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.book_review_rating_label),
                    modifier = Modifier.padding(start = AppSpace.sm, end = AppSpace.xs),
                    color = AppColors.TextPrimary,
                    fontSize = AppType.BodySmall,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    Modifier
                        .weight(1f)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                fun setRating(x: Float) {
                                    rating = ((x / size.width).coerceIn(0f, 1f) * 50f).roundToInt() / 10f
                                }
                                setRating(down.position.x)
                                var pointer = down
                                do {
                                    val event = awaitPointerEvent()
                                    pointer = event.changes.firstOrNull { it.id == pointer.id } ?: event.changes.first()
                                    setRating(pointer.position.x)
                                    pointer.consume()
                                } while (event.changes.any { it.pressed })
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(5) { index ->
                        val fill = (rating - index).coerceIn(0f, 1f)
                        Box(Modifier.weight(1f).height(42.dp), contentAlignment = Alignment.Center) {
                            ReviewStar(fill = fill, emptyColor = AppColors.Divider, fillColor = Color(0xFFFFB938))
                        }
                    }
                }
                Spacer(Modifier.width(AppSpace.sm))
                Text(
                    text = String.format(java.util.Locale.ROOT, "%.1f", rating) + " " + stringResource(R.string.book_review_score_unit),
                    color = AppColors.TextPrimary,
                    fontSize = AppType.Section,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Text(
            stringResource(R.string.book_review),
            modifier = Modifier.padding(start = AppSpace.sm, top = AppSpace.md, bottom = AppSpace.xs),
            color = AppColors.TextPrimary,
            fontSize = AppType.Body,
            fontWeight = FontWeight.Medium
        )
        LiquidGlassSurface(
            shape = com.huangder.lumibooks.ui.theme.AppRoundedCornerShape(if (isLiquidGlass) 20.dp else AppRadius.md),
            fallbackColor = AppColors.CardBg,
            contentScrimColor = if (isDark) AppColors.CardBg.copy(alpha = 0.68f) else AppColors.CardBg.copy(alpha = 0.82f),
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = AppSpace.md),
            controlEdge = true
        ) {
            Box(Modifier.fillMaxSize().padding(AppSpace.md)) {
                BasicTextField(
                    value = review,
                    onValueChange = { review = it.take(2000) },
                    modifier = Modifier.fillMaxSize().padding(bottom = AppSpace.md).verticalScroll(rememberScrollState()),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = AppColors.TextPrimary,
                        fontSize = AppType.Body,
                        lineHeight = AppType.Body * 1.55f
                    ),
                    cursorBrush = SolidColor(AppColors.Accent),
                    decorationBox = { innerTextField ->
                        if (review.isEmpty()) {
                            Text(stringResource(R.string.book_review_hint), color = AppColors.TextSecondary, fontSize = AppType.Body)
                        }
                        innerTextField()
                    }
                )
                Text(
                    stringResource(R.string.book_review_count, review.length),
                    modifier = Modifier.align(Alignment.BottomEnd),
                    color = AppColors.TextSecondary,
                    fontSize = AppType.Caption
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = AppSpace.md), contentAlignment = Alignment.CenterEnd) {
            LiquidGlassTextButton(
                text = stringResource(R.string.book_review_save),
                onClick = {
                    scope.launch {
                        dataStoreManager.saveBookReview(bookId, review, rating)
                        Toast.makeText(context, R.string.book_review_saved, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.width(144.dp).heightIn(min = 52.dp),
                tintedColor = if (hasLumiArtwork) Color(0xFF52776F) else AppColors.Accent,
                contentColor = if (hasLumiArtwork) Color.White else AppColors.OnAccent,
                prominentShadow = true
            )
        }
    }
}

@Composable
private fun BookReviewHeader(title: String, coverPath: String?, hasLumiArtwork: Boolean) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val isLiquidGlass = com.huangder.lumibooks.ui.theme.LocalAppTheme.current == "liquid_glass"
    val headingColor = if (hasLumiArtwork && !isDark) Color(0xFF17353B) else AppColors.TextPrimary
    val headingSubtle = if (hasLumiArtwork && !isDark) Color(0xFF536F76) else AppColors.TextSecondary
    Box(
        Modifier.fillMaxWidth().height(194.dp).clip(com.huangder.lumibooks.ui.theme.AppRoundedCornerShape(if (isLiquidGlass) 20.dp else AppRadius.md))
    ) {
        LiquidGlassSurface(
            shape = com.huangder.lumibooks.ui.theme.AppRoundedCornerShape(if (isLiquidGlass) 20.dp else AppRadius.md),
            fallbackColor = AppColors.CardBg,
            contentScrimColor = AppColors.CardBg.copy(alpha = if (isDark) 0.72f else 0.64f),
            modifier = Modifier.matchParentSize(),
            controlEdge = true
        ) {}
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.width(82.dp).height(126.dp).clip(com.huangder.lumibooks.ui.theme.AppRoundedCornerShape(AppRadius.sm)).background(AppColors.BgGray),
                contentAlignment = Alignment.Center
            ) {
                if (coverPath != null) {
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(context).data(coverPath).crossfade(true).build(),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(title.take(6), color = AppColors.TextSecondary, modifier = Modifier.padding(6.dp))
                }
            }
            Spacer(Modifier.width(AppSpace.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = headingColor, fontSize = AppType.Section, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(stringResource(R.string.book_review_subtitle), color = headingSubtle, fontSize = AppType.BodySmall)
            }
        }
    }
}

@Composable
private fun ReviewStar(fill: Float, emptyColor: Color, fillColor: Color) {
    Canvas(Modifier.width(28.dp).height(28.dp)) {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = size.minDimension * 0.48f
        val inner = outer * 0.45f
        for (point in 0 until 10) {
            val radius = if (point % 2 == 0) outer else inner
            val angle = -PI / 2.0 + point * PI / 5.0
            val x = cx + cos(angle).toFloat() * radius
            val y = cy + sin(angle).toFloat() * radius
            if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, emptyColor)
        if (fill > 0f) {
            clipRect(right = size.width * fill.coerceIn(0f, 1f)) { drawPath(path, fillColor) }
        }
    }
}
