package com.huangder.lumibooks.ui.bookshare

import com.huangder.lumibooks.ui.icons.ControlIcon

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.huangder.lumibooks.ui.animation.ShimmerBox
import com.huangder.lumibooks.ui.animation.VisualMotion
import com.huangder.lumibooks.ui.animation.lumiPressable
import com.huangder.lumibooks.ui.icons.AppIcons
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.*
import com.huangder.lumibooks.domain.repository.*
import com.huangder.lumibooks.ui.components.*
import com.huangder.lumibooks.ui.excerpt.*
import com.huangder.lumibooks.ui.statistics.*
import com.huangder.lumibooks.ui.theme.*
import com.huangder.lumibooks.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.huangder.lumibooks.ui.animation.finishWithPush

@AndroidEntryPoint
class BookShareActivity : ComponentActivity() {
    @Inject lateinit var manager: DataStoreManager
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(LocaleHelper.applyLanguage(newBase)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { ReadingFeatureTheme(manager) {
            BookSharePage(intent.getStringExtra("date"), onBack = { finishWithPush() })
        } }
    }
    companion object {
        fun start(context: Context, bookId: String, date: LocalDate? = null) {
            context.startActivity(Intent(context, BookShareActivity::class.java).putExtra("bookId", bookId)
                .apply { date?.let { putExtra("date", it.toString()) } })
        }
    }
}

