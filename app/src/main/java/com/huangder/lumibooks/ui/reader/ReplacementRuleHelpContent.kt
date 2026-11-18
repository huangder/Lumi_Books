package com.huangder.lumibooks.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassSegmentedControl
import com.huangder.lumibooks.ui.settings.DetailPage
import com.huangder.lumibooks.ui.theme.*

@Composable
internal fun ReplacementRuleHelpPage(onBack: () -> Unit) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    // Each tab starts at its top, even after a long tutorial/prompt was scrolled.
    key(selectedTab) {
        DetailPage(
            title = stringResource(R.string.replacement_help_title),
            onBack = onBack,
            actions = { ReplacementHelpTabs(selectedTab) { selectedTab = it } }
        ) {
            ReplacementRuleHelpContent(selectedTab)
        }
    }
}

@Composable
internal fun ReplacementHelpTabs(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(stringResource(R.string.replacement_help_tab_tutorial), stringResource(R.string.replacement_help_tab_ai))
    val modifier = Modifier.width(192.dp).height(48.dp).testTag("replacement-help-tabs")
    val eInk = LocalEInkMode.current
    if (LocalAppTheme.current != "liquid_glass" || eInk) {
        val shape = AppRoundedCornerShape(24.dp)
        Row(
            modifier.clip(shape).background(AppColors.BgGray)
                .then(if (eInk) Modifier.border(1.dp, AppColors.TextPrimary, shape) else Modifier)
                .padding(2.dp).selectableGroup()
        ) {
            labels.forEachIndexed { index, label ->
                val active = selected == index
                val itemShape = AppRoundedCornerShape(22.dp)
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(itemShape)
                        .then(if (active) Modifier.background(AppColors.CardBg)
                            .then(if (eInk) Modifier.border(1.dp, AppColors.TextPrimary, itemShape) else Modifier) else Modifier)
                        .selectable(active, role = Role.Tab, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = if (active) AppColors.TextPrimary else AppColors.TextSecondary,
                        fontSize = AppType.Caption, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1)
                }
            }
        }
        return
    }
    // Match both the visible track and touch bounds to the toolbar's 48dp back button.
    LiquidGlassSegmentedControl(
        itemCount = 2,
        selectedIndex = selected,
        onSelected = onSelect,
        trackHeight = 48.dp,
        modifier = modifier
    ) { index, active ->
        Text(
            labels[index],
            color = if (active) AppColors.TextPrimary else AppColors.TextSecondary,
            fontSize = AppType.Caption,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
internal fun ReplacementRuleHelpContent(selectedTab: Int) {
    key(selectedTab) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selectedTab == 0) ReplacementTutorial() else ReplacementAiGuide()
        }
    }
}

@Composable
private fun ReplacementTutorial() {
    HelpSection(R.string.replacement_help_intro_title, R.string.replacement_help_intro_body)
    HelpSection(R.string.replacement_help_steps_title, R.string.replacement_help_steps_body)
    HelpSection(R.string.replacement_help_symbols_title, R.string.replacement_help_symbols_body, code = true)
    HelpSection(R.string.replacement_help_escape_title, R.string.replacement_help_escape_body)
    HelpSection(R.string.replacement_help_examples_title, R.string.replacement_help_examples_body)
    ReplacementHelpExamples.forEach { example ->
        Surface(shape = AppRoundedCornerShape(AppRadius.lg), color = AppColors.BgGray) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HelpHeading(stringResource(example.title))
                HelpCode(stringResource(R.string.replacement_find), stringResource(example.find))
                HelpCode(stringResource(R.string.replacement_value), when {
                    example.space -> stringResource(R.string.replacement_help_one_space)
                    example.value == null -> stringResource(R.string.replacement_help_empty)
                    else -> stringResource(example.value)
                })
                HelpCode(stringResource(R.string.replacement_before_label), stringResource(example.before))
                HelpCode(stringResource(R.string.replacement_after_label), stringResource(example.after))
                HelpBody(stringResource(example.body))
            }
        }
    }
    HelpSection(R.string.replacement_help_groups_title, R.string.replacement_help_groups_body)
    HelpSection(R.string.replacement_help_flags_title, R.string.replacement_help_flags_body)
    HelpSection(R.string.replacement_help_order_title, R.string.replacement_help_order_body)
    HelpSection(R.string.replacement_help_faq_title, R.string.replacement_help_faq_body)
}

