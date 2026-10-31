package com.huangder.lumibooks.edition

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.huangder.lumibooks.ui.statistics.report.ReadingReportPeriod

@Composable
internal fun EditionMembershipEntry() = Unit

@Composable
internal fun EditionTagManagementSettingsEntry() = Unit

@Composable
internal fun EditionReportEntry(period: ReadingReportPeriod) = Unit

@Composable
internal fun EditionTagManagementButton(type: String, modifier: Modifier = Modifier) = Unit

@Composable
internal fun rememberRemoteSourcePermission(sourceCount: Int): () -> Boolean = { true }
