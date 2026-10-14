package com.huangder.lumibooks.ui.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.dataStore
import com.huangder.lumibooks.util.AuthorizedFolderSnapshotStore
import com.huangder.lumibooks.util.AuthorizedStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AuthorizedFoldersViewModelTest {
    @Test
    fun removingOneRootKeepsOtherRootAndIndividualFileGrants() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val dataStore = DataStoreManager(context)
        val snapshots = AuthorizedFolderSnapshotStore(context)
        val first = Uri.parse("content://documents/tree/primary%3ABooks")
        val second = Uri.parse("content://documents/tree/primary%3AArchive%2FBooks")
        val file = Uri.parse("content://documents/document/primary%3AOther%2Fbook.epub")
        val resolver = context.contentResolver
        resolver.takePersistableUriPermission(first, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        resolver.takePersistableUriPermission(second, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        resolver.takePersistableUriPermission(file, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        listOf(first, second).forEach {
            dataStore.addAuthorizedBookDirectory(it.toString())
            snapshots.replaceTree(it, AuthorizedStorageManager.ScanResult(it, emptyList(), emptyList()), "Books")
        }
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val store = ViewModelStore()
        try {
            val viewModel = AuthorizedFoldersViewModel(context, dataStore, snapshots, AuthorizedStorageManager())
            store.put("folders", viewModel)
            val loaded = withTimeout(10_000) { viewModel.uiState.first { !it.loading } }
            assertEquals(2, loaded.folders.size)
            assertEquals(listOf("Books", "Books"), loaded.folders.map { it.name })
            assertEquals(2, loaded.folders.map { it.path }.distinct().size)

            viewModel.removeFolder(loaded.folders.first { it.uri == first.toString() })

            withTimeout(10_000) { snapshots.snapshot.first { first.toString() !in it.trees } }
            val remaining = withTimeout(10_000) { viewModel.uiState.first { it.folders.size == 1 } }
            assertEquals(second.toString(), remaining.folders.single().uri)
            assertEquals(listOf(second.toString()), DataStoreManager(context).authorizedBookDirectories.first())
            assertEquals(setOf(second.toString()), AuthorizedFolderSnapshotStore(context).load().trees.keys)
            assertEquals(setOf(second, file), resolver.persistedUriPermissions.map { it.uri }.toSet())
            assertTrue(resolver.persistedUriPermissions.first { it.uri == second }.isWritePermission)
            assertTrue(shadowOf(resolver).deletedUris.isEmpty())
            assertFalse(remaining.removalFailed)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun lostGrantCanStillBeRemovedAndListBecomesEmpty() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val dataStore = DataStoreManager(context)
        val snapshots = AuthorizedFolderSnapshotStore(context)
        val uri = "content://documents/tree/primary%3AMissing"
        dataStore.addAuthorizedBookDirectory(uri)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val store = ViewModelStore()
        try {
            val viewModel = AuthorizedFoldersViewModel(context, dataStore, snapshots, AuthorizedStorageManager())
            store.put("folders", viewModel)
            val loaded = withTimeout(10_000) { viewModel.uiState.first { !it.loading } }
            assertEquals("Missing", loaded.folders.single().name)
            viewModel.removeFolder(loaded.folders.single())
            val empty = withTimeout(10_000) { viewModel.uiState.first { !it.loading && it.folders.isEmpty() } }
            assertFalse(empty.removalFailed)
            assertTrue(DataStoreManager(context).authorizedBookDirectories.first().isEmpty())
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
