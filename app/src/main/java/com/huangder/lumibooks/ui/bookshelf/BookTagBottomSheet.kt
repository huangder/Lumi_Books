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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
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
    onCreateTag: (String) -> Unit,
    onDeleteTag: (LibraryTag) -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    var newTagName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }
    val nameRequired = stringResource(R.string.tag_name_required)
    val nameTooLong = stringResource(R.string.tag_name_too_long, TagNameValidator.MAX_LENGTH)

    LaunchedEffect(tags) {
        if (tags.none { it.id == deleteTargetId }) deleteTargetId = null
    }

    val createTag = {
        when {
            TagNameValidator.clean(newTagName).isEmpty() -> nameError = nameRequired
            !TagNameValidator.isValid(newTagName) -> nameError = nameTooLong
            else -> {
                onCreateTag(newTagName)
                newTagName = ""
                nameError = null
            }
        }
    }

    val sheetContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    tags.forEach { tag ->
                        TagSelectionChip(
                            tag = tag,
                            selected = tag.id in selectedTagIds,
                            deleteVisible = deleteTargetId == tag.id,
                            onToggle = {
                                deleteTargetId = null
                                onTagCheckedChange(tag, tag.id !in selectedTagIds)
                            },
                            onShowDelete = { deleteTargetId = tag.id },
                            onDelete = {
                                deleteTargetId = null
                                onDeleteTag(tag)
                            }
                        )
                    }
                }
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
