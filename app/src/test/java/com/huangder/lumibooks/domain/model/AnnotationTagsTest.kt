package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationTagsTest {
    @Test fun roundTripTrimsAndDeduplicatesWithoutSplittingNames() {
        assertEquals(listOf("人物", "a,b", "引文\""), AnnotationTags.decode(
            AnnotationTags.encode(listOf(" 人物 ", "", "人物", "a,b", "引文\""))
        ))
    }

    @Test fun missingMalformedAndNonStringValuesAreIgnored() {
        assertEquals(emptyList<String>(), AnnotationTags.decode(null))
        assertEquals(emptyList<String>(), AnnotationTags.decode("not json"))
        assertEquals(listOf("主题"), AnnotationTags.decode("[null,42,{},\"主题\"]"))
    }

    @Test fun renameMergesExistingNameAndKeepsOtherTags() {
        assertEquals(listOf("主题", "人物"), AnnotationTags.rename(listOf("旧名", "主题", "人物"), "旧名", " 主题 "))
    }
}
