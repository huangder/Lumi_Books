package com.huangder.lumibooks.ui.home.remote

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.remote.RemoteBookItem
import com.huangder.lumibooks.data.remote.RemoteDestination
import com.huangder.lumibooks.data.remote.RemoteSource
import com.huangder.lumibooks.ui.components.ConfigurableActivityBack
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Details of one remote book with its download action. Uses the OEM Activity transition. */
@AndroidEntryPoint
class RemoteBookDetailsActivity : ComponentActivity() {
    private var systemDarkMode by mutableStateOf(false)

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        systemDarkMode = resources.configuration.isNightModeEnabled()
        val source = intent.getStringExtra(EXTRA_SOURCE)
            ?.let { runCatching { RemoteSource.fromJson(JSONObject(it)) }.getOrNull() }
        val item = intent.getStringExtra(EXTRA_ITEM)
            ?.let { runCatching { RemoteBookItem.fromJson(JSONObject(it)) }.getOrNull() }
        if (source == null || item == null) {
            finish()
            return
        }
        val targetFolderId = intent.getStringExtra(EXTRA_TARGET_FOLDER_ID)

        setContent {
            RemoteThemedContent(dataStoreManager, systemDarkMode) { contentBackdrop, predictiveBackEnabled ->
                val viewModel: RemoteBookDetailsViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsState()
                val scope = rememberCoroutineScope()
                var choosingDestination by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { viewModel.start(source, item, targetFolderId) }
                val pickFolderLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree()
                ) { uri: Uri? ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    scope.launch {
                        val folder = viewModel.authorizeFolder(uri)
                        if (folder == null) {
                            Toast.makeText(this@RemoteBookDetailsActivity, getString(R.string.remote_error_destination), Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.download(RemoteDestination.AuthorizedFolder(folder.treeUri, folder.name))
                        }
                    }
                }
                ConfigurableActivityBack(predictiveBackEnabled = predictiveBackEnabled, onBack = { finish() })
                val imageLoader = remember(source.id) {
                    RemoteImageLoaders.create(this@RemoteBookDetailsActivity, source, viewModel.password(source.id))
                }
                androidx.compose.runtime.CompositionLocalProvider(LocalRemoteImageLoader provides imageLoader) {
                RemoteBookDetailsScreen(
                        state = state,
                        contentBackdrop = contentBackdrop,
                        onBack = { finish() },
                        onSelectFormat = viewModel::selectFormat,
                        onReadMetadata = viewModel::readMetadata,
                        onDownload = { choosingDestination = true },
                        onCancel = viewModel::cancelDownload,
                        onRetry = {
                            viewModel.clearFailure()
                            choosingDestination = true
                        },
                        onOpen = { bookId ->
                            startActivity(
                                Intent(this, MainActivity::class.java)
                                    .putExtra(MainActivity.EXTRA_OPEN_BOOK_ID, bookId)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            )
                            finish()
                        }
                    )
                }
                if (choosingDestination) {
                    val appTitle = state.libraryFolderName?.let { stringResource(R.string.remote_destination_app_folder, it) }
                        ?: stringResource(R.string.remote_destination_app)
                    val appDetail = stringResource(R.string.remote_destination_app_detail)
                    val folderDetail = stringResource(R.string.remote_destination_folder_detail)
                    val pickTitle = stringResource(R.string.remote_destination_pick)
                    val pickDetail = stringResource(R.string.remote_destination_pick_detail)
                    RemoteDestinationSheet(
                        bookCount = 1,
                        choices = buildList {
                            add(DestinationChoice(appTitle, appDetail, AppIcons.Book, primary = true) {
                                choosingDestination = false
                                viewModel.download(viewModel.appDestination())
                            })
                            state.authorizedFolders.forEach { folder ->
                                add(DestinationChoice(folder.name, folderDetail, AppIcons.Folder, primary = false) {
                                    choosingDestination = false
                                    viewModel.download(RemoteDestination.AuthorizedFolder(folder.treeUri, folder.name))
                                })
                            }
                            add(DestinationChoice(pickTitle, pickDetail, AppIcons.FolderPlus, primary = false) {
                                choosingDestination = false
                                runCatching { pickFolderLauncher.launch(null) }
                            })
                        },
                        onDismiss = { choosingDestination = false }
                    )
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemDarkMode = newConfig.isNightModeEnabled()
    }

    companion object {
        private const val EXTRA_SOURCE = "remote_book_source"
        private const val EXTRA_ITEM = "remote_book_item"
        private const val EXTRA_TARGET_FOLDER_ID = "remote_book_target_folder"

        /** The source carries no password; the details page reads it from the credential store. */
        fun createIntent(context: Context, source: RemoteSource, item: RemoteBookItem, targetFolderId: String?): Intent =
            Intent(context, RemoteBookDetailsActivity::class.java)
                .putExtra(EXTRA_SOURCE, source.toJson().toString())
                .putExtra(EXTRA_ITEM, item.toJson().toString())
                .putExtra(EXTRA_TARGET_FOLDER_ID, targetFolderId)
    }
}
