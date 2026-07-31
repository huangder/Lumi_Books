package com.huangder.lumibooks.widget

import android.content.Context
import com.huangder.lumibooks.data.local.dao.BookDao
import com.huangder.lumibooks.data.local.dao.NoteDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDataEntryPoint {
    fun bookDao(): BookDao
    fun noteDao(): NoteDao
}

internal fun Context.widgetDataEntryPoint(): WidgetDataEntryPoint {
    return EntryPointAccessors.fromApplication(
        applicationContext,
        WidgetDataEntryPoint::class.java
    )
}
