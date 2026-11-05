package com.huangder.lumibooks.ui.theme

import androidx.compose.runtime.compositionLocalOf

/** Retained tabs keep their UI state, but only pages participating in a transition do work. */
internal val LocalPageVisible = compositionLocalOf { true }
