package com.huangder.lumibooks.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.replacement.BookReplacementRules
import com.huangder.lumibooks.replacement.ReplacementError
import com.huangder.lumibooks.replacement.ReplacementPreview
import com.huangder.lumibooks.replacement.ReplacementProgram
import com.huangder.lumibooks.replacement.ReplacementRule
import com.huangder.lumibooks.ui.components.AppBottomSheet
import com.huangder.lumibooks.ui.components.AppDialogAction
import com.huangder.lumibooks.ui.components.AppDialogActionRole
import com.huangder.lumibooks.ui.components.AppSheetHeader
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassSegmentedControl
import com.huangder.lumibooks.ui.components.LiquidGlassSwitch
import com.huangder.lumibooks.ui.components.LumiTextField
import com.huangder.lumibooks.ui.components.appSheetMaxHeight
import com.huangder.lumibooks.ui.components.appSheetPadding
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CardShape @Composable get() = AppRoundedCornerShape(AppRadius.lg)

/** One critically damped spring for every size change, so nested resizes stay in step; snaps without motion. */
private class SheetMotion(enabled: Boolean) {
    val size: FiniteAnimationSpec<IntSize> =
        if (enabled) spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow, IntSize.VisibilityThreshold) else snap()
    val fadeIn: FiniteAnimationSpec<Float> = if (enabled) tween(220, delayMillis = 60) else snap()
    val fadeOut: FiniteAnimationSpec<Float> = if (enabled) tween(120) else snap()
    val expand: EnterTransition = expandVertically(size, Alignment.Top) + fadeIn(fadeIn)
    val collapse: ExitTransition = shrinkVertically(size, Alignment.Top) + fadeOut(fadeOut)
}

@Composable
private fun rememberSheetMotion(): SheetMotion {
    val enabled = LocalMotionEnabled.current && !LocalEInkMode.current
    return remember(enabled) { SheetMotion(enabled) }
}
private val RegexFont = FontFamily.Monospace

@Composable
internal fun ReplacementRulesSheet(viewModel: ReaderViewModel, chapter: Int, onDismiss: () -> Unit) {
    val settings by viewModel.replacementRules.collectAsState()
    ReplacementRulesSheet(settings, chapter, viewModel::updateReplacementRules, viewModel::previewReplacement, onDismiss)
}

