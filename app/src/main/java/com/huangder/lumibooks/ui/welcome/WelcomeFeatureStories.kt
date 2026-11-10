package com.huangder.lumibooks.ui.welcome

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.brand.LumiCodename
import com.huangder.lumibooks.ui.brand.LumiDawnArrowButton
import com.huangder.lumibooks.ui.brand.LumiDawnColors
import com.huangder.lumibooks.ui.brand.LumiPageIndicator
import com.huangder.lumibooks.ui.brand.LumiPaperCard
import com.huangder.lumibooks.ui.brand.loopWindow
import com.huangder.lumibooks.ui.brand.lumiDawnColors
import com.huangder.lumibooks.ui.brand.lumiDawnMotion
import com.huangder.lumibooks.ui.brand.lumiReveal
import com.huangder.lumibooks.ui.brand.rememberLumiReveal
import com.huangder.lumibooks.ui.brand.shiftHue
import com.huangder.lumibooks.ui.components.ConfigurableBackHandler
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** The three headline updates of this release, in the order they are told. */
internal enum class WelcomeStory { JOURNAL, LISTENING, STATS }

private val StoryHighlight = Color(0xFFFFE08A)

/** Illustration colours, all derived from the user's accent so the art matches the glow. */
private class StoryPalette(colors: LumiDawnColors, ink: Boolean) {
    val primary = if (ink) Color.Black else colors.accent
    val secondary = if (ink) Color.Black else colors.accent.shiftHue(38f, saturation = 0.9f, value = 1.05f)
    val tertiary = if (ink) Color.Black else colors.accent.shiftHue(-42f, saturation = 0.85f, value = 1.02f)
}

/**
 * One welcome step holding three swipeable "stories". Each story is a drawn illustration on
 * a paper card above a short title; the illustrations loop while their page is current and
 * rest on a complete frame when motion is off.
 */
@Composable
internal fun WelcomeFeatureStoriesPage(
    hasReadingJournal: Boolean,
    onContinue: () -> Unit,
    initialStory: Int = 0
) {
    val stories = WelcomeStory.entries
    val pager = rememberPagerState(initialPage = initialStory.coerceIn(0, stories.lastIndex)) { stories.size }
    val scope = rememberCoroutineScope()
    val colors = lumiDawnColors()
    val motion = lumiDawnMotion()
    val lastPage = pager.currentPage == stories.lastIndex

    ConfigurableBackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1, animationSpec = StoryPageTurn) }
    }

    val chromeIn = rememberLumiReveal(index = 3)
    Box(Modifier.fillMaxSize().testTag("welcome-feature-stories")) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(top = 12.dp, bottom = 12.dp)
                .widthIn(max = WelcomeStoriesMaxWidth)
                .align(Alignment.TopCenter)
        ) {
            // Top: "Filmy" (the landing spot of the intro's wordmark) and skip.
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    LumiCodename.CURRENT,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.02).em,
                    color = colors.ink,
                    modifier = Modifier.welcomeCodename().semantics { invisibleToUser() }
                )
                Spacer(Modifier.weight(1f))
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount = 1,
                // A soft, critically damped settle for swipes: no overshoot, no snap.
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pager,
                    snapAnimationSpec = spring(dampingRatio = 1f, stiffness = 260f)
                )
            ) { page ->
                val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
                StoryPage(
                    story = stories[page],
                    index = page,
                    offset = offset,
                    active = pager.currentPage == page && motion,
                    hasReadingJournal = hasReadingJournal
                )
            }

            // Bottom: progress bars and the single action.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 24.dp, top = 16.dp, bottom = 20.dp)
                    .lumiReveal(chromeIn, 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LumiPageIndicator(
                    count = stories.size,
                    position = pager.currentPage + pager.currentPageOffsetFraction,
                    modifier = Modifier.semantics { invisibleToUser() }
                )
                Spacer(Modifier.weight(1f))
                LumiDawnArrowButton(
                    label = stringResource(if (lastPage) R.string.welcome_continue else R.string.welcome_next),
                    expanded = true,
                    onClick = {
                        if (lastPage) onContinue()
                        else scope.launch { pager.animateScrollToPage(pager.currentPage + 1, animationSpec = StoryPageTurn) }
                    },
                    modifier = Modifier.testTag("welcome-story-next")
                )
            }
        }
    }
}

