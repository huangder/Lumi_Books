package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.HighlightRule
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.reader.resolveReaderHighlightColor

/** The caller supplies a page-sized LiquidGlassMenuHost so menus can escape the filter row. */
@Composable
internal fun AnnotationListFilters(
    tags: List<String>, selectedTag: String?, onTagSelected: (String?) -> Unit,
    colors: List<String>, selectedColor: String?, onColorSelected: (String?) -> Unit,
    selectedLine: Int?, onLineSelected: (Int?) -> Unit, showLines: Boolean,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AnnotationFilterMenu(
            label = when (selectedTag) {
                null -> stringResource(R.string.annotation_tag_all)
                "" -> stringResource(R.string.annotation_tag_none)
                else -> selectedTag
            }, selected = selectedTag,
            choices = listOf(null to stringResource(R.string.annotation_tag_all), "" to stringResource(R.string.annotation_tag_none)) + tags.map { it to it },
            onSelect = onTagSelected
        )
        if (colors.isNotEmpty()) AnnotationFilterMenu(
            label = stringResource(R.string.annotation_tag_color), selected = selectedColor,
            choices = listOf(null to stringResource(R.string.annotation_tag_all_colors)) + colors.mapIndexed { i, color -> color to stringResource(R.string.annotation_tag_color_number, i + 1) },
            onSelect = onColorSelected,
            colorFor = { value -> value?.let { runCatching { Color(android.graphics.Color.parseColor(resolveReaderHighlightColor(it))) }.getOrNull() } }
        )
        if (showLines) {
            val lines = listOf(
                HighlightRule.UNDERLINE_STRAIGHT to stringResource(R.string.highlight_rule_underline_straight),
                HighlightRule.UNDERLINE_DOUBLE to stringResource(R.string.highlight_rule_underline_double),
                HighlightRule.UNDERLINE_WAVE to stringResource(R.string.highlight_rule_underline_wave),
                HighlightRule.UNDERLINE_DASHED to stringResource(R.string.highlight_rule_underline_dashed)
            )
            AnnotationFilterMenu(
                label = lines.firstOrNull { it.first == selectedLine }?.second ?: stringResource(R.string.annotation_tag_line),
                selected = selectedLine,
                choices = listOf(null to stringResource(R.string.annotation_tag_all_lines)) + lines,
                onSelect = onLineSelected
            )
        }
    }
}

@Composable
private fun <T> AnnotationFilterMenu(
    label: String, selected: T?, choices: List<Pair<T?, String>>, onSelect: (T?) -> Unit,
    colorFor: ((T?) -> Color?)? = null
) {
    val host = LocalLiquidGlassMenuHost.current
    val sourceId = remember { Any() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val selectedColor = colorFor?.invoke(selected)
    val textColor = AppColors.TextPrimary
    val accent = AppColors.Accent
    val menuWidth = annotationMenuWidth(choices.map { it.second },
        extraWidth = if (colorFor == null) 60.dp else 82.dp,
        fontSize = if (colorFor == null) 13.sp else AppType.BodySmall)
    val spec = LiquidGlassMenuSpec(
        anchorBounds = bounds, width = menuWidth, sourceId = sourceId, alignEnd = false,
        keepTriggerInteractive = false,
        items = choices.map { (value, text) -> LiquidGlassMenuItem(text, selected = value == selected) { onSelect(value) } },
        content = if (colorFor == null) null else { enabled, select ->
            Column {
                choices.forEach { (value, text) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .semantics { this.selected = value == selected }
                        .clickable(enabled = enabled) { select { onSelect(value) } }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(16.dp)) {
                            if (value == selected) Icon(AppIcons.Check, null, tint = accent, modifier = Modifier.fillMaxSize())
                        }
                        colorFor(value)?.let { Box(Modifier.size(18.dp).clip(CircleShape).background(it)) }
                        Text(text, color = textColor, fontSize = AppType.BodySmall, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    )
    LiquidGlassButton(
        onClick = { host?.toggle(spec) },
        modifier = Modifier.liquidGlassMenuAnchor(sourceId).onGloballyPositioned { bounds = it.boundsInRoot() },
        tintedColor = if (selected != null) AppColors.Accent else null,
        contentColor = if (selected != null) AppColors.OnAccent else AppColors.TextPrimary
    ) {
        selectedColor?.let { Box(Modifier.padding(end = 6.dp).size(14.dp).clip(CircleShape).background(it)) }
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontSize = AppType.BodySmall, modifier = Modifier.widthIn(max = 150.dp))
        Spacer(Modifier.width(6.dp))
        Icon(AppIcons.CaretDown, null, modifier = Modifier.size(14.dp))
    }
}

/** Measure translated labels at the current font scale, including icon/check gutters. */
@Composable
internal fun annotationMenuWidth(labels: List<String>, extraWidth: Dp, fontSize: TextUnit = 13.sp): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val available = (LocalConfiguration.current.screenWidthDp.dp - 32.dp).coerceAtLeast(80.dp)
    val textWidth = labels.maxOfOrNull {
        measurer.measure(it, style = TextStyle(fontSize = fontSize), maxLines = 1, softWrap = false).size.width
    } ?: 0
    return (with(density) { textWidth.toDp() } + extraWidth)
        .coerceIn(96.dp.coerceAtMost(available), 320.dp.coerceAtMost(available))
}
