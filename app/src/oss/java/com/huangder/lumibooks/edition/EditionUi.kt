package com.huangder.lumibooks.edition

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.huangder.lumibooks.ui.statistics.report.ReadingReportPeriod

@Composable
internal fun EditionMembershipEntry() = Unit

@Composable
internal fun EditionHomeMembershipPrompt() = Unit

@Composable
internal fun EditionTagManagementSettingsEntry() = Unit

@Composable
internal fun EditionReadingReviewEntry(period: ReadingReportPeriod) = Unit

@Composable
internal fun EditionTagManagementButton(type: String, modifier: Modifier = Modifier, toolbar: Boolean = false) = Unit

@Composable
internal fun rememberRemoteSourcePermission(sourceCount: Int): () -> Boolean = { true }

@Composable
internal fun rememberBookNotesExportAction(
    book: com.huangder.lumibooks.domain.model.Book?,
    notes: List<com.huangder.lumibooks.domain.model.Note>,
    bookmarks: List<com.huangder.lumibooks.domain.model.Bookmark>,
    onExportTxt: () -> Unit
): () -> Unit = onExportTxt

@Composable
internal fun EditionAnnotationFeedExportButton(
    entries: List<com.huangder.lumibooks.ui.bookshelf.AnnotationFeedEntry>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) = Unit

internal fun canOpenBookNotesExport(hasAnnotations: Boolean): Boolean = hasAnnotations