internal data class BookShareInput(val book: Book, val review: String, val rating: Float, val daily: Map<LocalDate, ActivityDuration>)
internal data class BookShareState(val input: BookShareInput? = null, val loading: Boolean = true)
@HiltViewModel
internal class BookShareViewModel @Inject constructor(
    handle: SavedStateHandle, books: BookRepository, repository: ReadingRepository, manager: DataStoreManager
) : ViewModel() {
    private val id = handle.get<String>("bookId").orEmpty()
    val state = combine(books.getAllBooks(), repository.getBookDailyActivity(id), manager.bookReview(id), manager.bookReviewRating(id)) { library, days, review, rating ->
        BookShareState(library.firstOrNull { it.id == id }?.let { BookShareInput(it, review, rating,
            days.associate { readingLocalDate(it.date) to ActivityDuration(it.readingDuration, it.listeningDuration) }) }, false)
    }.catch { emit(BookShareState(loading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookShareState())
}

@Composable private fun BookSharePage(initialDate: String?, onBack: () -> Unit, model: BookShareViewModel = hiltViewModel()) {
    val state by model.state.collectAsState()
    val input = state.input
    val context = LocalContext.current
    var kind by rememberSaveable { mutableStateOf(if (initialDate == null) BookShareKind.REVIEW else BookShareKind.CHECK_IN) }
    var style by rememberSaveable { mutableStateOf(BookShareStyle.SIMPLE) }
    val activeStyle = style.takeIf { it in BookShareEdition.styles } ?: BookShareStyle.SIMPLE
    var dateString by rememberSaveable { mutableStateOf(initialDate ?: LocalDate.now().toString()) }
    val date = runCatching { LocalDate.parse(dateString) }.getOrDefault(LocalDate.now())
    var chooseDate by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val permission = rememberBookShareExportPermission()
    val snapshot = remember(input, date) { input?.let {
        createBookShareSnapshot(it.book, it.review, it.rating, it.daily, date, LocalDate.now())
    } }
    val valid = snapshot?.let { shareHasContent(it, kind) } == true
    val document by key(snapshot, kind, activeStyle, retry) { produceState<ExcerptDocument?>(null) {
        value = null; failed = false
        if (snapshot != null && valid) try {
            value = withContext(Dispatchers.Default) { BookShareRenderer.prepare(context, snapshot, kind, activeStyle) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
        catch (_: OutOfMemoryError) { failed = true }
    } }
    fun export(share: Boolean) {
        val rendered = document ?: return
        if (busy || failed) return
        busy = true
        scope.launch {
            try {
                val uri = withContext(Dispatchers.IO) { ExcerptImages.write(context, rendered, share, "LUMI_book_") }
                if (share) context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, "Lumi", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, context.getString(R.string.insights_share)))
                else Toast.makeText(context, R.string.insights_saved, Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { Toast.makeText(context, R.string.insights_export_failed, Toast.LENGTH_LONG).show() }
            catch (_: OutOfMemoryError) { Toast.makeText(context, R.string.insights_export_failed, Toast.LENGTH_LONG).show() }
            finally { busy = false }
        }
    }
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) permission(activeStyle) { export(false) }
    }
    val pageBackdrop = rememberLayerBackdrop()
    val backdrop = pageBackdrop.takeIf { LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current }
    val canExport = document != null && valid && !busy && !failed
    Box(Modifier.fillMaxSize().background(AppColors.PageBg)) {
        // Capture the background only: the glass controls below must never sample themselves.
        Box(Modifier.fillMaxSize().then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier).background(AppColors.PageBg))
        ProvideLiquidGlassBackdrop(backdrop) {
            CollapsingPageScaffold(
                title = stringResource(R.string.insights_share_card), onBack = onBack, bottomBarOverlay = true,
                pinnedContent = {
                    Column(Modifier.fillMaxWidth().padding(horizontal = AppSpace.md + 4.dp).padding(bottom = AppSpace.sm),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        InsightSegmented(listOf(stringResource(R.string.insights_review_card), stringResource(R.string.insights_check_in)),
                            BookShareKind.entries.indexOf(kind), { kind = BookShareKind.entries[it] }, enabled = !busy)
                        // Equal-width chips spanning the same edges as the segmented control above.
                        if (BookShareEdition.styles.size > 1) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BookShareEdition.styles.forEach { value ->
                                ShareStyleChip(value, value == activeStyle, enabled = !busy, Modifier.weight(1f).fillMaxHeight()) { style = value }
                            }
                        }
                        if (kind == BookShareKind.CHECK_IN) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.insights_select_date), color = AppColors.TextSecondary, fontSize = AppType.Footnote,
                                modifier = Modifier.weight(1f))
                            InsightsCapsuleButton(dayTitle(date), { chooseDate = true }, enabled = !busy, icon = AppIcons.CaretDown)
                        }
                    }
                },
                bottomBar = {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = AppSpace.md + 4.dp, vertical = AppSpace.md),
                        horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)) {
                        LiquidGlassButton(onClick = { permission(activeStyle) { export(true) } }, enabled = canExport, modifier = Modifier.weight(1f)) {
                            ControlIcon(AppIcons.ShareNetwork, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.insights_share), fontWeight = FontWeight.SemiBold)
                            BookShareExportBadge(activeStyle)
                        }
                        LiquidGlassButton(onClick = { permission(activeStyle) {
                            if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                                storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            else export(false)
                        } }, enabled = canExport, modifier = Modifier.weight(1f), tintedColor = AppColors.Accent) {
                            if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = AppColors.OnAccent, strokeWidth = 2.dp)
                            else ControlIcon(AppIcons.DownloadSimple, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.insights_save_image), fontWeight = FontWeight.SemiBold)
                            BookShareExportBadge(activeStyle)
                        }
                    }
                }
            ) {
                val view: ShareView = when {
                    state.loading -> ShareView.Loading
                    input == null -> ShareView.Message(R.string.insights_load_failed)
                    !valid -> ShareView.Message(R.string.insights_no_day_activity)
                    failed -> ShareView.Failed
                    document != null -> ShareView.Ready(document!!)
                    else -> ShareView.Loading
                }
                val body: @Composable (ShareView) -> Unit = { current ->
                    val messageInset = if (current is ShareView.Ready) 0.dp else LocalCollapsingPageBottomInset.current
                    Box(Modifier.fillMaxSize().padding(start = AppSpace.lg, end = AppSpace.lg, bottom = messageInset), Alignment.Center) {
                        when (current) {
                            ShareView.Loading -> Box(Modifier.fillMaxWidth(.82f).aspectRatio(.62f).clip(AppRoundedCornerShape(18.dp))) {
                                if (insightsMotion()) ShimmerBox(Modifier.fillMaxSize()) else Box(Modifier.fillMaxSize().background(AppColors.BgGray))
                            }
                            is ShareView.Message -> EmptyState(stringResource(current.text), icon = AppIcons.Image)
                            ShareView.Failed -> EmptyState(stringResource(R.string.insights_export_failed), icon = AppIcons.ImageBroken,
                                actionLabel = stringResource(R.string.insights_retry), onAction = { retry++ })
                            is ShareView.Ready -> ShareDocumentPreview(current.document, Modifier.fillMaxSize().widthIn(max = 520.dp),
                                contentPadding = PaddingValues(top = 16.dp, bottom = LocalCollapsingPageBottomInset.current + 16.dp)) { failed = true }
                        }
                    }
                }
                if (!insightsMotion()) body(view)
                else AnimatedContent(view, Modifier.fillMaxSize(), transitionSpec = {
                    (fadeIn(VisualMotion.tween(260)) + scaleIn(VisualMotion.spring(dampingRatio = .8f, stiffness = 300f), initialScale = .94f))
                        .togetherWith(fadeOut(VisualMotion.tween(140)) + scaleOut(VisualMotion.tween(140), targetScale = .98f))
                }, contentKey = { (it as? ShareView.Ready)?.document ?: it::class }, label = "sharePreview") { body(it) }
            }
        }
    }
    if (chooseDate) AppBottomSheet({ chooseDate = false }) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = insightsSheetMaxHeight()), contentPadding = insightsSheetPadding()) {
            item { AppSheetHeader(stringResource(R.string.insights_select_date), onClose = { chooseDate = false }) }
            val days = input?.daily.orEmpty().filter { it.value.totalDuration > 0 && it.key <= LocalDate.now() }.keys.sortedDescending()
            if (days.isEmpty()) item { InsightLabel(stringResource(R.string.no_reading_records)) }
            items(days.size) { index ->
                val day = days[index]
                if (index > 0) InsightsDivider(0.dp)
                Row(Modifier.fillMaxWidth().clickable { dateString = day.toString(); chooseDate = false }.padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(dayTitle(day), color = AppColors.TextPrimary, fontSize = AppType.Subhead, modifier = Modifier.weight(1f),
                        fontWeight = if (day == date) FontWeight.SemiBold else FontWeight.Normal)
                    Text(insightsDuration(input!!.daily.getValue(day).totalDuration), color = AppColors.TextSecondary, fontSize = AppType.Footnote)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(18.dp)) { if (day == date) Icon(AppIcons.Check, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

private sealed interface ShareView {
    data object Loading : ShareView
    data object Failed : ShareView
    data class Message(val text: Int) : ShareView
    data class Ready(val document: ExcerptDocument) : ShareView
}

@Composable private fun ShareStyleChip(style: BookShareStyle, selected: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = AppRoundedCornerShape(AppRadius.full)
    val eInk = LocalEInkMode.current
    val accent = if (eInk) Color.Black else AppColors.Accent
    val interaction = remember { MutableInteractionSource() }
    val swatch = when (style) {
        BookShareStyle.SIMPLE -> listOf(Color(0xFFF7F4EE), Color(0xFFE6E0D4))
        BookShareStyle.COVER -> listOf(Color(0xFF4E7165), Color(0xFF2B3F38))
        BookShareStyle.TICKET -> listOf(Color(0xFFF2D7A0), Color(0xFFC9955B))
    }
    Row(modifier.heightIn(min = 40.dp).lumiPressable(interaction, pressedScale = .95f).clip(shape)
        .background(if (selected) accent.copy(alpha = .14f) else AppColors.BgGray)
        .then(if (selected) Modifier.border(1.5.dp, accent, shape) else Modifier)
        .selectable(selected, enabled = enabled, role = Role.RadioButton, interactionSource = interaction, indication = null, onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(Brush.linearGradient(swatch)).border(.5.dp, AppColors.Divider, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(when (style) { BookShareStyle.SIMPLE -> R.string.insights_style_simple; BookShareStyle.COVER -> R.string.insights_style_cover; BookShareStyle.TICKET -> R.string.insights_style_ticket }),
            color = if (selected) accent else AppColors.TextPrimary, fontSize = AppType.BodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (style.premium) Spacer(Modifier.width(4.dp))
        BookShareExportBadge(style)
    }
}
