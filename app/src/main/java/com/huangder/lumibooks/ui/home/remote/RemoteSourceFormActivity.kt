package com.huangder.lumibooks.ui.home.remote

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.remote.RemoteSource
import com.huangder.lumibooks.data.remote.RemoteSourceType
import com.huangder.lumibooks.data.sync.LocalNetworkAccessManager
import com.huangder.lumibooks.ui.components.ConfigurableActivityBack
import com.huangder.lumibooks.ui.components.rememberLocalNetworkPermissionGate
import com.huangder.lumibooks.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.json.JSONObject

/**
 * Add / edit a remote library. A separate Activity so opening it plays the OEM system
 * transition, like every other settings-style page in Lumi.
 */
@AndroidEntryPoint
class RemoteSourceFormActivity : ComponentActivity() {
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
        val existing = intent.getStringExtra(EXTRA_SOURCE)
            ?.let { runCatching { RemoteSource.fromJson(JSONObject(it)) }.getOrNull() }

        setContent {
            RemoteThemedContent(dataStoreManager, systemDarkMode) { _, predictiveBackEnabled ->
                val viewModel: RemoteLibraryViewModel = hiltViewModel()
                val requestLocalNetworkPermission = rememberLocalNetworkPermissionGate(localNetworkAccessManager)
                LaunchedEffect(type) { viewModel.start(type, null) }
                val hasSavedPassword = remember(existing?.id) {
                    existing?.let { viewModel.hasPassword(it.id) } == true
                }
                ConfigurableActivityBack(predictiveBackEnabled = predictiveBackEnabled, onBack = { finish() })
                RemoteSourceFormScreen(
                    type = type,
                    existing = existing,
                    hasSavedPassword = hasSavedPassword,
                    requestLocalNetworkPermission = requestLocalNetworkPermission,
                    loadSyncAccount = viewModel::webdavSyncAccount,
                    onTestAndSave = { name, url, username, password ->
                        viewModel.testAndSave(existing, name, url, username, password)
                    },
                    onBack = { finish() },
                    onSaved = { finish() }
                )
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemDarkMode = newConfig.isNightModeEnabled()
    }

    companion object {
        private const val EXTRA_TYPE = "remote_source_type"
        private const val EXTRA_SOURCE = "remote_source"

        /** The source JSON never contains the password; the form reads the stored one itself. */
        fun createIntent(context: Context, type: RemoteSourceType, existing: RemoteSource?): Intent =
            Intent(context, RemoteSourceFormActivity::class.java)
                .putExtra(EXTRA_TYPE, type.name)
                .putExtra(EXTRA_SOURCE, existing?.toJson()?.toString())
    }
}
