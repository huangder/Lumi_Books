package com.huangder.lumibooks.ui.bookshelf

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookDeleteMode
import com.huangder.lumibooks.domain.model.DEFAULT_APP_ACCENT_HEX
import com.huangder.lumibooks.ui.components.ConfigurableActivityBack
import com.huangder.lumibooks.ui.components.CloudAwareBookDeleteDialog
import com.huangder.lumibooks.ui.components.EditInputDialog
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassDialogHost
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.home.HomeViewModel
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.effectiveAppTheme
import com.huangder.lumibooks.ui.theme.rememberLiquidGlassCapability
import com.huangder.lumibooks.util.FileUtils
import com.huangder.lumibooks.util.TimeUtils
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import java.io.File
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class BookDetailsActivity : ComponentActivity() {
    companion object {
        const val EXTRA_BOOK_ID = "bookId"
        fun start(context: Context, bookId: String) =
            context.startActivity(Intent(context, BookDetailsActivity::class.java).putExtra(EXTRA_BOOK_ID, bookId))
    }

    @Inject lateinit var dataStoreManager: DataStoreManager

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val bookId = intent.getStringExtra(EXTRA_BOOK_ID).orEmpty()
        setContent {
            val appTheme by dataStoreManager.appTheme.collectAsState(initial = "lumi")
            val accent by dataStoreManager.appAccentColor.collectAsState(initial = DEFAULT_APP_ACCENT_HEX)
            val font by dataStoreManager.globalFontMode.collectAsState(initial = "system")
            val transparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = 0.55f)
            val hdr by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = false)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = "system")
            val eInk by dataStoreManager.eInkModeEnabled.collectAsState(initial = false)
            val predictiveBack by dataStoreManager.predictiveBackEnabled.collectAsState(initial = true)
            val isDark = if (eInk) false else when (darkMode) { "dark" -> true; "light" -> false; else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES }
            val capability = rememberLiquidGlassCapability(eInk, LocalView.current)
            val resolvedTheme = effectiveAppTheme(appTheme, capability)
            EBookReaderTheme(
                darkTheme = isDark,
                dynamicColor = resolvedTheme == "material3",
                appTheme = resolvedTheme,
                appAccentColor = accent,
                liquidGlassTransparency = transparency,
                liquidGlassHdrHighlightEnabled = hdr && !eInk,
                eInkMode = eInk,
                globalFontMode = font
            ) {
                ConfigurableActivityBack(predictiveBackEnabled = predictiveBack, onBack = { finish() })
                val dialogBackdrop = rememberLayerBackdrop()
                val controlsBackdrop = rememberLayerBackdrop()
                val activeDialogBackdrop = dialogBackdrop.takeIf { resolvedTheme == "liquid_glass" }
                val activeControlsBackdrop = controlsBackdrop.takeIf { resolvedTheme == "liquid_glass" }
                LiquidGlassDialogHost(modifier = Modifier.fillMaxSize(), backdrop = activeDialogBackdrop) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(activeDialogBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .then(activeControlsBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                                .background(AppColors.WindowBg)
                        )
                        ProvideLiquidGlassBackdrop(activeControlsBackdrop) {
                            BookDetailsScreen(bookId = bookId, onBack = { finish() }, onOpenReader = { id ->
                                startActivity(Intent(this@BookDetailsActivity, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_BOOK_ID, id))
                                finish()
                            })
                        }
                    }
                }
            }
        }
    }
}

data class BookDetailsUiState(
    val book: Book? = null,
    val totalReadingTime: Long = 0L,
    val activeDays: Int = 0,
    val bookmarkCount: Int = 0,
    val noteCount: Int = 0,
    val isLoading: Boolean = true
)

@dagger.hilt.android.lifecycle.HiltViewModel
class BookDetailsViewModel @Inject constructor(
    private val bookRepository: com.huangder.lumibooks.domain.repository.BookRepository,
    private val readingRepository: com.huangder.lumibooks.domain.repository.ReadingRepository
) : androidx.lifecycle.ViewModel() {
    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(BookDetailsUiState())
    val uiState = _uiState.asStateFlow()
    fun load(bookId: String) {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                bookRepository.getAllBooks(),
                readingRepository.getTotalDurationByBookId(bookId),
                readingRepository.getActiveDaysByBookId(bookId),
                readingRepository.getBookmarksByBookId(bookId),
                readingRepository.getNotesByBookId(bookId)
            ) { books, duration, days, bookmarks, notes -> BookDetailsUiState(books.firstOrNull { it.id == bookId }, duration ?: 0L, days, bookmarks.size, notes.size, false) }
                .collect { _uiState.value = it }
        }
    }
}

