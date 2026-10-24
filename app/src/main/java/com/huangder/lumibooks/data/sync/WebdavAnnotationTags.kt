package com.huangder.lumibooks.data.sync

import com.huangder.lumibooks.domain.model.AnnotationTags

internal fun resolveSyncedAnnotationTags(
    hasRemoteTags: Boolean,
    remoteTagsJson: String?,
    localTags: List<String>
): List<String> = if (hasRemoteTags) {
    AnnotationTags.decode(remoteTagsJson)
} else {
    localTags
}