@Composable
internal fun ReplacementRulesSheet(
    settings: BookReplacementRules,
    chapter: Int,
    updateRules: suspend ((BookReplacementRules) -> BookReplacementRules) -> Unit,
    previewChapter: suspend (Int, BookReplacementRules, ReplacementRule) -> ReplacementPreview?,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf<ReplacementRule?>(null) }
    var pendingDelete by remember { mutableStateOf<ReplacementRule?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun update(change: (BookReplacementRules) -> BookReplacementRules, onSaved: () -> Unit = {}) {
        if (saving) return
        saving = true
        saveFailed = false
        scope.launch {
            try {
                updateRules(change)
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                saveFailed = true
            } finally {
                saving = false
            }
        }
    }
    val editor = draft
    val original = editor?.let { current -> settings.rules.firstOrNull { it.id == current.id } }
    val isNew = editor != null && original == null
    val error = remember(editor) { editor?.let(ReplacementProgram::validate) }
    val goBack: () -> Unit = {
        if (!saving) {
            val current = draft
            when {
                current == null -> onDismiss()
                current != (original ?: ReplacementRule(current.id)) -> confirmDiscard = true
                else -> draft = null
            }
        }
    }
    val save: () -> Unit = {
        // Validate the draft at click time; the header's enabled state is from the last composition.
        val rule = draft
        if (rule != null && ReplacementProgram.validate(rule) == null) {
            update({ it.upsert(rule) }) { draft = null }
        }
    }

    // Keeps the outgoing editor drawable while it fades out after Back or Save clears the draft.
    var lastDraft by remember { mutableStateOf<ReplacementRule?>(null) }
    if (editor != null) lastDraft = editor
    val motion = rememberSheetMotion()

    // Scrim taps and sheet swipes close the whole task; the back gesture steps back from the editor.
    AppBottomSheet(onDismissRequest = onDismiss) {
        BackHandler(enabled = editor != null, onBack = goBack)
        // Children carry their own top gap so that collapsing rows animate their spacing too.
        Column(
            Modifier.fillMaxWidth().heightIn(max = appSheetMaxHeight())
                .imePadding().padding(appSheetPadding())
        ) {
            AppSheetHeader(
                title = stringResource(when {
                    editor == null -> R.string.replacement_title
                    isNew -> R.string.replacement_new
                    else -> R.string.replacement_edit
                }),
                onClose = goBack,
                onBack = if (editor != null) goBack else null,
                // Save lives in the header so it stays reachable while the keyboard covers the sheet.
                onConfirm = if (editor != null) save else null,
                confirmEnabled = error == null && !saving
            )
            AnimatedVisibility(saveFailed, enter = motion.expand, exit = motion.collapse) {
                Text(stringResource(R.string.replacement_save_failed), color = AppColors.Destructive,
                    fontSize = AppType.Footnote,
                    modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
            Box(Modifier.weight(1f, fill = false).padding(top = 12.dp).verticalScroll(rememberScrollState())) {
                // The page swap springs the sheet height while the pages cross-fade.
                AnimatedContent(
                    targetState = editor?.id,
                    transitionSpec = {
                        (fadeIn(motion.fadeIn) togetherWith fadeOut(motion.fadeOut))
                            .using(SizeTransform(clip = false) { _, _ -> motion.size })
                    },
                    label = "replacementPage"
                ) { id ->
                    // Changes within a page (mode, notices, preview states, rule count) resize smoothly.
                    Column(
                        Modifier.fillMaxWidth().animateContentSize(motion.size),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val rule = if (id == null) null else draft?.takeIf { it.id == id } ?: lastDraft?.takeIf { it.id == id }
                        if (rule == null) {
                            ReplacementRuleList(
                                settings = settings,
                                enabled = !saving && draft == null,
                                onBookToggle = { checked -> update({ it.copy(enabled = checked) }) },
                                onRuleToggle = { item, checked -> update({ it.upsert(item.copy(enabled = checked)) }) },
                                onEdit = { draft = it },
                                onMove = { item, delta -> update({ it.move(item.id, delta) }) },
                                onDelete = { pendingDelete = it }
                            )
                        } else {
                            val active = draft?.id == id
                            val ruleError = remember(rule) { ReplacementProgram.validate(rule) }
                            ReplacementRuleEditor(rule, ruleError, !saving && active,
                                { if (active) draft = it }, settings, chapter) {
                                if (active) previewChapter(chapter, settings, rule) else null
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(editor == null, enter = motion.expand, exit = motion.collapse) {
                Box(Modifier.padding(top = 12.dp)) {
                    LiquidGlassButton(
                        onClick = { draft = ReplacementRule(UUID.randomUUID().toString()) },
                        enabled = !saving,
                        tintedColor = AppColors.Accent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(AppIcons.Plus, null, Modifier.size(18.dp), tint = AppColors.OnAccent)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.replacement_add), color = AppColors.OnAccent,
                            fontSize = AppType.BodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    pendingDelete?.let { rule ->
        LiquidGlassAlertDialog(
            onDismissRequest = { pendingDelete = null },
            titleText = stringResource(R.string.replacement_delete),
            text = {
                ReplacementMapping(rule, maxLines = 3)
            },
            contentScrimColor = AppColors.CardBg.copy(alpha = 0.82f),
            actions = listOf(
                AppDialogAction(stringResource(R.string.cancel), { pendingDelete = null }, AppDialogActionRole.Cancel),
                AppDialogAction(stringResource(R.string.delete), {
                    pendingDelete = null
                    update({ rules -> rules.copy(rules = rules.rules.filterNot { it.id == rule.id }) })
                }, AppDialogActionRole.Destructive)
            )
        )
    }
    if (confirmDiscard) {
        LiquidGlassAlertDialog(
            onDismissRequest = { confirmDiscard = false },
            titleText = stringResource(R.string.replacement_discard_title),
            contentScrimColor = AppColors.CardBg.copy(alpha = 0.82f),
            actions = listOf(
                AppDialogAction(stringResource(R.string.replacement_keep_editing), { confirmDiscard = false },
                    AppDialogActionRole.Cancel),
                AppDialogAction(stringResource(R.string.replacement_discard), {
                    confirmDiscard = false
                    draft = null
                }, AppDialogActionRole.Destructive)
            )
        )
    }
}

// ----------------------------------------------------------------------------------------------
// Rule list
// ----------------------------------------------------------------------------------------------

@Composable
internal fun ReplacementRuleList(
    settings: BookReplacementRules,
    enabled: Boolean,
    onBookToggle: (Boolean) -> Unit,
    onRuleToggle: (ReplacementRule, Boolean) -> Unit,
    onEdit: (ReplacementRule) -> Unit,
    onMove: (ReplacementRule, Int) -> Unit,
    onDelete: (ReplacementRule) -> Unit
) {
    val activeCount = settings.rules.count { it.enabled }
    val bookLabel = stringResource(R.string.replacement_enable_book)
    Surface(Modifier.fillMaxWidth(), shape = CardShape, color = AppColors.BgGray) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(bookLabel, color = AppColors.TextPrimary, fontSize = AppType.Body,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        !settings.enabled -> stringResource(R.string.replacement_status_off)
                        activeCount == 0 -> stringResource(R.string.replacement_status_idle)
                        else -> stringResource(R.string.replacement_status_active, activeCount)
                    },
                    color = AppColors.TextSecondary, fontSize = AppType.Caption
                )
            }
            LiquidGlassSwitch(
                checked = settings.enabled,
                onCheckedChange = onBookToggle,
                modifier = Modifier.semantics { contentDescription = bookLabel },
                enabled = enabled
            )
        }
    }

    if (settings.rules.isEmpty()) {
        ReplacementEmptyState()
        return
    }

    Text(
        stringResource(if (settings.enabled) R.string.replacement_rules_order else R.string.replacement_rules_paused),
        color = AppColors.TextSecondary, fontSize = AppType.Footnote,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp).semantics { heading() }
    )
    settings.rules.forEachIndexed { index, rule ->
        key(rule.id) {
            ReplacementRuleCard(
                rule = rule,
                bookEnabled = settings.enabled,
                canMoveUp = index > 0,
                canMoveDown = index < settings.rules.lastIndex,
                enabled = enabled,
                onToggle = { onRuleToggle(rule, it) },
                onEdit = { onEdit(rule) },
                onMove = { onMove(rule, it) },
                onDelete = { onDelete(rule) }
            )
        }
    }
}

@Composable
private fun ReplacementEmptyState() {
    val eInk = LocalEInkMode.current
    Column(
        Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(56.dp)
                .background(if (eInk) Color.White else AppColors.BgGray, CircleShape)
                .then(if (eInk) Modifier.border(1.dp, Color.Black, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(AppIcons.ArrowsLeftRight, null, Modifier.size(24.dp), tint = AppColors.TextSecondary)
        }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.replacement_empty_title), color = AppColors.TextPrimary,
            fontSize = AppType.Body, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.replacement_empty), color = AppColors.TextSecondary,
            fontSize = AppType.Footnote, textAlign = TextAlign.Center,
            style = TextStyle(lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph),
            modifier = Modifier.widthIn(max = 280.dp))
    }
}

@Composable
private fun ReplacementRuleCard(
    rule: ReplacementRule,
    bookEnabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val label = rule.name.ifBlank { rule.find }
    val effective = bookEnabled && rule.enabled
    val mode = stringResource(if (rule.regex) R.string.replacement_regex else R.string.replacement_plain)
    val mapping = mappingDescription(rule)
    val editLabel = stringResource(R.string.replacement_edit)
    Surface(Modifier.fillMaxWidth(), shape = CardShape, color = AppColors.BgGray) {
        Column(
            Modifier.clickable(enabled = enabled, onClickLabel = editLabel, onClick = onEdit)
                .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f).padding(end = 8.dp).alpha(if (effective) 1f else 0.55f)
                        .clearAndSetSemantics {
                            contentDescription = listOf(rule.name, mode, mapping)
                                .filter(String::isNotBlank).joinToString(", ")
                        },
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ReplacementModeBadge(rule.regex)
                        if (rule.name.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(rule.name, color = AppColors.TextPrimary, fontSize = AppType.BodySmall,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    ReplacementMapping(rule, maxLines = 3)
                }
                val switchLabel = stringResource(R.string.replacement_rule_switch, label)
                LiquidGlassSwitch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics { contentDescription = switchLabel },
                    enabled = enabled
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)
            ) {
                RuleActionButton(AppIcons.CaretUp, stringResource(R.string.move_up), enabled && canMoveUp) { onMove(-1) }
                RuleActionButton(AppIcons.CaretDown, stringResource(R.string.move_down), enabled && canMoveDown) { onMove(1) }
                RuleActionButton(AppIcons.PencilSimple, editLabel, enabled, onClick = onEdit)
                RuleActionButton(AppIcons.Trash, stringResource(R.string.delete), enabled, AppColors.Destructive, onDelete)
            }
        }
    }
}

@Composable
private fun mappingDescription(rule: ReplacementRule): String =
    if (rule.replacement.isEmpty()) stringResource(R.string.replacement_mapping_delete, rule.find)
    else stringResource(R.string.replacement_mapping_replace, rule.find, rule.replacement)

/** "What becomes what": the pattern on its own line, then an arrow to the result or a delete marker. */
@Composable
private fun ReplacementMapping(rule: ReplacementRule, maxLines: Int) {
    val description = mappingDescription(rule)
    Column(
        Modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            rule.find,
            color = AppColors.TextPrimary,
            fontSize = if (rule.regex) AppType.Footnote else AppType.BodySmall,
            fontFamily = if (rule.regex) RegexFont else null,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.ArrowRight, null, Modifier.size(14.dp), tint = AppColors.TextSecondary)
            Spacer(Modifier.width(6.dp))
            if (rule.replacement.isEmpty()) {
                Icon(AppIcons.Trash, null, Modifier.size(14.dp), tint = AppColors.Destructive)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.replacement_remove_match), color = AppColors.Destructive,
                    fontSize = AppType.Footnote, fontWeight = FontWeight.Medium)
            } else {
                Text(
                    rule.replacement,
                    color = AppColors.TextPrimary,
                    fontSize = if (rule.regex) AppType.Footnote else AppType.BodySmall,
                    fontFamily = if (rule.regex) RegexFont else null,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ReplacementModeBadge(regex: Boolean) {
    val eInk = LocalEInkMode.current
    val color = if (regex) AppColors.Accent else AppColors.TextSecondary
    Text(
        stringResource(if (regex) R.string.replacement_regex else R.string.replacement_plain),
        color = if (eInk) Color.Black else color,
        fontSize = AppType.Caption2,
        fontWeight = FontWeight.SemiBold,
        fontFamily = if (regex) RegexFont else null,
        modifier = Modifier
            .background(if (eInk) Color.White else color.copy(alpha = 0.14f), AppRoundedCornerShape(AppRadius.sm))
            .then(if (eInk) Modifier.border(1.dp, Color.Black, AppRoundedCornerShape(AppRadius.sm)) else Modifier)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun RuleActionButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    tint: Color = AppColors.TextSecondary,
    onClick: () -> Unit
) {
    LiquidGlassIconButton(
        imageVector = icon,
        contentDescription = description,
        onClick = onClick,
        enabled = enabled,
        size = 44.dp,
        iconSize = 18.dp,
        contentColor = if (enabled) tint else tint.copy(alpha = 0.35f),
        normalContainerColor = AppColors.BgGray
    )
}

// ----------------------------------------------------------------------------------------------
// Editor
// ----------------------------------------------------------------------------------------------

@Composable
internal fun ReplacementRuleEditor(
    draft: ReplacementRule,
    error: ReplacementError?,
    enabled: Boolean,
    onChange: (ReplacementRule) -> Unit,
    settings: BookReplacementRules,
    chapter: Int,
    previewChapter: suspend () -> ReplacementPreview?
) {
    val codeStyle = TextStyle(fontSize = AppType.BodySmall, fontFamily = RegexFont)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val modes = listOf(stringResource(R.string.replacement_plain), stringResource(R.string.replacement_regex))
        LiquidGlassSegmentedControl(
            itemCount = 2,
            selectedIndex = if (draft.regex) 1 else 0,
            onSelected = { onChange(draft.copy(regex = it == 1)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        ) { index, selected ->
            Text(modes[index], color = if (selected) AppColors.TextPrimary else AppColors.TextSecondary,
                fontSize = AppType.Footnote,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
        Text(
            stringResource(if (draft.regex) R.string.replacement_mode_regex_hint else R.string.replacement_mode_plain_hint),
            color = AppColors.TextSecondary, fontSize = AppType.Caption,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
    LumiTextField(
        draft.find, { onChange(draft.copy(find = it)) }, Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.replacement_find)) },
        supportingText = if (error == ReplacementError.INVALID_REGEX) {
            { Text(stringResource(R.string.replacement_invalid_regex)) }
        } else null,
        isError = error == ReplacementError.INVALID_REGEX,
        minLines = if (draft.regex) 2 else 1,
        maxLines = 6,
        textStyle = if (draft.regex) codeStyle else null,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
        enabled = enabled
    )
    LumiTextField(
        draft.replacement, { onChange(draft.copy(replacement = it)) }, Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.replacement_value)) },
        placeholder = { Text(stringResource(R.string.replacement_value_placeholder)) },
        supportingText = when {
            error == ReplacementError.INVALID_GROUP -> {
                { Text(stringResource(R.string.replacement_invalid_group)) }
            }
            draft.replacement.isEmpty() -> null
            draft.regex -> {
                { Text(stringResource(R.string.replacement_group_hint)) }
            }
            else -> null
        },
        isError = error == ReplacementError.INVALID_GROUP,
        maxLines = 4,
        textStyle = if (draft.regex) codeStyle else null,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
        enabled = enabled
    )
    val motion = rememberSheetMotion()
    AnimatedVisibility(draft.replacement.isEmpty(), enter = motion.expand, exit = motion.collapse) {
        ReplacementDeleteNotice()
    }

    ReplacementPreviewCard(draft, error, settings, chapter, previewChapter)

    LumiTextField(
        draft.name, { onChange(draft.copy(name = it)) }, Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.replacement_name)) },
        singleLine = true, enabled = enabled
    )
}

