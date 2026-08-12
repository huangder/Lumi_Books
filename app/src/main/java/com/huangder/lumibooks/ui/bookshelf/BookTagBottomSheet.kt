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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.LibraryTag
import com.huangder.lumibooks.domain.model.TagNameValidator
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
    var deleteTargetTag by remember { mutableStateOf<LibraryTag?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSubTagInput by remember { mutableStateOf<String?>(null) }
    var subTagName by remember { mutableStateOf("") }
    var subTagError by remember { mutableStateOf<String?>(null) }
    val nameRequired = stringResource(R.string.tag_name_required)
    val nameTooLong = stringResource(R.string.tag_name_too_long, TagNameValidator.MAX_LENGTH)

    val primaryTags = tags.filter { it.parentId == null }
    val secondaryTags = tags.filter { it.parentId != null }

    LaunchedEffect(tags) {
        if (tags.none { it.id == deleteTargetTag?.id }) deleteTargetTag = null
    }

    val createPrimaryTag = {
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
            TagNameValidator.clean(subTagName).isEmpty() -> subTagError = nameRequired
            !TagNameValidator.isValid(subTagName) -> subTagError = nameTooLong
            else -> {
                onCreateTag(subTagName, parentId)
                subTagName = ""
                subTagError = null
                showSubTagInput = null
            }
        }
    }

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
                        textStyle = TextStyle(
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
                    onClick = createPrimaryTag,
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
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 未分类的二级标签（没有父标签的二级标签，理论不会出现）
                    // 一级标签及其子标签
                    primaryTags.forEach { primaryTag ->
                        val children = secondaryTags.filter { it.parentId == primaryTag.id }
                        PrimaryTagSection(
                            primaryTag = primaryTag,
                            children = children,
                            selectedTagIds = selectedTagIds,
                            deleteVisible = deleteTargetTag?.id == primaryTag.id,
                            subTagInputVisible = showSubTagInput == primaryTag.id,
                            subTagName = subTagName,
                            subTagError = subTagError,
                            onToggle = { onTagCheckedChange(primaryTag, primaryTag.id !in selectedTagIds) },
                            onShowDelete = { deleteTargetTag = primaryTag },
                            onDelete = { showDeleteDialog = true },
                            onShowSubTagInput = { showSubTagInput = primaryTag.id },
                            onSubTagNameChange = {
                                subTagName = it
                                subTagError = null
                            },
                            onSubTagCreate = { createSubTag(primaryTag.id) },
                            onSubTagDismiss = { showSubTagInput = null },
                            onChildToggle = { child, checked ->
                                onTagCheckedChange(child, checked)
                            },
                            onChildDelete = { child -> onDeleteTag(child, false) }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog && deleteTargetTag != null) {
        val primaryTag = deleteTargetTag!!
        val childCount = secondaryTags.count { it.parentId == primaryTag.id }
        if (childCount > 0) {
            DeletePrimaryTagDialog(
                primaryTag = primaryTag,
                childCount = childCount,
                isLiquidGlass = isLiquidGlass,
                onDismiss = { showDeleteDialog = false },
                onKeepChildren = {
                    showDeleteDialog = false
                    onDeleteTag(primaryTag, false)
                },
                onCascadeDelete = {
                    showDeleteDialog = false
                    onDeleteTag(primaryTag, true)
                }
            )
        } else {
            // 没有子标签，直接删除
            LaunchedEffect(Unit) {
                showDeleteDialog = false
                onDeleteTag(primaryTag, false)
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PrimaryTagSection(
    primaryTag: LibraryTag,
    children: List<LibraryTag>,
    selectedTagIds: Set<String>,
    deleteVisible: Boolean,
    subTagInputVisible: Boolean,
    subTagName: String,
    subTagError: String?,
    onToggle: () -> Unit,
    onShowDelete: () -> Unit,
    onDelete: () -> Unit,
    onShowSubTagInput: () -> Unit,
    onSubTagNameChange: (String) -> Unit,
    onSubTagCreate: () -> Unit,
    onSubTagDismiss: () -> Unit,
    onChildToggle: (LibraryTag, Boolean) -> Unit,
    onChildDelete: (LibraryTag) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.BgGray.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Primary tag row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Primary tag chip
            val primarySelected = primaryTag.id in selectedTagIds
            LiquidGlassSurface(
                shape = RoundedCornerShape(50),
                fallbackColor = if (primarySelected) {
                    AppColors.Accent.copy(alpha = 0.18f)
                } else {
                    AppColors.Accent.copy(alpha = 0.08f)
                },
                contentScrimColor = if (primarySelected) {
                    AppColors.Accent.copy(alpha = 0.34f)
                } else {
                    AppColors.CardBg.copy(alpha = 0.24f)
                },
                interactive = false,
                modifier = Modifier
                    .height(36.dp)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggle,
                        onLongClick = {
                            onShowDelete()
                            onDelete()
                        }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = primaryTag.name,
                        fontSize = AppType.BodySmall,
                        fontWeight = if (primarySelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (primarySelected) AppColors.Accent else AppColors.TextPrimary
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
                                .clip(RoundedCornerShape(50))
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
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Add sub-tag button
            LiquidGlassIconButton(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.add_sub_tag),
                onClick = onShowSubTagInput,
                size = 32.dp,
                iconSize = 16.dp,
                normalContainerColor = AppColors.BgGray,
                contentColor = AppColors.TextSecondary
            )
        }

        // Sub-tag input
        if (subTagInputVisible) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(AppRadius.sm))
                        .background(AppColors.BgGray)
                        .padding(horizontal = AppSpace.sm),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (subTagName.isEmpty()) {
                        Text(
                            text = stringResource(R.string.sub_tag_name_hint),
                            fontSize = AppType.Caption,
                            color = AppColors.TextSecondary
                        )
                    }
                    BasicTextField(
                        value = subTagName,
                        onValueChange = onSubTagNameChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = AppType.Caption,
                            color = AppColors.TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.size(AppSpace.xs))
                LiquidGlassIconButton(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.add_sub_tag),
                    onClick = onSubTagCreate,
                    size = 40.dp,
                    iconSize = 18.dp,
                    contentColor = AppColors.OnAccent,
                    normalContainerColor = AppColors.Accent,
                    liquidContainerColor = AppColors.Accent,
                    liquidScrimColor = AppColors.Accent
                )
            }
            subTagError?.let { error ->
                Text(
                    text = error,
                    fontSize = AppType.Caption,
                    color = Color(0xFFC62828),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        // Secondary tags
        if (children.isEmpty() && !subTagInputVisible) {
            Text(
                text = stringResource(R.string.no_sub_tags),
                fontSize = AppType.Caption,
                color = AppColors.TextSecondary,
                modifier = Modifier.padding(start = 8.dp, vertical = 4.dp)
            )
        } else {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                children.forEach { child ->
                    TagSelectionChip(
                        tag = child,
                        selected = child.id in selectedTagIds,
                        deleteVisible = false,
                        onToggle = {
                            onChildToggle(child, child.id !in selectedTagIds)
                        },
                        onShowDelete = {},
                        onDelete = { onChildDelete(child) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeletePrimaryTagDialog(
    primaryTag: LibraryTag,
    childCount: Int,
    isLiquidGlass: Boolean,
    onDismiss: () -> Unit,
    onKeepChildren: () -> Unit,
    onCascadeDelete: () -> Unit
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .padding(horizontal = AppSpace.lg, vertical = AppSpace.md)
                .widthIn(max = 360.dp)
        ) {
            Text(
                text = stringResource(R.string.delete_tag_primary),
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(AppSpace.sm))
            Text(
                text = stringResource(R.string.delete_tag_primary_desc, childCount),
                fontSize = AppType.Body,
                color = AppColors.TextSecondary
            )
            Spacer(Modifier.height(AppSpace.lg))
            LiquidGlassSurface(
                shape = RoundedCornerShape(AppRadius.md),
                fallbackColor = AppColors.BgGray,
                contentScrimColor = AppColors.CardBg.copy(alpha = 0.5f),
                onClick = onKeepChildren,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpace.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.delete_tag_keep_children),
                        fontSize = AppType.Body,
                        color = AppColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(AppSpace.sm))
            LiquidGlassSurface(
                shape = RoundedCornerShape(AppRadius.md),
                fallbackColor = Color(0xFFC62828).copy(alpha = 0.08f),
                contentScrimColor = Color(0xFFC62828).copy(alpha = 0.12f),
                onClick = onCascadeDelete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpace.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.delete_tag_cascade),
                        fontSize = AppType.Body,
                        color = Color(0xFFC62828),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(AppSpace.md))
            LiquidGlassTextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                contentColor = AppColors.TextSecondary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (isLiquidGlass) {
        LiquidGlassDialog(
            onDismissRequest = onDismiss,
            alignment = Alignment.Center,
            shape = RoundedCornerShape(28.dp),
            contentScrimColor = AppColors.CardBg.copy(alpha = 0.82f),
            backgroundScrimColor = Color.Black.copy(alpha = 0.12f)
        ) {
            content()
        }
    } else {
        LiquidGlassDialog(
            onDismissRequest = onDismiss,
            alignment = Alignment.Center,
            shape = RoundedCornerShape(28.dp),
            contentScrimColor = AppColors.CardBg.copy(alpha = 0.95f),
            backgroundScrimColor = Color.Black.copy(alpha = 0.12f)
        ) {
            content()
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

    val primaryTags = tags.filter { it.parentId == null }
    val secondaryTags = tags.filter { it.parentId != null }

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
                        textStyle = TextStyle(
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    primaryTags.forEach { primaryTag ->
                        val children = secondaryTags.filter { it.parentId == primaryTag.id }
                        BatchPrimaryTagSection(
                            primaryTag = primaryTag,
                            children = children,
                            selectedTagIds = selectedTagIds,
                            onPrimaryToggle = {
                                selectedTagIds = if (primaryTag.id in selectedTagIds) {
                                    selectedTagIds - primaryTag.id
                                } else {
                                    selectedTagIds + primaryTag.id
                                }
                            },
                            onChildToggle = { child ->
                                selectedTagIds = if (child.id in selectedTagIds) {
                                    selectedTagIds - child.id
                                } else {
                                    selectedTagIds + child.id
                                }
                            }
                        )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatchPrimaryTagSection(
    primaryTag: LibraryTag,
    children: List<LibraryTag>,
    selectedTagIds: Set<String>,
    onPrimaryToggle: () -> Unit,
    onChildToggle: (LibraryTag) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.BgGray.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Primary tag chip
        val primarySelected = primaryTag.id in selectedTagIds
        LiquidGlassSurface(
            shape = RoundedCornerShape(50),
            fallbackColor = if (primarySelected) {
                AppColors.Accent.copy(alpha = 0.18f)
            } else {
                AppColors.Accent.copy(alpha = 0.08f)
            },
            contentScrimColor = if (primarySelected) {
                AppColors.Accent.copy(alpha = 0.34f)
            } else {
                AppColors.CardBg.copy(alpha = 0.24f)
            },
            onClick = onPrimaryToggle,
            modifier = Modifier.height(36.dp)
        ) {
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = primaryTag.name,
                    fontSize = AppType.BodySmall,
                    fontWeight = if (primarySelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (primarySelected) AppColors.Accent else AppColors.TextPrimary
                )
            }
        }

        // Secondary tags
        if (children.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                children.forEach { child ->
                    val childSelected = child.id in selectedTagIds
                    LiquidGlassSurface(
                        shape = RoundedCornerShape(50),
                        fallbackColor = if (childSelected) {
                            AppColors.Accent.copy(alpha = 0.18f)
                        } else {
                            AppColors.BgGray
                        },
                        contentScrimColor = if (childSelected) {
                            AppColors.Accent.copy(alpha = 0.34f)
                        } else {
                            AppColors.CardBg.copy(alpha = 0.24f)
                        },
                        onClick = { onChildToggle(child) },
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = child.name,
                            fontSize = AppType.Caption,
                            fontWeight = if (childSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (childSelected) AppColors.Accent else AppColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagSelectionChip(
    tag: LibraryTag,
    selected: Boolean,
    deleteVisible: Boolean,
    onToggle: () -> Unit,
    onShowDelete: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(50)
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
            .height(36.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
                onLongClick = onShowDelete
            )
    ) {
        Box(
            modifier = Modifier
                .height(36.dp)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tag.name,
                fontSize = AppType.BodySmall,
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
