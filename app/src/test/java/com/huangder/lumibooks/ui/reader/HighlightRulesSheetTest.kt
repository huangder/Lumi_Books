package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.HighlightRule
import com.huangder.lumibooks.highlight.HighlightRuleScanState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HighlightRulesSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun movedRuleStaysAtTheSameScreenPositionAfterTheSheetIsRebuilt() {
        val originalRules = List(24) { HighlightRule("rule-$it", "Rule $it", "pattern $it", position = it) }
        val target = originalRules[12]
        val rules = mutableStateOf(originalRules)
        val mounted = mutableStateOf(true)
        var requestedMove: Pair<String, Int>? = null
        compose.setContent {
            val state = rememberHighlightRulesSheetState()
            if (mounted.value) {
                HighlightRulesSheet(
                    visible = true,
                    state = state,
                    rules = rules.value,
                    materializeNotes = false,
                    scanState = HighlightRuleScanState.Idle,
                    eInkModeEnabled = true,
                    onMaterializeChange = {},
                    onSaveRule = {},
                    onDuplicateRule = {},
                    onDeleteRule = {},
                    onRuleEnabledChange = { _, _ -> },
                    onMoveRule = { id, delta ->
                        requestedMove = id to delta
                        mounted.value = false
                    },
                    onImportRules = { _, _ -> error("Unexpected import") },
                    onExportRules = { error("Unexpected export") },
                    onCancelScan = {},
                    onRebuild = {},
                    onDismiss = {}
                )
            }
        }

        compose.onNode(hasScrollAction()).performScrollToKey(originalRules[10].id)
        val targetLabel = compose.onNodeWithText(target.name).assertIsDisplayed()
        val targetTop = targetLabel.fetchSemanticsNode().boundsInRoot.top
        val context = RuntimeEnvironment.getApplication()
        for (delta in listOf(1, 1, -1, -1)) {
            val action = context.getString(if (delta < 0) R.string.move_up else R.string.move_down)
            compose.onNode(
                hasContentDescription(action) and hasAnyAncestor(hasTestTag("highlight-rule-${target.id}")),
                useUnmergedTree = true
            ).assertIsDisplayed().performClick()
            targetLabel.assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(target.id to delta, requestedMove)
                val updated = rules.value.toMutableList()
                val oldIndex = updated.indexOfFirst { it.id == target.id }
                updated.add(oldIndex + delta, updated.removeAt(oldIndex))
                rules.value = updated.mapIndexed { index, rule -> rule.copy(position = index) }
                requestedMove = null
                mounted.value = true
            }
            targetLabel.assertIsDisplayed()
            assertEquals(targetTop, targetLabel.fetchSemanticsNode().boundsInRoot.top, 1f)
        }
    }
}