internal val WelcomeStoriesMaxWidth = 560.dp

/** Button-driven page turn: an ease-out curve long enough to read as one continuous move. */
private val StoryPageTurn = tween<Float>(620, easing = com.huangder.lumibooks.ui.animation.AppEasing.Smooth)

@Composable
private fun StoryPage(
    story: WelcomeStory,
    index: Int,
    offset: Float,
    active: Boolean,
    hasReadingJournal: Boolean
) {
    val colors = lumiDawnColors()
    val palette = StoryPalette(colors, LocalEInkMode.current)
    val showJournal = hasReadingJournal || story != WelcomeStory.JOURNAL
    val (eyebrow, title, subtitle) = when (story) {
        WelcomeStory.JOURNAL -> if (showJournal) Triple(
            R.string.welcome_story_journal_eyebrow, R.string.welcome_story_journal_title, R.string.welcome_story_journal_subtitle
        ) else Triple(
            R.string.welcome_story_insights_eyebrow, R.string.welcome_story_insights_title, R.string.welcome_story_insights_subtitle
        )
        WelcomeStory.LISTENING -> Triple(
            R.string.welcome_story_listening_eyebrow, R.string.welcome_story_listening_title, R.string.welcome_story_listening_subtitle
        )
        WelcomeStory.STATS -> Triple(
            R.string.welcome_story_stats_eyebrow, R.string.welcome_story_stats_title, R.string.welcome_story_stats_subtitle
        )
    }
    val artIn = rememberLumiReveal(index = 0)
    val textIn = rememberLumiReveal(index = 1)
    val distance = abs(offset).coerceAtMost(1f)

    // Clip to the page: the parallax shift must never let a neighbour's card show at rest
    // (very visible once the cards are translucent glass).
    Column(Modifier.fillMaxSize().clipToBounds()) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val cardHeight = min(maxHeight.value, 500f).dp
            LumiPaperCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .graphicsLayer {
                        // Each card stays inside its own page (no overlap, so no layer swap);
                        // depth comes from a gentle scale and fade as it leaves or arrives.
                        val s = 1f - 0.08f * distance
                        scaleX = s * (0.94f + 0.06f * artIn)
                        scaleY = s * (0.94f + 0.06f * artIn)
                        alpha = artIn * (1f - 0.55f * distance)
                    }
                    .semantics { invisibleToUser() }
            ) {
                // Restart the loop from its first frame whenever the page becomes current.
                key(active) {
                    val progress = rememberStoryLoop(active, story)
                    when (story) {
                        // The photo fills the card's width; the drawn stories keep a design size.
                        WelcomeStory.LISTENING -> ListeningPhotoArt(progress)
                        WelcomeStory.JOURNAL -> FitArt { JournalStoryArt(progress, showJournal, colors, palette) }
                        WelcomeStory.STATS -> FitArt { StatsStoryArt(progress, colors, palette) }
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, top = 20.dp)
                .lumiReveal(textIn)
                .graphicsLayer { alpha = textIn * (1f - (distance * 1.4f).coerceAtMost(1f)) }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "0${index + 1}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.accent
                )
                Box(Modifier.padding(horizontal = 10.dp).width(18.dp).height(1.dp).background(colors.inkTertiary))
                Text(
                    stringResource(eyebrow),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.04.em,
                    color = colors.inkSecondary
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(title),
                fontSize = 26.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                color = colors.ink,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(subtitle),
                fontSize = 15.sp,
                lineHeight = 23.sp,
                color = colors.inkSecondary
            )
        }
    }
}

