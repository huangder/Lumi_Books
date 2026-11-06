package com.huangder.lumibooks.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w393dp-h852dp-mdpi")
class LumiControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun navigationRowShowsTrailingValueAndClicks() {
        var clicks = 0
        compose.setContent {
            EBookReaderTheme(motionPreference = MotionPreference.REDUCED) {
                LumiNavigationRow("Language", onClick = { clicks++ }, value = "English")
            }
        }
        compose.onNodeWithText("English").assertExists()
        compose.onNodeWithText("Language").performClick()
        assertEquals(1, clicks)
    }

    @Test fun switchRowTogglesFromAnywhereOnTheRow() {
        var checked by mutableStateOf(false)
        compose.setContent {
            EBookReaderTheme(motionPreference = MotionPreference.REDUCED) {
                LumiSwitchRow("Sync", checked, { checked = it })
            }
        }
        compose.onNodeWithText("Sync").performClick()
        compose.runOnIdle { assertTrue(checked) }
    }

    @Test fun checkboxAndRadioExposeTheirRoles() {
        var checked by mutableStateOf(false)
        compose.setContent {
            EBookReaderTheme(motionPreference = MotionPreference.REDUCED) {
                Column {
                    LumiCheckbox(checked, { checked = it })
                    LumiRadio(selected = true, onClick = {})
                }
            }
        }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertIsOff().performClick()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertIsOn()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertExists()
    }

    @Test fun segmentedControlUsesTabSemantics() {
        var selected by mutableStateOf(0)
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", motionPreference = MotionPreference.REDUCED) {
                LumiSegmentedControl(listOf("A", "B", "C"), selected, { selected = it })
            }
        }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(3)
        compose.onNodeWithText("B").performClick().assertIsSelected()
    }

    @Test fun selectionHeaderAndActionBarAreLabelled() {
        var editing by mutableStateOf(false)
        var moved = 0
        compose.setContent {
            EBookReaderTheme(motionPreference = MotionPreference.REDUCED) {
                Column {
                    SelectionHeader("3 books", editing, selectedCount = 1, allSelected = false,
                        onEditToggle = { editing = !editing }, onSelectAll = {})
                    SelectionActionBar(editing, listOf(SelectionAction(AppIcons.FolderSimple, "Move") { moved++ }), enabled = true)
                }
            }
        }
        compose.onNodeWithText("3 books").assertExists()
        compose.onNodeWithText("Move").assertDoesNotExist()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Select all").assertExists()
        compose.onNodeWithText("Move").performClick()
        compose.runOnIdle { assertEquals(1, moved) }
    }

    @Test fun textFieldShowsLabelAndAcceptsInput() {
        var value by mutableStateOf("")
        compose.setContent {
            EBookReaderTheme(motionPreference = MotionPreference.REDUCED) {
                LumiTextField(value, { value = it }, label = { androidx.compose.material3.Text("Server") }, singleLine = true)
            }
        }
        compose.onNodeWithText("Server").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("dav.example")
        compose.runOnIdle { assertEquals("dav.example", value) }
    }
}
