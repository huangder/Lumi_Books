package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.domain.model.EARLY_DONOR_NOTICE_ID
import com.huangder.lumibooks.domain.model.ReceivedNotice
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReceivedNoticePersistenceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clearPreferences() = runBlocking {
        context.dataStore.edit { it.clear() }
        Unit
    }

    @Test fun preservesFirstSnapshotWhenAnAnnouncementIsReplacedOrNoLongerFetched() = runBlocking {
        val store = DataStoreManager(context)
        val remote = ReceivedNotice("remote", "Announcement", "First\nmessage", 100, "https://example.com/photo.jpg")
        val donor = ReceivedNotice(EARLY_DONOR_NOTICE_ID, "Donor announcement", "Donor message", 200)
        assertEquals(emptyList<ReceivedNotice>(), store.receivedNotices.first())

        store.recordReceivedNotice(remote)
        listOf(
            async { store.recordReceivedNotice(remote.copy(title = "Revised", message = "Changed", receivedAt = 300, imageUrl = null)) },
            async { store.recordReceivedNotice(donor) }
        ).awaitAll()

        val reopened = DataStoreManager(context)
        assertEquals(listOf(donor, remote), reopened.receivedNotices.first())
        assertEquals(emptySet<String>(), reopened.acknowledgedNoticeIds.first())
        reopened.acknowledgeNotice(donor.id)
        assertEquals(setOf(donor.id), reopened.acknowledgedNoticeIds.first())
        assertEquals(listOf(donor, remote), reopened.receivedNotices.first())
        assertFalse(reopened.exportPortablePreferences("test-device").any { it.key == "received_notices_v1" })
    }

    @Test fun archivesAnActiveAnnouncementPreviouslyAcknowledgedOnAnOlderVersion() = runBlocking {
        val store = DataStoreManager(context)
        val notice = ReceivedNotice(EARLY_DONOR_NOTICE_ID, "Donor announcement", "Donor message", 100)
        store.acknowledgeNotice(notice.id)
        assertEquals(emptyList<ReceivedNotice>(), store.receivedNotices.first())

        store.recordReceivedNotice(notice)

        assertEquals(listOf(notice), store.receivedNotices.first())
        assertEquals(setOf(notice.id), store.acknowledgedNoticeIds.first())
    }
}
