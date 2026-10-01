package com.huangder.lumibooks.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.AnnotationTags
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppType
import com.kyant.backdrop.Backdrop

@Composable
fun AnnotationTagSheet(
    selected: List<String>, available: List<String>,
    onSave: (List<String>) -> Unit, onDismiss: () -> Unit,
    onRename: ((String, String) -> Unit)? = null,
    onDelete: ((String) -> Unit)? = null,
    managementOnly: Boolean = false,
    backdrop: Backdrop? = null
) {
    // Preserve the unsaved selection when global operations update the Room flow.
    var chosen by remember { mutableStateOf(selected.toSet()) }
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    val names = (available + chosen).distinct().sorted()
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val compactHeight = screenHeight < 480
    val sheetOffset = remember { Animatable(1f) }
    var isClosing by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<List<String>?>(null) }
    val secondary = renaming != null || deleting != null
    val dismiss = { if (secondary) { renaming = null; deleting = null } else { isClosing = true } }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler(onBack = dismiss)
    LaunchedEffect(Unit) { sheetOffset.animateBottomSheetIn() }
    LaunchedEffect(isClosing) {
        if (isClosing) {
            sheetOffset.animateBottomSheetOut()
            pendingSave?.let(onSave) ?: onDismiss()
        }
    }

    // Match the reader's table-of-contents container; menus use the whole page.
    LiquidGlassMenuHost(Modifier.fillMaxSize().imePadding(), backdrop = backdrop) {
        Box(Modifier.fillMaxSize()
            .background(AppColors.Scrim.copy(alpha = 0.20f * (1f - sheetOffset.value.coerceIn(0f, 1f))))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = dismiss))
        LiquidGlassColumnSheetContainer(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .heightIn(max = screenHeight.dp * if (compactHeight) 0.94f else 0.82f)
                .materialBottomSheetMotion(sheetOffset.value, predictiveBackProgress),
            contentModifier = Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = if (compactHeight) 10.dp else 20.dp),
            fallbackColor = AppColors.CardBg,
            shape = AppRoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            backdrop = backdrop
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        renaming != null -> stringResource(R.string.annotation_tag_rename)
                        deleting != null -> stringResource(R.string.annotation_tag_delete_title, deleting.orEmpty())
                        managementOnly -> stringResource(R.string.annotation_tag_manage)
                        else -> stringResource(R.string.annotation_tags)
                    },
                    color = AppColors.TextPrimary, fontSize = AppType.Section,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                LiquidGlassIconButton(imageVector = AppIcons.X,
                    contentDescription = stringResource(R.string.reader_close), onClick = dismiss,
                    size = 44.dp, iconSize = 20.dp, normalContainerColor = AppColors.BgGray)
            }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        secondary -> {
                            Text(stringResource(R.string.annotation_tag_global_hint),
                                color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
                            if (renaming != null) AnnotationTagNameField(renameText, { renameText = it }, stringResource(R.string.annotation_tag_new))
                        }
                        else -> {
                            names.forEach { name ->
                                AnnotationTagChoice(name, name in chosen, managementOnly,
                                    onChecked = { checked -> chosen = if (checked) chosen + name else chosen - name },
                                    onRename = onRename?.let { { renaming = name; renameText = name } },
                                    onDelete = onDelete?.let { { deleting = name } })
                            }
                            if (!managementOnly) {
                                AnnotationTagNameField(newName, { newName = it }, stringResource(R.string.annotation_tag_new))
                                LiquidGlassTextButton(
                                    text = stringResource(R.string.annotation_tag_add),
                                    enabled = newName.isNotBlank(),
                                    onClick = { chosen = chosen + newName.trim(); newName = "" }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidGlassTextButton(stringResource(R.string.cancel), dismiss, Modifier.weight(1f))
                    LiquidGlassTextButton(
                        text = stringResource(if (deleting != null) R.string.delete else R.string.confirm),
                        modifier = Modifier.weight(1f), tintedColor = AppColors.Accent,
                        enabled = renaming == null || renameText.isNotBlank(),
                        onClick = {
                            val old = renaming
                            val removed = deleting
                            when {
                                old != null -> {
                                    val next = renameText.trim()
                                    if (next != old) {
                                        chosen = AnnotationTags.rename(chosen.toList(), old, next).toSet()
                                        onRename?.invoke(old, next)
                                    }
                                    renaming = null
                                }
                                removed != null -> {
                                    chosen = chosen - removed
                                    onDelete?.invoke(removed)
                                    deleting = null
                                }
                                else -> { pendingSave = AnnotationTags.normalize(chosen + newName); isClosing = true }
                            }
                        }
                    )
                }
        }
    }
}

@Composable
private fun AnnotationTagChoice(
    name: String, checked: Boolean, managementOnly: Boolean, onChecked: (Boolean) -> Unit,
    onRename: (() -> Unit)?, onDelete: (() -> Unit)?
) {
    val host = LocalLiquidGlassMenuHost.current
    val sourceId = remember { Any() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val renameLabel = stringResource(R.string.annotation_tag_rename)
    val deleteLabel = stringResource(R.string.delete)
    val moreSize = (24f * LocalDensity.current.fontScale.coerceIn(1f, 1.5f)).dp
    val menuWidth = annotationMenuWidth(buildList {
        if (onRename != null) add(renameLabel)
        if (onDelete != null) add(deleteLabel)
    }, extraWidth = 66.dp)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = 48.dp).clip(AppRoundedCornerShape(12.dp))
            .then(if (managementOnly) Modifier else Modifier.toggleable(checked, role = Role.Checkbox, onValueChange = onChecked))
            .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!managementOnly) Box(
                Modifier.size(22.dp).clip(AppRoundedCornerShape(7.dp))
                    .background(if (checked) AppColors.Accent else AppColors.BgGray)
                    .border(1.dp, if (checked) AppColors.Accent else AppColors.TextSecondary.copy(alpha = 0.35f), AppRoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (checked) Icon(AppIcons.Check, null, tint = AppColors.OnAccent, modifier = Modifier.size(15.dp))
            }
            Text(name, modifier = Modifier.weight(1f), color = AppColors.TextPrimary,
                fontSize = AppType.BodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (onRename != null || onDelete != null) LiquidGlassIconButton(
            imageVector = AppIcons.DotsThreeVertical,
            contentDescription = stringResource(R.string.annotation_tag_manage), size = moreSize, iconSize = 16.dp,
            modifier = Modifier.liquidGlassMenuAnchor(sourceId).onGloballyPositioned { bounds = it.boundsInRoot() },
            onClick = { host?.toggle(LiquidGlassMenuSpec(bounds, menuWidth, sourceId = sourceId, keepTriggerInteractive = false,
                items = buildList {
                    onRename?.let { add(LiquidGlassMenuItem(renameLabel, icon = AppIcons.PencilSimple, onClick = it)) }
                    onDelete?.let { add(LiquidGlassMenuItem(deleteLabel, icon = AppIcons.Trash, destructive = true, onClick = it)) }
                })) }
        )
    }
}

@Composable
private fun AnnotationTagNameField(value: String, onChange: (String) -> Unit, hint: String) {
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(fontSize = AppType.Body, color = AppColors.TextPrimary),
        cursorBrush = SolidColor(AppColors.Accent),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(AppRoundedCornerShape(14.dp))
            .background(AppColors.BgGray).padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner -> Box {
            if (value.isEmpty()) Text(hint, color = AppColors.TextSecondary, fontSize = AppType.Body)
            inner()
        } }
    )
}
