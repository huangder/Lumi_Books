package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.LibraryTag
import com.huangder.lumibooks.domain.model.TagNameValidator
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookTagBottomSheet(
    tags: List<LibraryTag>,
    selectedTagIds: Set<String>,
    onDismiss: () -> Unit,
    onTagCheckedChange: (LibraryTag, Boolean) -> Unit,
    onCreateTag: (String, String?) -> Unit,
    onDeleteTag: (LibraryTag, Boolean) -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    var newTagName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    // 二级标签的删除覆盖层目标；一级标签统一走确认对话框
    var deleteTargetId by remember { mutableStateOf<String?>(null) }
    // 待删除的一级标签（长按触发，含子标签时弹两选项对话框）
    var deletePrimaryTarget by remember { mutableStateOf<LibraryTag?>(null) }
    // 行内添加子标签的一级标签 id
    var subTagInputParentId by remember { mutableStateOf<String?>(null) }
    var subTagName by remember { mutableStateOf("") }
    val nameRequired = stringResource(R.string.tag_name_required)
    val nameTooLong = stringResource(R.string.tag_name_too_long, TagNameValidator.MAX_LENGTH)

    LaunchedEffect(tags) {
        if (tags.none { it.id == deleteTargetId }) deleteTargetId = null
        deletePrimaryTarget = deletePrimaryTarget?.takeIf { target -> tags.any { it.id == target.id } }
        subTagInputParentId = subTagInputParentId?.takeIf { id -> tags.any { it.id == id } }
    }

    val createTag = {
        when {
            TagNameValidator.clean(newTagName).isEmpty() -> nameError = nameRequired
            !TagNameValidator.isValid(newTagName) -> nameError = nameTooLong
            else -> {
                onCreateTag(newTagName, null)
                newTagName = ""
                nameError = null
            }
        }
    }

    val createSubTag = { parentId: String ->
        when {
            TagNameValidator.clean(subTagName).isEmpty() -> nameError = nameRequired
            !TagNameValidator.isValid(subTagName) -> nameError = nameTooLong
            else -> {
                onCreateTag(subTagName, parentId)
                subTagName = ""
                subTagInputParentId = null
                nameError = null
            }
        }
    }

    val primaryTags = tags.filter { it.parentId == null }
    val childTagsByParent = tags.filter { it.parentId != null }.groupBy { it.parentId!! }
    val orphanChildTags = childTagsByParent.filterKeys { parentId -> tags.none { it.id == parentId } }.values.flatten()

    val sheetContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = AppSpace.lg, vertical = AppSpace.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.tag_sheet_title),
                    fontSize = AppType.Section,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                LiquidGlassIconButton(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.close),
                    onClick = onDismiss,
                    size = 40.dp,
                    iconSize = 20.dp,
                    normalContainerColor = AppColors.BgGray
                )
            }

            Spacer(Modifier.height(AppSpace.md))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(AppRadius.md))
                        .background(AppColors.BgGray)
                        .padding(horizontal = AppSpace.md),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (newTagName.isEmpty()) {
                        Text(
                            text = stringResource(R.string.tag_name_hint),
                            fontSize = AppType.Body,
                            color = AppColors.TextSecondary
                        )
                    }
                    BasicTextField(
                        value = newTagName,
                        onValueChange = {
                            newTagName = it
                            nameError = null
                        },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = AppType.Body,
                            color = AppColors.TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.size(AppSpace.sm))
                LiquidGlassIconButton(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.add_tag),
                    onClick = createTag,
                    size = 48.dp,
                    iconSize = 24.dp,
                    contentColor = AppColors.OnAccent,
                    normalContainerColor = AppColors.Accent,
                    liquidContainerColor = AppColors.Accent,
                    liquidScrimColor = AppColors.Accent
                )
            }

            nameError?.let { error ->
                Text(
                    text = error,
                    fontSize = AppType.Caption,
                    color = Color(0xFFC62828),
                    modifier = Modifier.padding(top = AppSpace.xs)
                )
            }

            Spacer(Modifier.height(AppSpace.lg))
            Text(
                text = stringResource(R.string.existing_tags),
                fontSize = AppType.Caption,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextSecondary
            )
            Spacer(Modifier.height(AppSpace.sm))

            if (tags.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_tags),
                    fontSize = AppType.BodySmall,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(vertical = AppSpace.lg)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
                ) {
                    primaryTags.forEach { primaryTag ->
                        PrimaryTagSection(
                            primaryTag = primaryTag,
                            childTags = childTagsByParent[primaryTag.id].orEmpty(),
                            selectedTagIds = selectedTagIds,
                            deleteTargetId = deleteTargetId,
                            subTagInputVisible = subTagInputParentId == primaryTag.id,
                            subTagName = subTagName,
                            onSubTagNameChange = {
                                subTagName = it
                                nameError = null
                            },
                            onToggle = { tag ->
                                deleteTargetId = null
                                onTagCheckedChange(tag, tag.id !in selectedTagIds)
                            },
                            onShowDelete = { tag ->
                                if (tag.parentId != null) {
                                    deleteTargetId = tag.id
                                } else {
                                    deletePrimaryTarget = tag
                                }
                            },
                            onDeleteChild = { tag ->
                                deleteTargetId = null
                                onDeleteTag(tag, false)
                            },
                            onShowSubTagInput = {
                                subTagName = ""
                                subTagInputParentId = primaryTag.id
                            },
                            onSubTagCreate = { createSubTag(primaryTag.id) },
                            onSubTagDismiss = {
                                subTagInputParentId = null
                                subTagName = ""
                            }
                        )
                    }
                    if (orphanChildTags.isNotEmpty()) {
                        PrimaryTagSection(
                            primaryTag = null,
                            childTags = orphanChildTags,
                            selectedTagIds = selectedTagIds,
                            deleteTargetId = deleteTargetId,
                            subTagInputVisible = false,
                            subTagName = subTagName,
                            onSubTagNameChange = {},
                            onToggle = { tag ->
                                deleteTargetId = null
                                onTagCheckedChange(tag, tag.id !in selectedTagIds)
                            },
                            onShowDelete = { tag -> deleteTargetId = tag.id },
                            onDeleteChild = { tag ->
                                deleteTargetId = null
                                onDeleteTag(tag, false)
                            },
                            onShowSubTagInput = {},
                            onSubTagCreate = {},
                            onSubTagDismiss = {}
                        )
                    }
                }
            }
        }
    }

    deletePrimaryTarget?.let { primaryTag ->
        val childCount = childTagsByParent[primaryTag.id].orEmpty().size
        DeletePrimaryTagDialog(
            tagName = primaryTag.name,
            childCount = childCount,
            onDismiss = { deletePrimaryTarget = null },
            onKeepChildren = {
                deletePrimaryTarget = null
                onDeleteTag(primaryTag, false)
            },
            onCascadeDelete = {
                deletePrimaryTarget = null
                onDeleteTag(primaryTag, true)
            }
        )
    }

    if (isLiquidGlass) {
        LiquidGlassDialog(
            onDismissRequest = onDismiss,
            alignment = Alignment.BottomCenter,
            shape = RoundedCornerShape(28.dp),
            contentScrimColor = AppColors.CardBg.copy(alpha = 0.82f),
            backgroundScrimColor = Color.Black.copy(alpha = 0.12f)
        ) {
            sheetContent()
        }
    } else {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = AppColors.CardBg,
            contentColor = AppColors.TextPrimary,
            scrimColor = Color.Black.copy(alpha = 0.12f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            sheetContent()
        }
    }
}

