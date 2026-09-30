package com.huangder.lumibooks.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.reader.ReaderDocumentState
import kotlinx.coroutines.delay

internal fun shouldMountReaderContent(state: ReaderDocumentState): Boolean =
    state.book != null || state.error != null || !state.isLoading

@Composable
internal fun ReaderOpeningPlaceholder(onNavigateBack: () -> Unit) {
    com.huangder.lumibooks.ui.components.ConfigurableBackHandler(onBack = onNavigateBack)
    var showLoading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(600)
        showLoading = true
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center) {
        if (showLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}