/** Per-story loop length and the frame shown when nothing animates. */
private fun WelcomeStory.loopMillis(): Int = when (this) {
    WelcomeStory.JOURNAL -> 3_800
    WelcomeStory.LISTENING -> 5_200
    WelcomeStory.STATS -> 4_000
}

private fun WelcomeStory.restingFrame(): Float = when (this) {
    WelcomeStory.JOURNAL -> 0.86f
    WelcomeStory.LISTENING -> 0.5f
    WelcomeStory.STATS -> 0.88f
}

@Composable
private fun rememberStoryLoop(active: Boolean, story: WelcomeStory): Float {
    if (!active) return story.restingFrame()
    val transition = rememberInfiniteTransition(label = "story-${story.name}")
    val value by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(story.loopMillis(), easing = LinearEasing), RepeatMode.Restart),
        label = "storyLoop"
    )
    return value
}

private val ArtDesignWidth = 300.dp
private val ArtDesignHeight = 384.dp

/** Lays the illustration out at a fixed design size and scales it into the card. */
@Composable
private fun FitArt(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val scale = min(maxWidth / ArtDesignWidth, maxHeight / ArtDesignHeight).coerceIn(0.5f, 1.25f)
        Box(
            Modifier
                .requiredSize(ArtDesignWidth, ArtDesignHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) { content() }
    }
}

/** 0 → 1 for the loop's closing fade, so every cycle restarts from a clean page. */
private fun loopFadeOut(p: Float) = 1f - loopWindow(p, 0.93f, 1f)

// ---------------------------------------------------------------------------------------------
// 01 · Reading journal (or, without the journal, reading insights)
// ---------------------------------------------------------------------------------------------

@Composable
private fun JournalStoryArt(p: Float, journal: Boolean, colors: LumiDawnColors, palette: StoryPalette) {
    val fade = loopFadeOut(p)
    val ring = loopWindow(p, 0.48f, 0.80f)
    val today = remember { LocalDate.now() }
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.welcome_story_art_today), fontSize = 13.sp, color = colors.inkSecondary)
                Text(
                    "${today.monthValue}.${today.dayOfMonth.toString().padStart(2, '0')}",
                    fontSize = 34.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.ink
                )
            }
            Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 6.dp.toPx()
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(colors.hairline, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    drawArc(
                        brush = Brush.sweepGradient(listOf(palette.primary, palette.secondary, palette.primary)),
                        startAngle = -90f,
                        sweepAngle = 360f * 0.72f * ring * fade,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(72 * ring).toInt()}%",
                        fontSize = 16.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.ink.copy(alpha = fade)
                    )
                    Text(
                        stringResource(R.string.welcome_story_art_insight_goal),
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        color = colors.inkSecondary.copy(alpha = fade),
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(22.dp))

        val entries = if (journal) listOf(
            Triple("08:30", stringResource(R.string.welcome_story_art_journal_started), palette.secondary),
            Triple("12:45", stringResource(R.string.welcome_story_art_journal_highlight), palette.primary),
            Triple("22:10", stringResource(R.string.welcome_story_art_journal_chapter), palette.tertiary)
        ) else listOf(
            Triple("", stringResource(R.string.welcome_story_art_insight_today), palette.secondary),
            Triple("", stringResource(R.string.welcome_story_art_insight_streak), palette.primary),
            Triple("", stringResource(R.string.welcome_story_art_insight_peak), palette.tertiary)
        )
        val line = loopWindow(p, 0.02f, 0.50f) * fade
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Canvas(Modifier.fillMaxHeight().width(14.dp)) {
                val x = size.width / 2f
                drawLine(colors.hairline, Offset(x, 6.dp.toPx()), Offset(x, size.height), 2.dp.toPx(), cap = StrokeCap.Round)
                drawLine(
                    colors.inkTertiary.copy(alpha = 0.7f),
                    Offset(x, 6.dp.toPx()),
                    Offset(x, 6.dp.toPx() + (size.height - 6.dp.toPx()) * line),
                    2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                entries.forEachIndexed { i, (time, label, tint) ->
                    val appear = loopWindow(p, 0.06f + 0.14f * i, 0.24f + 0.14f * i) * fade
                    JournalEntry(time, label, tint, highlighted = journal && i == 1, appear = appear, colors = colors)
                }
            }
        }
    }
}

