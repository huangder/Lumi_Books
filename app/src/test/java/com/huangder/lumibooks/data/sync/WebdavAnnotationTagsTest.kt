package com.huangder.lumibooks.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class WebdavAnnotationTagsTest {
    @Test
    fun legacyPayloadWithoutTagsPreservesLocalTags() {
        assertEquals(
            listOf("复习", "人物"),
            resolveSyncedAnnotationTags(hasRemoteTags = false, remoteTagsJson = null, localTags = listOf("复习", "人物"))
        )
    }

    @Test
    fun explicitEmptyTagsStillClearsLocalTags() {
        assertEquals(
            emptyList<String>(),
            resolveSyncedAnnotationTags(hasRemoteTags = true, remoteTagsJson = "[]", localTags = listOf("复习"))
        )
    }
}
