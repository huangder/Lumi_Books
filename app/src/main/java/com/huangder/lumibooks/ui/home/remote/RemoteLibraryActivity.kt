package com.huangder.lumibooks.ui.home.remote

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.remote.RemoteSourceType
import com.huangder.lumibooks.data.sync.LocalNetworkAccessManager
import com.huangder.lumibooks.ui.components.rememberLocalNetworkPermissionGate
import com.huangder.lumibooks.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Remote library (WebDAV / OPDS) — source list, source editor and catalog browser in one
 * Activity. Like the folder books page it relies on the OEM system Activity transition.
 */
@AndroidEntryPoint
class RemoteLibraryActivity : ComponentActivity() {
    private var systemDarkMode by mutableStateOf(false)

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var localNetworkAccessManager: LocalNetworkAccessManager

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        systemDarkMode = resources.configuration.isNightModeEnabled()
        val type = intent.getStringExtra(EXTRA_TYPE)
            ?.let { runCatching { RemoteSourceType.valueOf(it) }.getOrNull() }
            ?: RemoteSourceType.OPDS
        val targetFolderId = intent.getStringExtra(EXTRA_TARGET_FOLDER_ID)

        setContent {
            RemoteThemedContent(dataStoreManager, systemDarkMode) { contentBackdrop, predictiveBackEnabled ->
                val requestLocalNetworkPermission = rememberLocalNetworkPermissionGate(localNetworkAccessManager)
                RemoteLibraryRoute(
                    type = type,
                    targetFolderId = targetFolderId,
                    predictiveBackEnabled = predictiveBackEnabled,
                    contentBackdrop = contentBackdrop,
                    requestLocalNetworkPermission = requestLocalNetworkPermission,
                    onExit = { finish() },
                    onOpenBookDetails = { source, item ->
                        startActivity(RemoteBookDetailsActivity.createIntent(this, source, item, targetFolderId))
                    }
                )
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemDarkMode = newConfig.isNightModeEnabled()
    }

    companion object {
        private const val EXTRA_TYPE = "remote_library_type"
        private const val EXTRA_TARGET_FOLDER_ID = "remote_library_target_folder"

        fun createIntent(context: Context, type: RemoteSourceType, targetFolderId: String?): Intent =
            Intent(context, RemoteLibraryActivity::class.java)
                .putExtra(EXTRA_TYPE, type.name)
                .putExtra(EXTRA_TARGET_FOLDER_ID, targetFolderId)
    }
}
