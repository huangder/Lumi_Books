package com.huangder.lumibooks.ui.bookshare

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
import androidx.compose.foundation.background
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

@AndroidEntryPoint
class BookShareActivity : ComponentActivity() {
    @Inject lateinit var manager: DataStoreManager
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(LocaleHelper.applyLanguage(newBase)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { ReadingFeatureTheme(manager) {
            BookSharePage(intent.getStringExtra("date"), onBack = ::finish)
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
    val document by produceState<ExcerptDocument?>(null, snapshot, kind, activeStyle, retry) {
        value = null; failed = false
        if (snapshot != null && valid) try {
            value = withContext(Dispatchers.Default) { BookShareRenderer.prepare(context, snapshot, kind, activeStyle) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
        catch (_: OutOfMemoryError) { failed = true }
    }
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
            finally { busy = false }
        }
    }
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) permission(activeStyle) { export(false) }
    }
    CollapsingPageScaffold(
        title = stringResource(R.string.insights_share_card), onBack = onBack,
        modifier = Modifier.fillMaxSize().background(AppColors.PageBg),
        pinnedContent = {
            Column(Modifier.fillMaxWidth().padding(horizontal = AppSpace.md)) {
                Row {
                    BookShareKind.entries.forEach { value ->
                        TextButton(onClick = { kind = value }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Text(stringResource(if (value == BookShareKind.REVIEW) R.string.insights_review_card else R.string.insights_check_in),
                                color = if (kind == value) AppColors.Accent else AppColors.TextSecondary)
                        }
                    }
                }
                Row {
                    BookShareEdition.styles.forEach { value ->
                        TextButton(onClick = { style = value }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Text(stringResource(when (value) { BookShareStyle.SIMPLE -> R.string.insights_style_simple; BookShareStyle.COVER -> R.string.insights_style_cover; BookShareStyle.TICKET -> R.string.insights_style_ticket }),
                                color = if (value == activeStyle) AppColors.Accent else AppColors.TextSecondary)
                        }
                    }
                }
                if (kind == BookShareKind.CHECK_IN) TextButton(onClick = { chooseDate = true }, enabled = !busy) {
                    Text(date.toString() + " · " + stringResource(R.string.insights_select_date), color = AppColors.Accent)
                }
            }
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(AppSpace.md), horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)) {
                LiquidGlassButton(onClick = { permission(activeStyle) {
                    if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                        storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    else export(false)
                } }, enabled = document != null && valid && !busy && !failed, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.insights_save_image))
                }
                LiquidGlassButton(onClick = { permission(activeStyle) { export(true) } },
                    enabled = document != null && valid && !busy && !failed, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.insights_share)) }
            }
        }
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = AppSpace.md), Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator(color = AppColors.Accent)
                input == null -> InsightLabel(stringResource(R.string.insights_load_failed))
                !valid -> InsightLabel(stringResource(R.string.insights_no_day_activity))
                failed -> Column { InsightLabel(stringResource(R.string.insights_export_failed)); TextButton(onClick = { retry++ }) { Text(stringResource(R.string.insights_retry)) } }
                document != null -> ShareDocumentPreview(document!!, Modifier.fillMaxSize()) { failed = true }
                else -> CircularProgressIndicator(color = AppColors.Accent)
            }
        }
    }
    if (chooseDate) AppBottomSheet({ chooseDate = false }) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).navigationBarsPadding(), contentPadding = PaddingValues(AppSpace.md)) {
            item { AppSheetHeader(stringResource(R.string.insights_select_date), onClose = { chooseDate = false }) }
            val days = input?.daily.orEmpty().filter { it.value.totalDuration > 0 && it.key <= LocalDate.now() }.keys.sortedDescending()
            if (days.isEmpty()) item { InsightLabel(stringResource(R.string.no_reading_records)) }
            items(days.size) { index ->
                val day = days[index]
                TextButton(onClick = { dateString = day.toString(); chooseDate = false }) {
                    Text(day.toString() + " · " + activityDurationText(input!!.daily.getValue(day).totalDuration), color = AppColors.TextPrimary)
                }
            }
        }
    }
}
