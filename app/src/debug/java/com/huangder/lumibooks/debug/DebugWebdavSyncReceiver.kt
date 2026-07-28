package com.huangder.lumibooks.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.huangder.lumibooks.data.sync.WebdavSyncManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DebugWebdavSyncReceiver : BroadcastReceiver() {
    @Inject lateinit var syncManager: WebdavSyncManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SYNC) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "Debug WebDAV sync started")
                val result = syncManager.fullSync()
                Log.i(TAG, "Debug WebDAV sync finished: success=${result.success}, message=${result.message}")
            } catch (error: Throwable) {
                Log.e(TAG, "Debug WebDAV sync crashed", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SYNC = "com.huangder.lumibooks.DEBUG_WEBDAV_SYNC"
        private const val TAG = "WebDAV"
    }
}