@Composable
private fun JournalEntry(
    time: String,
    label: String,
    tint: Color,
    highlighted: Boolean,
    appear: Float,
    colors: LumiDawnColors
) {
    val ink = LocalEInkMode.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(14.dp)
                .graphicsLayer { scaleX = appear; scaleY = appear }
                .background(tint, CircleShape)
                .border(2.5.dp, Color.White, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .graphicsLayer {
                    alpha = appear
                    translationX = (1f - appear) * -18.dp.toPx()
                }
                .clip(RoundedCornerShape(16.dp))
                .background(if (ink) Color.White else colors.base.copy(alpha = 0.9f))
                .border(1.dp, colors.hairline, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (time.isNotEmpty()) {
                Text(time, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, color = colors.inkTertiary)
                Spacer(Modifier.height(3.dp))
            }
            Text(label, fontSize = 14.sp, lineHeight = 19.sp, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (highlighted) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.width(54.dp).height(6.dp).background(colors.hairline, RoundedCornerShape(3.dp)))
                    Box(
                        Modifier.width(80.dp * appear).height(6.dp)
                            .background(if (ink) Color.Black else StoryHighlight, RoundedCornerShape(3.dp))
                    )
                    Box(Modifier.width(30.dp).height(6.dp).background(colors.hairline, RoundedCornerShape(3.dp)))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 02 · Full-screen listening + floating subtitles
// ---------------------------------------------------------------------------------------------

/**
 * A real capture of the full-screen listening page, cropped to its upper part. A diffused
 * gradient title sits in the top of the card; the phone rises over its lower edge so the two
 * layers overlap, then drifts gently.
 */
@Composable
private fun ListeningPhotoArt(p: Float) {
    val ink = LocalEInkMode.current
    val rise = loopWindow(p, 0f, 0.16f)
    val titleIn = loopWindow(p, 0.04f, 0.22f)
    val drift = sin(p * 2f * PI.toFloat())
    val title = stringResource(R.string.welcome_story_listening_eyebrow)
    val titleSize = if (title.length > 8) 30.sp else 46.sp
    val titleBrush = if (ink) Brush.linearGradient(listOf(Color.Black, Color.Black))
    else Brush.linearGradient(listOf(Color(0xFF8C6CF2), Color(0xFFD36DC0), Color(0xFFF3A07C)))
    val titleStyle = ComposeTextStyle(brush = titleBrush, fontSize = titleSize, fontWeight = FontWeight.Black, letterSpacing = 0.04.em)
    Box(Modifier.fillMaxSize()) {
        if (!ink) {
            // Diffused colour: a violet bloom behind the title and a warm one at the corner.
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Brush.radialGradient(
                        0f to Color(0xFFB27BE6).copy(alpha = 0.34f),
                        1f to Color(0x00B27BE6),
                        center = Offset(size.width * 0.38f, size.height * 0.16f),
                        radius = size.width * 0.70f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        0f to Color(0xFFF4A38A).copy(alpha = 0.26f),
                        1f to Color(0x00F4A38A),
                        center = Offset(size.width * 0.92f, size.height * 0.08f),
                        radius = size.width * 0.55f
                    )
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val aspect = 900f / 1189f
            // Leave room above the phone for the title; the phone always meets the bottom edge.
            val photoWidth = min((maxWidth - 44.dp).value, ((maxHeight - 70.dp).value * aspect)).dp
            val photoHeight = photoWidth / aspect
            val overlap = 16.dp
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = photoHeight - overlap)
                    .graphicsLayer {
                        alpha = titleIn
                        translationY = (1f - titleIn) * 10.dp.toPx()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(title, style = titleStyle, maxLines = 1)
            }
            Image(
                painter = painterResource(R.drawable.welcome_story_listening),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(photoWidth)
                    .height(photoHeight)
                    .graphicsLayer {
                        translationY = (1f - rise) * 36.dp.toPx() + (1f + drift) * 2.dp.toPx()
                        alpha = 0.2f + 0.8f * rise
                        rotationZ = drift * 0.4f
                    }
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 03 · Statistics, renewed
// ---------------------------------------------------------------------------------------------

private val WeekBars = listOf(0.42f, 0.66f, 0.34f, 0.92f, 0.55f, 0.78f, 0.61f)

/** 7 weekdays × 13 weeks of made-up activity, 0 (none) to 4 (most). */
private val HeatLevels = List(7 * 13) { i ->
    val r = i / 13
    val c = i % 13
    val v = (r * 5 + c * 3 + r * c + c / 4) % 7
    if (v > 4) v - 3 else v
}

@Composable
private fun StatsStoryArt(p: Float, colors: LumiDawnColors, palette: StoryPalette) {
    val ink = LocalEInkMode.current
    val fade = loopFadeOut(p)
    val count = loopWindow(p, 0.04f, 0.42f)
    val minutes = (756 * count).toInt()
    val locale = Locale.getDefault()
    val dayNames = remember(locale) {
        DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, locale) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(22.dp)
            .graphicsLayer { alpha = 0.25f + 0.75f * fade }
    ) {
        Text(stringResource(R.string.welcome_story_art_stats_week), fontSize = 13.sp, color = colors.inkSecondary)
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.welcome_story_art_stats_duration, minutes / 60, minutes % 60),
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Black,
            color = colors.ink,
            maxLines = 1
        )
        Spacer(Modifier.height(6.dp))
        val chip = loopWindow(p, 0.38f, 0.50f)
        Text(
            stringResource(R.string.welcome_story_art_stats_compare),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (ink) Color.Black else palette.primary,
            modifier = Modifier
                .graphicsLayer { alpha = chip; translationY = (1f - chip) * 6.dp.toPx() }
                .background(if (ink) Color.White else palette.primary.copy(alpha = 0.12f), RoundedCornerShape(50))
                .then(if (ink) Modifier.border(1.dp, Color.Black, RoundedCornerShape(50)) else Modifier)
                .padding(horizontal = 9.dp, vertical = 3.dp)
        )

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().height(104.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            WeekBars.forEachIndexed { i, value ->
                val grow = loopWindow(p, 0.08f + 0.05f * i, 0.36f + 0.05f * i)
                val peak = i == 3
                val tint = if (peak) palette.secondary else palette.primary
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .width(18.dp)
                            .height(80.dp * value * grow)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Brush.verticalGradient(listOf(tint.copy(alpha = if (ink) 1f else 0.55f), tint)))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(dayNames[i], fontSize = 11.sp, lineHeight = 14.sp, color = if (peak) colors.ink else colors.inkTertiary)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.welcome_story_art_stats_heatmap), fontSize = 12.sp, color = colors.inkSecondary)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val cols = 13
            val rows = 7
            val gap = 3.dp.toPx()
            val cell = min((size.width - gap * (cols - 1)) / cols, (size.height - gap * (rows - 1)) / rows)
            val totalWidth = cell * cols + gap * (cols - 1)
            val startX = (size.width - totalWidth) / 2f
            for (r in 0 until rows) for (c in 0 until cols) {
                val level = HeatLevels[r * cols + c]
                val appear = loopWindow(p, 0.34f + (r + c) * 0.016f, 0.46f + (r + c) * 0.016f)
                val color = if (level == 0) colors.hairline else palette.primary.copy(alpha = (0.18f + 0.2f * level) * appear)
                val s = cell * (0.6f + 0.4f * appear)
                val inset = (cell - s) / 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(startX + c * (cell + gap) + inset, r * (cell + gap) + inset),
                    size = Size(s, s),
                    cornerRadius = CornerRadius(s * 0.3f)
                )
            }
        }
    }
}