@Composable
private fun DeletePrimaryTagDialog(
    tagName: String,
    childCount: Int,
    onDismiss: () -> Unit,
    onKeepChildren: () -> Unit,
    onCascadeDelete: () -> Unit
) {
    LiquidGlassAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.delete_primary_tag_title, tagName),
                fontSize = AppType.Body,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
        },
        text = {
            Text(
                text = if (childCount > 0) {
                    stringResource(R.string.delete_tag_with_children_desc, childCount)
                } else {
                    stringResource(R.string.delete_tag_simple_desc)
                },
                fontSize = AppType.BodySmall,
                color = AppColors.TextSecondary
            )
        },
        confirmButton = {
            if (childCount > 0) {
                LiquidGlassTextButton(
                    text = stringResource(R.string.delete_tag_cascade),
                    tintedColor = Color(0xFFC62828),
                    onClick = onCascadeDelete
                )
            } else {
                LiquidGlassTextButton(
                    text = stringResource(R.string.delete),
                    tintedColor = Color(0xFFC62828),
                    onClick = onCascadeDelete
                )
            }
        },
        dismissButton = {
            if (childCount > 0) {
                LiquidGlassTextButton(
                    text = stringResource(R.string.delete_tag_keep_children),
                    onClick = onKeepChildren
                )
            } else {
                LiquidGlassTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrimaryTagSection(
    primaryTag: LibraryTag?,
    childTags: List<LibraryTag>,
    selectedTagIds: Set<String>,
    deleteTargetId: String?,
    subTagInputVisible: Boolean,
    subTagName: String,
    onSubTagNameChange: (String) -> Unit,
    onToggle: (LibraryTag) -> Unit,
    onShowDelete: (LibraryTag) -> Unit,
    onDeleteChild: (LibraryTag) -> Unit,
    onShowSubTagInput: () -> Unit,
    onSubTagCreate: () -> Unit,
    onSubTagDismiss: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.weight(1f, fill = false)) {
                if (primaryTag != null) {
                    TagSelectionChip(
                        tag = primaryTag,
                        selected = primaryTag.id in selectedTagIds,
                        deleteVisible = false,
                        onToggle = { onToggle(primaryTag) },
                        onShowDelete = { onShowDelete(primaryTag) },
                        onDelete = {}
                    )
                } else {
                    Text(
                        text = stringResource(R.string.tag_group_ungrouped),
                        fontSize = AppType.BodySmall,
                        color = AppColors.TextSecondary
                    )
                }
            }
            if (primaryTag != null) {
                Spacer(Modifier.width(AppSpace.sm))
                LiquidGlassIconButton(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.add_sub_tag),
                    onClick = onShowSubTagInput,
                    size = 30.dp,
                    iconSize = 16.dp,
                    normalContainerColor = AppColors.BgGray
                )
            }
        }

        AnimatedVisibility(visible = subTagInputVisible) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppSpace.sm, start = AppSpace.md)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(AppRadius.md))
                        .background(AppColors.BgGray)
                        .padding(horizontal = AppSpace.md),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (subTagName.isEmpty()) {
                        Text(
                            text = stringResource(R.string.sub_tag_name_hint),
                            fontSize = AppType.BodySmall,
                            color = AppColors.TextSecondary
                        )
                    }
                    BasicTextField(
                        value = subTagName,
                        onValueChange = onSubTagNameChange,
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = AppType.BodySmall,
                            color = AppColors.TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.size(AppSpace.sm))
                LiquidGlassIconButton(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.add_sub_tag),
                    onClick = onSubTagCreate,
                    size = 38.dp,
                    iconSize = 18.dp,
                    contentColor = AppColors.OnAccent,
                    normalContainerColor = AppColors.Accent,
                    liquidContainerColor = AppColors.Accent,
                    liquidScrimColor = AppColors.Accent
                )
                Spacer(Modifier.size(AppSpace.sm))
                LiquidGlassIconButton(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.cancel),
                    onClick = onSubTagDismiss,
                    size = 38.dp,
                    iconSize = 16.dp,
                    normalContainerColor = AppColors.BgGray
                )
            }
        }

        if (childTags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppSpace.sm, start = AppSpace.md),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                childTags.forEach { childTag ->
                    TagSelectionChip(
                        tag = childTag,
                        selected = childTag.id in selectedTagIds,
                        deleteVisible = deleteTargetId == childTag.id,
                        small = true,
                        onToggle = { onToggle(childTag) },
                        onShowDelete = { onShowDelete(childTag) },
                        onDelete = { onDeleteChild(childTag) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BatchBookTagSheet(
    tags: List<LibraryTag>,
    selectedBookCount: Int,
    onDismiss: () -> Unit,
    onCreateTag: (String) -> Unit,
    onApply: (Set<String>) -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    var selectedTagIds by remember { mutableStateOf(emptySet<String>()) }
    var newTagName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var pendingCreatedName by remember { mutableStateOf<String?>(null) }
    val nameRequired = stringResource(R.string.tag_name_required)
    val nameTooLong = stringResource(R.string.tag_name_too_long, TagNameValidator.MAX_LENGTH)

    LaunchedEffect(tags, pendingCreatedName) {
        val normalizedName = pendingCreatedName ?: return@LaunchedEffect
        tags.firstOrNull { TagNameValidator.normalized(it.name) == normalizedName }?.let { tag ->
            selectedTagIds = selectedTagIds + tag.id
            pendingCreatedName = null
        }
    }

    val createTag = {
        when {
            TagNameValidator.clean(newTagName).isEmpty() -> nameError = nameRequired
            !TagNameValidator.isValid(newTagName) -> nameError = nameTooLong
            else -> {
                pendingCreatedName = TagNameValidator.normalized(newTagName)
                onCreateTag(newTagName)
                newTagName = ""
                nameError = null
            }
        }
    }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = AppSpace.lg, vertical = AppSpace.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.batch_add_tags),
                        fontSize = AppType.Section,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.selected_books_count, selectedBookCount),
                        fontSize = AppType.Caption,
                        color = AppColors.TextSecondary
                    )
                }
                LiquidGlassIconButton(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.close),
                    onClick = onDismiss,
                    size = 40.dp,
                    iconSize = 20.dp,
                    normalContainerColor = AppColors.BgGray
                )
            }

            Spacer(Modifier.height(AppSpace.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(AppRadius.md))
                        .background(AppColors.BgGray)
                        .padding(horizontal = AppSpace.md),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (newTagName.isEmpty()) {
                        Text(
                            text = stringResource(R.string.tag_name_hint),
                            fontSize = AppType.Body,
                            color = AppColors.TextSecondary
                        )
                    }
                    BasicTextField(
                        value = newTagName,
                        onValueChange = {
                            newTagName = it
                            nameError = null
                        },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = AppType.Body,
                            color = AppColors.TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.size(AppSpace.sm))
                LiquidGlassIconButton(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.add_tag),
                    onClick = createTag,
                    enabled = selectedBookCount > 0,
                    size = 48.dp,
                    iconSize = 24.dp,
                    contentColor = AppColors.OnAccent,
                    normalContainerColor = AppColors.Accent,
                    liquidContainerColor = AppColors.Accent,
                    liquidScrimColor = AppColors.Accent
                )
            }

            nameError?.let { error ->
                Text(
                    text = error,
                    fontSize = AppType.Caption,
                    color = Color(0xFFC62828),
                    modifier = Modifier.padding(top = AppSpace.xs)
                )
            }

            Spacer(Modifier.height(AppSpace.lg))
            Text(
                text = stringResource(R.string.existing_tags),
                fontSize = AppType.Caption,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextSecondary
            )
            Spacer(Modifier.height(AppSpace.sm))
            if (tags.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_tags),
                    fontSize = AppType.BodySmall,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(vertical = AppSpace.lg)
                )
            } else {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    tags.forEach { tag ->
                        val selected = tag.id in selectedTagIds
                        LiquidGlassSurface(
                            shape = RoundedCornerShape(50),
                            fallbackColor = if (selected) {
                                AppColors.Accent.copy(alpha = 0.18f)
                            } else {
                                AppColors.BgGray
                            },
                            contentScrimColor = if (selected) {
                                AppColors.Accent.copy(alpha = 0.34f)
                            } else {
                                AppColors.CardBg.copy(alpha = 0.24f)
                            },
                            onClick = {
                                selectedTagIds = if (selected) {
                                    selectedTagIds - tag.id
                                } else {
                                    selectedTagIds + tag.id
                                }
                            },
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(
                                text = tag.name,
                                fontSize = AppType.BodySmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) AppColors.Accent else AppColors.TextPrimary,
                                modifier = Modifier.padding(horizontal = 15.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(AppSpace.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                LiquidGlassTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    contentColor = AppColors.TextSecondary
                )
                Spacer(Modifier.size(AppSpace.sm))
                LiquidGlassTextButton(
                    text = stringResource(R.string.confirm),
                    onClick = { onApply(selectedTagIds) },
                    enabled = selectedTagIds.isNotEmpty() && selectedBookCount > 0,
                    tintedColor = AppColors.Accent
                )
            }
        }
    }

    if (isLiquidGlass) {
        LiquidGlassDialog(
            onDismissRequest = onDismiss,
            alignment = Alignment.BottomCenter,
            shape = RoundedCornerShape(28.dp),
            contentScrimColor = AppColors.CardBg.copy(alpha = 0.82f),
            backgroundScrimColor = Color.Black.copy(alpha = 0.12f)
        ) {
            content()
        }
    } else {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = AppColors.CardBg,
            contentColor = AppColors.TextPrimary,
            scrimColor = Color.Black.copy(alpha = 0.12f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagSelectionChip(
    tag: LibraryTag,
    selected: Boolean,
    deleteVisible: Boolean,
    small: Boolean = false,
    onToggle: () -> Unit,
    onShowDelete: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    val chipHeight = if (small) 30.dp else 36.dp
    LiquidGlassSurface(
        shape = shape,
        fallbackColor = if (selected) AppColors.Accent.copy(alpha = 0.18f) else AppColors.BgGray,
        contentScrimColor = if (selected) {
            AppColors.Accent.copy(alpha = 0.34f)
        } else {
            AppColors.CardBg.copy(alpha = 0.24f)
        },
        interactive = false,
        modifier = Modifier
            .height(chipHeight)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
                onLongClick = onShowDelete
            )
    ) {
        Box(
            modifier = Modifier
                .height(chipHeight)
                .padding(horizontal = if (small) 11.dp else 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tag.name,
                fontSize = if (small) AppType.Caption else AppType.BodySmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) AppColors.Accent else AppColors.TextPrimary
            )
            AnimatedVisibility(
                visible = deleteVisible,
                enter = fadeIn() + scaleIn(
                    initialScale = 0.88f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f)
                ),
                exit = fadeOut() + scaleOut(targetScale = 0.92f),
                modifier = Modifier.matchParentSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(Color.Black.copy(alpha = 0.28f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDelete
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