@Composable
private fun ReplacementDeleteNotice() {
    Row(
        Modifier.fillMaxWidth()
            .background(AppColors.Destructive.copy(alpha = if (LocalEInkMode.current) 0f else 0.08f),
                AppRoundedCornerShape(AppRadius.md))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(AppIcons.Trash, null, Modifier.size(16.dp), tint = AppColors.Destructive)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.replacement_delete_notice), color = AppColors.TextPrimary,
            fontSize = AppType.Caption)
    }
}

@Composable
private fun ReplacementPreviewCard(
    draft: ReplacementRule,
    error: ReplacementError?,
    settings: BookReplacementRules,
    chapter: Int,
    previewChapter: suspend () -> ReplacementPreview?
) {
    val preview by produceState<ReplacementPreviewState>(ReplacementPreviewState.Loading, draft, settings, chapter, error) {
        if (error != null) return@produceState
        value = ReplacementPreviewState.Loading
        delay(180)
        value = try {
            previewChapter()?.let(ReplacementPreviewState::Ready) ?: ReplacementPreviewState.Unavailable
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ReplacementPreviewState.Unavailable
        }
    }
    Surface(Modifier.fillMaxWidth(), shape = CardShape, color = AppColors.BgGray) {
        Column(
            Modifier.padding(14.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val state = preview
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.replacement_preview), color = AppColors.TextPrimary,
                    fontSize = AppType.BodySmall, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).semantics { heading() })
                if (error == null && state is ReplacementPreviewState.Ready && state.result.count > 0) {
                    Text(stringResource(R.string.replacement_matches, state.result.count),
                        color = AppColors.Accent, fontSize = AppType.Caption, fontWeight = FontWeight.SemiBold)
                }
            }
            when {
                error == ReplacementError.EMPTY_FIND ->
                    PreviewMessage(stringResource(R.string.replacement_preview_hint_empty))
                error != null ->
                    PreviewMessage(stringResource(R.string.replacement_preview_fix_error), AppIcons.Warning, AppColors.Destructive)
                state == ReplacementPreviewState.Loading ->
                    PreviewMessage(stringResource(R.string.replacement_preview_loading))
                state == ReplacementPreviewState.Unavailable ->
                    PreviewMessage(stringResource(R.string.replacement_preview_unavailable))
                state is ReplacementPreviewState.Ready && state.result.count == 0 -> {
                    PreviewMessage(stringResource(R.string.replacement_no_match), AppIcons.MagnifyingGlass)
                    Text(stringResource(R.string.replacement_no_match_hint), color = AppColors.TextSecondary,
                        fontSize = AppType.Caption)
                }
                state is ReplacementPreviewState.Ready -> PreviewComparison(state.result)
            }
        }
    }
}

