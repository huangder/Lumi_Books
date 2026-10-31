package com.huangder.lumibooks.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.huangder.lumibooks.domain.model.TagTypes
import androidx.compose.ui.platform.LocalContext
import com.huangder.lumibooks.data.repository.TagManagementRepository
import com.huangder.lumibooks.domain.model.ManagedTag
import com.huangder.lumibooks.data.local.entity.TagGroupEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TagPickerEntryPoint {
    fun tagManagementRepository(): TagManagementRepository
}

data class TagPickerCatalog(val groups: List<TagGroupEntity>, val tags: List<ManagedTag>)

/** Standalone previews and component tests can still supply a plain list of labels. */
@Composable
internal fun rememberTagPickerRepository(): TagManagementRepository? {
    val app = LocalContext.current.applicationContext
    return remember(app) {
        if (app !is dagger.hilt.internal.GeneratedComponentManager<*>) null
        else EntryPointAccessors.fromApplication(app, TagPickerEntryPoint::class.java).tagManagementRepository()
    }
}

/** Global edits must also update the note draft when its nested picker is cancelled. */
@Composable
internal fun ReconcileAnnotationTagDraft(tags: List<String>, onChange: (List<String>) -> Unit) {
    val repository = rememberTagPickerRepository()
    val currentTags by rememberUpdatedState(tags)
    val change by rememberUpdatedState(onChange)
    LaunchedEffect(repository) {
        repository?.mutations?.collect { mutation ->
            if (mutation.type == TagTypes.ANNOTATION && mutation.oldKey in currentTags) {
                change(currentTags.mapNotNull { if (it == mutation.oldKey) mutation.newKey else it }.distinct())
            }
        }
    }
}
