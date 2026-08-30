package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookDetailsLayoutInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longUnbrokenFileNameMovesBelowLabel() {
        val label = "文件名"
        val value = "very_long_unbroken_filename_abcdefghijklmnopqrstuvwxyz0123456789.epub"
        composeRule.setContent {
            Box(Modifier.width(320.dp)) {
                DetailRow(label, value, clickable = false)
            }
        }

        val labelBounds = composeRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
        val valueBounds = composeRule.onNodeWithText(value).fetchSemanticsNode().boundsInRoot
        assertTrue(valueBounds.top >= labelBounds.bottom)
        assertTrue(valueBounds.height > labelBounds.height)
    }

    @Test
    fun shortFieldRemainsOnSameLine() {
        val label = "格式"
        val value = "EPUB"
        composeRule.setContent {
            Box(Modifier.width(320.dp)) {
                DetailRow(label, value, clickable = false)
            }
        }

        val labelBounds = composeRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
        val valueBounds = composeRule.onNodeWithText(value).fetchSemanticsNode().boundsInRoot
        assertTrue(valueBounds.top < labelBounds.bottom)
        assertTrue(labelBounds.top < valueBounds.bottom)
    }
}