@Composable
private fun PreviewMessage(text: String, icon: ImageVector? = null, tint: Color = AppColors.TextSecondary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp), tint = tint)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = tint, fontSize = AppType.Footnote)
    }
}

/** Shows the context around the first match: matched text struck out before, inserted text emphasised after. */
@Composable
private fun PreviewComparison(preview: ReplacementPreview) {
    val eInk = LocalEInkMode.current
    val removed = SpanStyle(
        color = AppColors.Destructive,
        textDecoration = TextDecoration.LineThrough,
        background = if (eInk) Color.Unspecified else AppColors.Destructive.copy(alpha = 0.12f)
    )
    // Neutral rather than accent: several accents are red and would read as another deletion.
    val added = SpanStyle(
        color = AppColors.TextPrimary,
        fontWeight = FontWeight.Bold,
        textDecoration = TextDecoration.Underline,
        background = if (eInk) Color.Unspecified else AppColors.TextPrimary.copy(alpha = 0.10f)
    )
    PreviewLine(stringResource(R.string.replacement_before_label), annotatePreview(preview.before, preview.removed, removed))
    PreviewLine(stringResource(R.string.replacement_after_label), annotatePreview(preview.after, preview.added, added))
    if (preview.count > 1) {
        Text(stringResource(R.string.replacement_preview_first), color = AppColors.TextSecondary,
            fontSize = AppType.Caption)
    }
}

@Composable
private fun PreviewLine(label: String, text: AnnotatedString) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = AppColors.TextSecondary, fontSize = AppType.Caption, fontWeight = FontWeight.Medium)
        Text(text, color = AppColors.TextPrimary, fontSize = AppType.BodySmall,
            modifier = Modifier.fillMaxWidth()
                .background(AppColors.CardBg, AppRoundedCornerShape(AppRadius.sm))
                .padding(horizontal = 10.dp, vertical = 8.dp))
    }
}

/** Line breaks become a visible return mark one-for-one, so the ranges stay valid. */
internal fun annotatePreview(text: String, ranges: List<IntRange>, style: SpanStyle): AnnotatedString =
    buildAnnotatedString {
        append(text.replace('\n', '⏎'))
        ranges.forEach { range ->
            val start = range.first.coerceIn(0, text.length)
            val end = (range.last + 1).coerceIn(start, text.length)
            if (end > start) addStyle(style, start, end)
        }
    }

private sealed interface ReplacementPreviewState {
    data object Loading : ReplacementPreviewState
    data object Unavailable : ReplacementPreviewState
    data class Ready(val result: ReplacementPreview) : ReplacementPreviewState
}
