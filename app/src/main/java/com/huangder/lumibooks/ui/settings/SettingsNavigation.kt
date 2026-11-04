package com.huangder.lumibooks.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.GroupedSectionTitle
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import kotlin.math.roundToInt

internal val LocalSettingsTargetSection = compositionLocalOf<String?> { null }
internal val LocalSettingsSectionNavigator = compositionLocalOf<SettingsSectionNavigator?> { null }

/** Section positions are measured inside the scrolling content, independently of its offset. */
internal class SettingsSectionNavigator {
    private var contentCoordinates: LayoutCoordinates? = null
    private val sections = mutableMapOf<String, LayoutCoordinates>()
    val offsets = mutableStateMapOf<String, Int>()

    fun updateContent(coordinates: LayoutCoordinates) {
        contentCoordinates = coordinates
        sections.forEach { (key, section) -> updateSection(key, section) }
    }

    fun updateSection(key: String, coordinates: LayoutCoordinates) {
        sections[key] = coordinates
        val content = contentCoordinates ?: return
        if (content.isAttached && coordinates.isAttached) {
            offsets[key] = content.localPositionOf(coordinates, Offset.Zero).y.roundToInt()
        }
    }

    fun removeSection(key: String) {
        sections.remove(key)
        offsets.remove(key)
    }
}

@Composable
internal fun SettingsSectionTitle(text: String, sectionKey: String) {
    val navigator = LocalSettingsSectionNavigator.current
    DisposableEffect(navigator, sectionKey) {
        onDispose { navigator?.removeSection(sectionKey) }
    }
    GroupedSectionTitle(
        text = text,
        modifier = Modifier.onGloballyPositioned { navigator?.updateSection(sectionKey, it) }
            .testTag("settings-section-$sectionKey")
    )
}

@Composable
internal fun MovedSettingsLink(
    text: String,
    category: String,
    targetSection: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    SettingsSearchHint(text, modifier) {
        context.startActivity(DetailActivity.createIntent(context, category, targetSection))
    }
}

/** Shared with Backup & Restore so relocated settings use the same quiet footer link. */
@Composable
internal fun SettingsSearchHint(
    linkText: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.page),
        horizontalArrangement = Arrangement.spacedBy(AppSpace.xs, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(AppSpace.xs)
    ) {
        Text(
            text = stringResource(R.string.backup_webdav_prompt),
            color = AppColors.TextSecondary,
            fontSize = AppType.Caption
        )
        Text(
            text = linkText,
            fontSize = AppType.Caption,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.Accent,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
        )
    }
}