@Composable
fun BookDetailsScreen(
    bookId: String,
    onBack: () -> Unit,
    onOpenReader: (String) -> Unit,
    detailsViewModel: BookDetailsViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val details by detailsViewModel.uiState.collectAsState()
    val library by homeViewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var tagsOpen by remember { mutableStateOf(false) }
    var moveOpen by remember { mutableStateOf(false) }
    var coverOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(bookId) { detailsViewModel.load(bookId) }
    val book = details.book ?: return
    val currentFolderId = library.bookFolderLinks.firstOrNull { it.bookId == book.id }?.folderId
    val folderName = currentFolderId?.let { id -> folderPath(library.folders, id).joinToString(" / ") { it.name } }
        ?: stringResource(R.string.library_root)
    val tags = library.bookTagLinks.filter { it.bookId == book.id }.mapNotNull { link -> library.tags.firstOrNull { it.id == link.tagId } }
    var coverTarget by remember { mutableStateOf<Book?>(null) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        coverTarget?.let { target -> if (uri != null) homeViewModel.updateCustomCover(target, uri) }
        coverTarget = null
    }

    Box(Modifier.fillMaxSize().background(AppColors.WindowBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = AppSpace.md, end = AppSpace.md, top = 10.dp, bottom = 124.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpace.md)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LiquidGlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), onBack)
                    Spacer(Modifier.width(AppSpace.sm))
                    Text(stringResource(R.string.book_details), fontSize = AppType.Title, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                }
            }
            item { BookHero(book, folderName, onCover = { coverOpen = true }) }
            item {
                MetricGrid(book, details.totalReadingTime, details.activeDays, details.bookmarkCount, details.noteCount)
            }
            item {
                InfoCard(book, folderName, tags.joinToString { it.name }, onEdit = { editing = true }, onTags = { tagsOpen = true }, onMove = { moveOpen = true })
            }
        }
        FloatingDetailTag(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
            onDelete = { deleteOpen = true }, onEdit = { editing = true }, onMove = { moveOpen = true },
            onNotes = { context.startActivity(Intent(context, BookNotesActivity::class.java).putExtra(BookNotesActivity.EXTRA_BOOK_ID, book.id)) },
            onRead = { onOpenReader(book.id) }
        )
    }

    if (editing) EditBookDialog(book, homeViewModel) { editing = false }
    if (tagsOpen) BookTagBottomSheet(library.tags, library.bookTagLinks.filter { it.bookId == book.id }.map { it.tagId }.toSet(), { tagsOpen = false }, { tag, checked -> homeViewModel.setBookTag(book.id, tag.id, checked) }, { name, parent -> homeViewModel.createAndAssignTag(book.id, name, parent) }, { tag, children -> homeViewModel.deleteTag(tag.id, children) })
    if (moveOpen) FolderMoveSheet(library.folders, 1, currentFolderId, { moveOpen = false }, { name, parent -> homeViewModel.createFolder(name, parent) }) { target -> homeViewModel.moveBooksToFolder(setOf(book.id), target) { if (it) moveOpen = false } }
    if (coverOpen) CustomCoverSourceSheet(book, { coverOpen = false }, { coverOpen = false; coverTarget = book; coverPicker.launch("image/*") }, { coverOpen = false; CoverSearchActivity.start(context, book.id, book.title) })
    if (deleteOpen) CloudAwareBookDeleteDialog(
        bookCount = 1,
        hasRemoteBooks = book.remoteFileName != null,
        onDismiss = { deleteOpen = false },
        onDelete = { mode: BookDeleteMode ->
            homeViewModel.deleteBook(book, mode)
            deleteOpen = false
            onBack()
        }
    )
}

@Composable private fun BookHero(book: Book, folder: String, onCover: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(104.dp, 148.dp).clip(RoundedCornerShape(AppRadius.md)).clickable(onClick = onCover)) {
            if (book.coverPath != null) AsyncImage(ImageRequest.Builder(LocalContext.current).data(book.coverPath).build(), book.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize().background(AppColors.BgGray), Alignment.Center) { Text(book.title.take(6), color = AppColors.TextSecondary, modifier = Modifier.padding(8.dp)) }
        }
        Spacer(Modifier.width(AppSpace.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(book.title, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text("${book.format.name} · ${book.author}", fontSize = AppType.Body, color = AppColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(folder, fontSize = AppType.Caption, color = AppColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(progress = { book.readingProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = AppColors.Accent, trackColor = AppColors.Divider)
            Text("${(book.readingProgress * 100).toInt()}%", fontSize = AppType.Caption, color = AppColors.Accent)
        }
    }
}

@Composable private fun MetricGrid(book: Book, duration: Long, days: Int, bookmarks: Int, notes: Int) {
    val context = LocalContext.current
    val metrics = listOf(
        stringResource(R.string.book_detail_progress) to AnnotatedString("${(book.readingProgress * 100).toInt()}%"),
        stringResource(R.string.book_detail_reading_time) to compactReadingDuration(duration, AppColors.TextSecondary),
        stringResource(R.string.book_detail_notes) to AnnotatedString(notes.toString()),
        stringResource(R.string.book_detail_bookmarks) to AnnotatedString(bookmarks.toString()),
        stringResource(R.string.book_detail_active_days) to AnnotatedString(days.toString()),
        stringResource(R.string.book_detail_file_size) to AnnotatedString(remember(book.filePath, book.remoteFileSize) { fileSize(context, book) })
    )
    LiquidGlassSurface(RoundedCornerShape(AppRadius.lg), AppColors.CardBg, Modifier.fillMaxWidth(), contentScrimColor = Color.Transparent) {
        Column(Modifier.padding(AppSpace.sm)) { metrics.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth()) { row.forEach { (label, value) -> Column(Modifier.weight(1f).padding(AppSpace.sm), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, fontSize = AppType.Section, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary); Text(label, fontSize = AppType.Caption, color = AppColors.TextSecondary) } } } } }
    }
}

