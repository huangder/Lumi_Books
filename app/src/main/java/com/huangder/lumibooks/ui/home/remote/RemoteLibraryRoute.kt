package com.huangder.lumibooks.ui.home.remote

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.remote.RemoteBookItem
import com.huangder.lumibooks.data.remote.RemoteDestination
import com.huangder.lumibooks.data.remote.RemoteFormat
import com.huangder.lumibooks.data.remote.RemoteSource
import com.huangder.lumibooks.data.remote.RemoteSourceType
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import com.huangder.lumibooks.ui.components.ConfigurableBackHandler
import com.huangder.lumibooks.ui.components.LocalPredictiveBackEnabled
import com.huangder.lumibooks.ui.components.RequestLocalNetworkPermission
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.launch

private enum class RemoteScreen { Sources, Browse }

@Composable
internal fun RemoteLibraryRoute(
    type: RemoteSourceType,
    targetFolderId: String?,
    predictiveBackEnabled: Boolean,
    contentBackdrop: LayerBackdrop?,
    requestLocalNetworkPermission: RequestLocalNetworkPermission,
    onExit: () -> Unit,
    onOpenBookDetails: (RemoteSource, RemoteBookItem) -> Unit,
    viewModel: RemoteLibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()
    var selectionMode by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDownload by remember { mutableStateOf<List<Pair<RemoteBookItem, RemoteFormat>>?>(null) }
    val motionEnabled = LocalMotionEnabled.current
    val eInkMode = LocalEInkMode.current

    LaunchedEffect(type, targetFolderId) { viewModel.start(type, targetFolderId) }
    LaunchedEffect(viewModel) {
        viewModel.batchFinished.collect { summary ->
            Toast.makeText(
                context,
                context.getString(R.string.remote_download_summary, summary.imported, summary.existing, summary.failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val screen = if (state.activeSource != null) RemoteScreen.Browse else RemoteScreen.Sources
    // The editor is its own Activity so it opens with the OEM system transition.
    val openForm: (RemoteSource?) -> Unit = { existing ->
        context.startActivity(RemoteSourceFormActivity.createIntent(context, type, existing))
    }
    val clearSelection = {
        selectionMode = false
        selection = emptySet()
    }
    val goBack: () -> Unit = {
        when {
            selectionMode -> clearSelection()
            !viewModel.navigateBack() -> onExit()
        }
    }
    val pickFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        val items = pendingDownload
        if (uri == null || items == null) return@rememberLauncherForActivityResult
        scope.launch {
            val folder = viewModel.authorizeFolder(uri)
            if (folder == null) {
                Toast.makeText(context, context.getString(R.string.remote_error_destination), Toast.LENGTH_SHORT).show()
            } else {
                viewModel.download(items, RemoteDestination.AuthorizedFolder(folder.treeUri, folder.name))
                pendingDownload = null
                clearSelection()
            }
        }
    }

    CompositionLocalProvider(LocalPredictiveBackEnabled provides predictiveBackEnabled) {
        ConfigurableBackHandler(enabled = screen != RemoteScreen.Sources || selectionMode, onBack = goBack)
    }

    AnimatedContent(
        targetState = screen,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (eInkMode) {
                // E-ink panels ghost on fades; swap the page in one refresh.
                EnterTransition.None togetherWith ExitTransition.None
            } else if (!motionEnabled) {
                fadeIn(tween(90)) togetherWith fadeOut(tween(70))
            } else {
                fadeIn(tween(200, delayMillis = 40)) togetherWith fadeOut(tween(140))
            }
        },
        label = "remoteLibraryScreen"
    ) { target ->
        when (target) {
            RemoteScreen.Sources -> RemoteSourceListScreen(
                type = type,
                sources = state.sources,
                onBack = goBack,
                onAdd = { openForm(null) },
                onOpen = { source ->
                    requestLocalNetworkPermission(source.url, { viewModel.openSource(source) }, {
                        Toast.makeText(context, context.getString(R.string.remote_error_local_network), Toast.LENGTH_SHORT).show()
                    })
                },
                onEdit = { openForm(it) },
                onDelete = viewModel::deleteSource,
                onFeedback = {
                    context.startActivity(android.content.Intent(context, com.huangder.lumibooks.ui.settings.FeedbackActivity::class.java))
                }
            )
            RemoteScreen.Browse -> {
                val books = state.levels.lastOrNull()?.page?.books.orEmpty()
                val activeSource = state.activeSource
                val imageLoader = remember(activeSource?.id) {
                    activeSource?.let { RemoteImageLoaders.create(context, it, viewModel.password(it.id)) }
                }
                CompositionLocalProvider(LocalRemoteImageLoader provides imageLoader) {
                RemoteBrowserScreen(
                    levels = state.levels,
                    searchAvailable = state.levels.firstOrNull()?.page?.searchTemplate != null,
                    searchQuery = state.searchQuery,
                    selectionMode = selectionMode,
                    selection = selection,
                    importedKeys = state.importedKeys,
                    downloadStates = state.downloadStates,
                    layoutMode = state.layoutMode,
                    sortMode = state.sortMode,
                    sortAscending = state.sortAscending,
                    navigatedForward = state.navigatedForward,
                    contentBackdrop = contentBackdrop,
                    onBack = goBack,
                    onSearchOpen = { viewModel.setSearchQuery("") },
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onSearchSubmit = viewModel::submitSearch,
                    onSearchClose = { viewModel.setSearchQuery(null) },
                    onRefresh = viewModel::refresh,
                    onOpenFolder = { folder ->
                        clearSelection()
                        viewModel.openFolder(folder.url, folder.title)
                    },
                    onOpenBook = { book -> state.activeSource?.let { onOpenBookDetails(it, book) } },
                    onToggleBook = { book ->
                        selection = if (book.key in selection) selection - book.key else selection + book.key
                    },
                    onStartSelection = { book ->
                        selectionMode = true
                        selection = selection + book.key
                    },
                    onSelectAll = {
                        val keys = books.filter { it.preferredFormat != null }.mapTo(linkedSetOf()) { it.key }
                        selectionMode = true
                        selection = if (selection.containsAll(keys)) selection - keys else selection + keys
                    },
                    onExitSelection = clearSelection,
                    onSortModeChange = viewModel::setSortMode,
                    onSortAscendingChange = viewModel::setSortAscending,
                    onLayoutModeChange = viewModel::cycleLayoutMode,
                    onLoadMore = viewModel::loadMore,
                    onRetry = viewModel::retry,
                    onDownloadSelected = {
                        val chosen = books.filter { it.key in selection }
                            .mapNotNull { book -> book.preferredFormat?.let { book to it } }
                        if (chosen.isNotEmpty()) pendingDownload = chosen
                    }
                )
                }
            }
        }
    }

    pendingDownload?.let { items ->
        val appTitle = state.libraryFolderName?.let { stringResource(R.string.remote_destination_app_folder, it) }
            ?: stringResource(R.string.remote_destination_app)
        val appDetail = stringResource(R.string.remote_destination_app_detail)
        val folderDetail = stringResource(R.string.remote_destination_folder_detail)
        val pickTitle = stringResource(R.string.remote_destination_pick)
        val pickDetail = stringResource(R.string.remote_destination_pick_detail)
        val finish: (RemoteDestination) -> Unit = { destination ->
            viewModel.download(items, destination)
            pendingDownload = null
            clearSelection()
        }
        RemoteDestinationSheet(
            bookCount = items.size,
            choices = buildList {
                add(DestinationChoice(appTitle, appDetail, AppIcons.Book, primary = true) { finish(viewModel.appDestination()) })
                state.authorizedFolders.forEach { folder ->
                    add(DestinationChoice(folder.name, folderDetail, AppIcons.Folder, primary = false) {
                        finish(RemoteDestination.AuthorizedFolder(folder.treeUri, folder.name))
                    })
                }
                add(DestinationChoice(pickTitle, pickDetail, AppIcons.FolderPlus, primary = false) {
                    runCatching { pickFolderLauncher.launch(null) }
                })
            },
            onDismiss = { pendingDownload = null }
        )
    }
}