@Composable
private fun ReplacementAiGuide() {
    HelpSection(R.string.replacement_help_ai_screenshot_title, R.string.replacement_help_ai_screenshot_body)
    HelpSection(R.string.replacement_help_ai_copy_title, R.string.replacement_help_ai_copy_body)
    val context = LocalContext.current
    val prompt = remember(context) {
        context.resources.openRawResource(R.raw.replacement_regex_ai_prompt).bufferedReader().use { it.readText() }
    }
    var copied by remember { mutableStateOf(false) }
    LiquidGlassButton(
        onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.replacement_help_tab_ai), prompt))
            copied = true
        },
        tintedColor = AppColors.Accent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(if (copied) R.string.replacement_help_ai_copied else R.string.replacement_help_ai_copy),
            color = AppColors.OnAccent, fontSize = AppType.BodySmall, fontWeight = FontWeight.SemiBold)
    }
    Surface(shape = AppRoundedCornerShape(AppRadius.lg), color = AppColors.BgGray) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HelpHeading(stringResource(R.string.replacement_help_ai_prompt_title))
            SelectionContainer { HelpBody(prompt) }
        }
    }
    HelpSection(R.string.replacement_help_ai_apply_title, R.string.replacement_help_ai_apply_body)
}

@Composable
private fun HelpSection(@StringRes title: Int, @StringRes body: Int, code: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HelpHeading(stringResource(title))
        SelectionContainer { HelpBody(stringResource(body), code) }
    }
}

@Composable
private fun HelpHeading(title: String) {
    Text(title, color = AppColors.TextPrimary, fontSize = AppType.Body,
        fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
}

@Composable
private fun HelpBody(body: String, code: Boolean = false) {
    Text(body, color = AppColors.TextSecondary, fontSize = AppType.BodySmall,
        fontFamily = if (code) FontFamily.Monospace else null)
}

@Composable
private fun HelpCode(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = AppColors.TextSecondary, fontSize = AppType.Caption)
        SelectionContainer {
            Text(value, color = AppColors.TextPrimary, fontSize = AppType.BodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

internal data class ReplacementHelpExample(
    @StringRes val title: Int,
    @StringRes val body: Int,
    @StringRes val find: Int,
    @StringRes val value: Int?,
    @StringRes val before: Int,
    @StringRes val after: Int,
    val space: Boolean = false
)

internal val ReplacementHelpExamples = listOf(
    ReplacementHelpExample(R.string.replacement_help_example_names_title, R.string.replacement_help_example_names_body,
        R.string.replacement_help_names_find, R.string.replacement_help_names_value,
        R.string.replacement_help_names_before, R.string.replacement_help_names_after),
    ReplacementHelpExample(R.string.replacement_help_example_ads_title, R.string.replacement_help_example_ads_body,
        R.string.replacement_help_ads_find, null, R.string.replacement_help_ads_before, R.string.replacement_help_ads_after),
    ReplacementHelpExample(R.string.replacement_help_example_brackets_title, R.string.replacement_help_example_brackets_body,
        R.string.replacement_help_brackets_find, null, R.string.replacement_help_brackets_before, R.string.replacement_help_brackets_after),
    ReplacementHelpExample(R.string.replacement_help_example_spaces_title, R.string.replacement_help_example_spaces_body,
        R.string.replacement_help_spaces_find, null, R.string.replacement_help_spaces_before, R.string.replacement_help_spaces_after, space = true),
    ReplacementHelpExample(R.string.replacement_help_example_groups_title, R.string.replacement_help_example_groups_body,
        R.string.replacement_help_groups_find, R.string.replacement_help_groups_value,
        R.string.replacement_help_groups_before, R.string.replacement_help_groups_after),
    ReplacementHelpExample(R.string.replacement_help_example_case_title, R.string.replacement_help_example_case_body,
        R.string.replacement_help_case_find, R.string.replacement_help_case_value,
        R.string.replacement_help_case_before, R.string.replacement_help_case_after)
)