@Composable private fun InfoCard(book: Book, folder: String, tags: String, onEdit: () -> Unit, onTags: () -> Unit, onMove: () -> Unit) {
    val context = LocalContext.current
    LiquidGlassSurface(RoundedCornerShape(AppRadius.lg), AppColors.CardBg, Modifier.fillMaxWidth(), contentScrimColor = Color.Transparent) {
        Column {
            DetailRow(stringResource(R.string.book_detail_file_name), remember(book.filePath, book.remoteFileName) { fileName(context, book) }, false)
            DetailRow(stringResource(R.string.book_title_label), book.title, true, onEdit)
            DetailRow(stringResource(R.string.book_author_label), book.author, true, onEdit)
            DetailRow(stringResource(R.string.book_detail_format), book.format.name, false)
            DetailRow(stringResource(R.string.book_detail_folder), folder, true, onMove)
            DetailRow(stringResource(R.string.add_tag), tags.ifBlank { stringResource(R.string.book_detail_none) }, true, onTags)
            DetailRow(
                stringResource(R.string.book_detail_storage),
                when {
                    book.isCloudOnly -> stringResource(R.string.book_detail_cloud_only)
                    book.remoteFileName != null -> stringResource(R.string.book_detail_synced)
                    else -> stringResource(R.string.book_detail_local)
                },
                false
            )
            DetailRow(stringResource(R.string.book_detail_imported), dateText(book.createdAt), false)
            DetailRow(stringResource(R.string.book_detail_last_read), dateText(book.lastReadTime), false)
            LocationDetailRow(book.filePath)
        }
    }
}

