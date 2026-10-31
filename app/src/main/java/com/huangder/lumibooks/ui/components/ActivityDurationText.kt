package com.huangder.lumibooks.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.huangder.lumibooks.R

@Composable
internal fun activityDurationText(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0L) / 1_000L
    return if (seconds < 60L) stringResource(R.string.activity_duration_seconds, seconds)
    else stringResource(R.string.statistics_duration_value, seconds / 3_600L, seconds / 60L % 60L)
}