@Composable private fun LocationDetailRow(filePath: String) {
    val location = readableBookLocation(
        filePath = filePath,
        internalStorageLabel = stringResource(R.string.book_detail_internal_storage),
        unknownLabel = stringResource(R.string.book_detail_unknown)
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.md, vertical = 14.dp)
    ) {
        Text(
            stringResource(R.string.book_detail_location),
            fontSize = AppType.Body,
            color = AppColors.TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            location,
            modifier = Modifier.fillMaxWidth(),
            fontSize = AppType.BodySmall,
            color = AppColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable private fun DetailRow(label: String, value: String, clickable: Boolean, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = AppSpace.md, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = AppType.Body, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
        Text(value, fontSize = AppType.BodySmall, color = AppColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun FloatingDetailTag(modifier: Modifier, onDelete: () -> Unit, onEdit: () -> Unit, onMove: () -> Unit, onNotes: () -> Unit, onRead: () -> Unit) {
    val isDark = LocalIsDarkTheme.current
    val shape = RoundedCornerShape(32.dp)
    LiquidGlassSurface(
        shape,
        AppColors.CardBg,
        modifier.shadow(
            elevation = 18.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = if (isDark) 0.34f else 0.18f),
            spotColor = Color.Black.copy(alpha = if (isDark) 0.40f else 0.24f)
        ),
        contentScrimColor = if (isDark) Color.Black.copy(alpha = .22f) else Color.White.copy(alpha = .28f)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TagIcon(Icons.Outlined.Delete, stringResource(R.string.delete), onDelete)
            TagIcon(Icons.Outlined.Edit, stringResource(R.string.book_detail_edit), onEdit)
            TagIcon(Icons.Outlined.DriveFileMove, stringResource(R.string.move_to_folder), onMove)
            TagIcon(Icons.Outlined.BookmarkBorder, stringResource(R.string.bookmarks_notes), onNotes)
            ReadTagAction(onRead)
        }
    }
}

@Composable private fun TagIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    val isDark = LocalIsDarkTheme.current
    LiquidGlassIconButton(
        icon,
        description,
        onClick,
        size = 46.dp,
        iconSize = 21.dp,
        contentColor = if (isDark) Color.White else Color.Black,
        normalContainerColor = if (isDark) Color(0xFF202023) else Color(0xFFF6F6F8),
        liquidContainerColor = if (isDark) Color(0xFF171719) else Color(0xFFF7F7F9),
        liquidScrimColor = if (isDark) Color.Black.copy(alpha = .34f) else Color.White.copy(alpha = .62f)
    )
}

@Composable private fun ReadTagAction(onClick: () -> Unit) {
    val isDark = LocalIsDarkTheme.current
    LiquidGlassSurface(
        shape = RoundedCornerShape(23.dp),
        fallbackColor = if (isDark) Color(0xFF202023) else Color(0xFFF6F6F8),
        contentScrimColor = if (isDark) Color.Black.copy(alpha = .34f) else Color.White.copy(alpha = .62f),
        onClick = onClick,
        modifier = Modifier.width(92.dp).height(46.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(
                Icons.Outlined.MenuBook,
                contentDescription = null,
                tint = if (isDark) Color.White else Color.Black,
                modifier = Modifier.size(20.dp)
            )
            Text(
                stringResource(R.string.book_detail_read),
                color = if (isDark) Color.White else Color.Black,
                fontSize = AppType.BodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable private fun EditBookDialog(book: Book, viewModel: HomeViewModel, onDismiss: () -> Unit) {
    LiquidGlassDialog(onDismissRequest = onDismiss, backgroundScrimColor = Color.Transparent) {
        EditInputDialog(stringResource(R.string.edit_book_info), listOf(Triple(stringResource(R.string.book_title_label), "", book.title), Triple(stringResource(R.string.book_author_label), "", book.author)), onDismiss, { values -> viewModel.updateBook(book.copy(title = values.getOrElse(0) { book.title }, author = values.getOrElse(1) { book.author })); onDismiss() })
    }
}

private fun fileSize(context: Context, book: Book): String = when {
    book.remoteFileSize > 0 -> FileUtils.formatFileSize(book.remoteFileSize)
    book.filePath.startsWith("content://") -> queryOpenable(context, book.filePath, OpenableColumns.SIZE)
        ?.toLongOrNull()?.let(FileUtils::formatFileSize) ?: "?"
    else -> runCatching { File(book.filePath).length() }.getOrDefault(0L).takeIf { it > 0 }?.let(FileUtils::formatFileSize) ?: "?"
}

private fun fileName(context: Context, book: Book): String =
    book.remoteFileName?.takeIf { book.isCloudOnly }
        ?: if (book.filePath.startsWith("content://")) {
            queryOpenable(context, book.filePath, OpenableColumns.DISPLAY_NAME)
        } else {
            File(book.filePath).name
        }
        ?: book.filePath.substringAfterLast('/').substringAfterLast('\\')

private fun queryOpenable(context: Context, uriString: String, column: String): String? = runCatching {
    context.contentResolver.query(Uri.parse(uriString), arrayOf(column), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
    }
}.getOrNull()

private fun dateText(timestamp: Long): String = if (timestamp <= 0) "-" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))

private fun readableBookLocation(filePath: String, internalStorageLabel: String, unknownLabel: String): String {
    if (filePath.isBlank()) return unknownLabel

    val decodedPath = Uri.decode(filePath).replace('\\', '/')
    if (decodedPath.startsWith("content://com.android.externalstorage.documents/")) {
        val documentId = decodedPath.substringAfter("/document/", missingDelimiterValue = "")
        if (documentId.startsWith("primary:")) {
            return "$internalStorageLabel/${documentId.substringAfter(':').trimStart('/')}"
        }
    }

    val normalizedPath = decodedPath.removePrefix("file://")
    val storageRoot = listOf("/storage/emulated/0", "/mnt/sdcard", "/sdcard")
        .firstOrNull { normalizedPath == it || normalizedPath.startsWith("$it/") }
    return if (storageRoot != null) {
        internalStorageLabel + normalizedPath.removePrefix(storageRoot)
    } else {
        normalizedPath
    }
}

private fun compactReadingDuration(durationMillis: Long, unitColor: Color): AnnotatedString {
    val totalMinutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(durationMillis)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val values = when {
        hours > 0 -> listOf(hours to "H", minutes to "M")
        minutes > 0 -> listOf(minutes to "M")
        else -> listOf(java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(durationMillis) to "S")
    }
    return buildAnnotatedString {
        values.forEachIndexed { index, (value, unit) ->
            if (index > 0) append(' ')
            append(value.toString())
            withStyle(SpanStyle(fontSize = 10.sp, color = unitColor, fontWeight = FontWeight.Medium)) {
                append(unit)
            }
        }
    }
}
