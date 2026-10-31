package com.huangder.lumibooks.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.AnnotationTags
import com.huangder.lumibooks.domain.model.TagTypes
import com.huangder.lumibooks.domain.model.ManagedTag
import com.huangder.lumibooks.data.local.entity.TagGroupEntity
import com.huangder.lumibooks.edition.EditionTagManagementButton
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.animation.VisualMotion
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

private data class TagSheetPage(
    val categories: Boolean,
    val groupId: String?,
    val renameKey: String?,
    val deleteKey: String?
) {
    val secondary: Boolean get() = renameKey != null || deleteKey != null
}

@Composable
fun AnnotationTagSheet(
    selected: List<String>, available: List<String>,
    onSave: suspend (List<String>) -> Unit, onDismiss: () -> Unit,
    onRename: ((String, String) -> Unit)? = null,
    onDelete: ((String) -> Unit)? = null,
    managementOnly: Boolean = false,
    backdrop: Backdrop? = null,
    onCreate: (suspend (String) -> Unit)? = null,
    catalogType: String = TagTypes.ANNOTATION,
    subtitle: String? = null,
    categoryCatalog: TagPickerCatalog? = null
) {
    val repository = rememberTagPickerRepository()
    val observedCatalog by (remember(repository, catalogType) { repository?.observeCatalog(catalogType) }
        ?: remember { kotlinx.coroutines.flow.flowOf(emptyList<ManagedTag>()) })
        .collectAsState(initial = emptyList())
    val observedGroups by (remember(repository, catalogType) { repository?.observeGroups(catalogType) }
        ?: remember { kotlinx.coroutines.flow.flowOf(emptyList<TagGroupEntity>()) })
        .collectAsState(initial = emptyList())
    val catalog = categoryCatalog?.tags ?: observedCatalog
    val groups = categoryCatalog?.groups ?: observedGroups
    val categorized = repository != null || categoryCatalog != null
    var groupOpen by rememberSaveable { mutableStateOf(false) }
    var groupId by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val saveFailed = stringResource(R.string.tag_picker_save_failed)
    val duplicateName = stringResource(R.string.tag_name_exists)
    val invalidName = stringResource(R.string.tag_management_invalid_name)
    fun operationError(code: String?): String = when (code) {
        "duplicate_name" -> duplicateName
        "invalid_name" -> invalidName
        else -> saveFailed
    }
    // Preserve the unsaved selection when global operations update the Room flow.
    var chosen by rememberSaveable { mutableStateOf(selected.toSet()) }
    var newName by rememberSaveable { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    var created by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var isCreating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(repository, catalogType) {
        repository?.mutations?.collect { mutation ->
            if (mutation.type == catalogType) {
                chosen = chosen.mapNotNull { if (it == mutation.oldKey) mutation.newKey else it }.toSet()
                created = created.mapNotNull { if (it == mutation.oldKey) mutation.newKey else it }.toSet()
            }
        }
    }
    LaunchedEffect(groups) {
        if (groupId != null && groups.none { it.id == groupId }) groupId = null
    }
    val catalogByKey = remember(catalog) { catalog.associateBy { it.key } }
    val names = remember(available, chosen, created, catalogByKey) {
        ((if (categorized) catalogByKey.keys.toList() else available) + chosen + created)
            .distinct().sortedBy { key -> catalogByKey[key]?.name ?: key }
    }
    val groupNames = groups.associate { it.id to it.name }
    val effectiveGroup: (String) -> String? = { key -> catalogByKey[key]?.groupId?.takeIf { it in groupNames } }
    val inCategories = categorized && !managementOnly && !groupOpen
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val motionEnabled = LocalMotionEnabled.current && !LocalEInkMode.current
    val compactHeight = screenHeight < 480
    val sheetOffset = remember { Animatable(1f) }
    var isClosing by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<List<String>?>(null) }
    val secondary = renaming != null || deleting != null
    val dismiss = {
        if (pendingSave == null && !isCreating) {
            if (secondary) { renaming = null; deleting = null }
            else if (groupOpen) { groupOpen = false; newName = "" }
            else { isClosing = true }
        }
    }
    val close = { if (pendingSave == null && !isCreating) isClosing = true }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler(onBack = dismiss)
    LaunchedEffect(Unit) {
        if (motionEnabled) sheetOffset.animateBottomSheetIn() else sheetOffset.snapTo(0f)
    }
    LaunchedEffect(isClosing) {
        if (isClosing) {
            if (motionEnabled) sheetOffset.animateBottomSheetOut() else sheetOffset.snapTo(1f)
            onDismiss()
        }
    }
    LaunchedEffect(pendingSave) {
        pendingSave?.let { tags ->
            try {
                onSave(tags)
                isClosing = true
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                error = saveFailed
                pendingSave = null
            }
        }
    }

    val page = TagSheetPage(inCategories, groupId, renaming, deleting)
    val sheetBackdrop = backdrop ?: LocalLiquidGlassBackdrop.current
    // Keep the sheet above page chrome, while retaining its page backdrop.
    FullscreenOverlay {
    LiquidGlassMenuHost(Modifier.fillMaxSize().imePadding(), backdrop = sheetBackdrop) {
        Box(Modifier.fillMaxSize()
            .background(AppColors.Scrim.copy(alpha = 0.28f * (1f - sheetOffset.value.coerceIn(0f, 1f))))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = close))
        LiquidGlassColumnSheetContainer(
            modifier = adaptiveSheetFrame(maxHeight = screenHeight.dp * if (compactHeight) 0.94f else 0.82f)
                .materialBottomSheetMotion(sheetOffset.value, predictiveBackProgress),
            contentModifier = Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = if (compactHeight) 10.dp else 20.dp),
            fallbackColor = AppColors.CardBg,
            shape = AppRoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            backdrop = sheetBackdrop
        ) {
            AppSheetHeader(
                    title = when {
                        renaming != null -> stringResource(R.string.annotation_tag_rename)
                        deleting != null -> stringResource(R.string.annotation_tag_delete_title, catalog.firstOrNull { it.key == deleting }?.name ?: deleting.orEmpty())
                        managementOnly -> stringResource(R.string.annotation_tag_manage)
                        groupOpen -> groupNames[groupId] ?: stringResource(R.string.tag_picker_uncategorized)
                        catalogType == TagTypes.BOOK -> stringResource(R.string.tag_sheet_title)
                        else -> stringResource(R.string.annotation_tags)
                    },
                    onClose = close,
                    onBack = if (secondary || groupOpen) dismiss else null,
                    actions = {
                        if (repository != null && !secondary) {
                            EditionTagManagementButton(catalogType, Modifier.padding(start = 8.dp))
                        }
                    }
            )
                subtitle?.let { Text(it, color = AppColors.TextSecondary, fontSize = AppType.Caption) }
                error?.let { Text(it, color = AppColors.Destructive, fontSize = AppType.BodySmall) }
                Spacer(Modifier.height(12.dp))
                AnimatedContent(
                    targetState = page,
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                    transitionSpec = {
                        if (!motionEnabled) {
                            EnterTransition.None.togetherWith(ExitTransition.None)
                                .using(SizeTransform(clip = true) { _, _ -> snap() })
                        } else {
                            (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 5 })
                                .togetherWith(fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 5 })
                                .using(SizeTransform(clip = true))
                        }
                    },
                    label = "tagSheetContent"
                ) { currentPage ->
                val visibleNames = if (!categorized || managementOnly) names
                    else names.filter { effectiveGroup(it) == currentPage.groupId }
                LazyColumn(Modifier.fillMaxWidth().testTag("annotation-tag-choices"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        currentPage.secondary -> {
                            item(key = "tag-operation", contentType = "operation") {
                                Column(Modifier.fillMaxWidth().clip(AppRoundedCornerShape(AppRadius.lg))
                                    .animateItem(fadeInSpec = if (motionEnabled) VisualMotion.tween(180) else snap(),
                                        placementSpec = if (motionEnabled) VisualMotion.tween(220) else snap(),
                                        fadeOutSpec = if (motionEnabled) VisualMotion.tween(140) else snap())
                                    .lumiCardSurface(shape = AppRoundedCornerShape(AppRadius.lg)).padding(16.dp)) {
                                    Text(stringResource(R.string.annotation_tag_global_hint),
                                        color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
                                    if (currentPage.renameKey != null) {
                                        Spacer(Modifier.height(12.dp))
                                        AnnotationTagNameField(renameText, { renameText = it }, stringResource(R.string.annotation_tag_new), enabled = !isCreating)
                                    }
                                }
                            }
                        }
                        currentPage.categories -> {
                            items(listOf<String?>(null) + groups.map { it.id }, key = { "group:${it ?: "unclassified"}" }) { id ->
                                val members = names.filter { effectiveGroup(it) == id }
                                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                    .animateItem(fadeInSpec = if (motionEnabled) VisualMotion.tween(180) else snap(),
                                        placementSpec = if (motionEnabled) VisualMotion.tween(220) else snap(),
                                        fadeOutSpec = if (motionEnabled) VisualMotion.tween(140) else snap())
                                    .clip(AppRoundedCornerShape(AppRadius.lg))
                                    .lumiCardSurface(shape = AppRoundedCornerShape(AppRadius.lg)).clickable {
                                    groupId = id; groupOpen = true; newName = ""
                                }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(AppIcons.Folder, null, tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(groupNames[id] ?: stringResource(R.string.tag_picker_uncategorized), color = AppColors.TextPrimary, fontSize = AppType.Body)
                                        Text(stringResource(R.string.tag_picker_group_count, members.size, members.count { it in chosen }), color = AppColors.TextSecondary, fontSize = AppType.Caption)
                                    }
                                    Icon(AppIcons.CaretRight, null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        else -> {
                            items(visibleNames, key = { "tag:$it" }, contentType = { "tag" }) { name ->
                                AnnotationTagChoice(catalogByKey[name]?.name ?: name, name in chosen, managementOnly,
                                    onChecked = { checked -> chosen = if (checked) chosen + name else chosen - name },
                                    onRename = if (onRename != null || repository != null) ({ renaming = name; renameText = catalogByKey[name]?.name ?: name }) else null,
                                    onDelete = if (onDelete != null || repository != null) ({ deleting = name }) else null,
                                    modifier = Modifier.animateItem(fadeInSpec = if (motionEnabled) VisualMotion.tween(180) else snap(),
                                        placementSpec = if (motionEnabled) VisualMotion.tween(220) else snap(),
                                        fadeOutSpec = if (motionEnabled) VisualMotion.tween(140) else snap()))
                            }
                            if (!managementOnly || onCreate != null) {
                                item(key = "tag-create", contentType = "create") {
                                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)
                                        .animateItem(fadeInSpec = if (motionEnabled) VisualMotion.tween(180) else snap(),
                                            placementSpec = if (motionEnabled) VisualMotion.tween(220) else snap(),
                                            fadeOutSpec = if (motionEnabled) VisualMotion.tween(140) else snap()),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        AnnotationTagNameField(newName, { newName = it }, stringResource(R.string.annotation_tag_new),
                                            modifier = Modifier.weight(1f), enabled = !isCreating && pendingSave == null)
                                        LiquidGlassIconButton(
                                            imageVector = AppIcons.Plus,
                                            contentDescription = stringResource(R.string.annotation_tag_add),
                                            size = 44.dp, iconSize = 20.dp,
                                            contentColor = AppColors.Accent,
                                            enabled = newName.isNotBlank() && !isCreating && pendingSave == null,
                                            onClick = {
                                                val name = newName.trim()
                                                isCreating = true
                                                scope.launch {
                                                    try {
                                                        val result = repository?.createTag(catalogType, name, groupId)
                                                        if (result != null && !result.success) { error = operationError(result.message); return@launch }
                                                        if (repository == null) onCreate?.invoke(name)
                                                        val key = if (catalogType == TagTypes.BOOK && repository != null) {
                                                            repository.observeCatalog(catalogType).first().first { it.name == name }.key
                                                        } else name
                                                        created = created + key
                                                        if (!managementOnly) chosen = chosen + key
                                                        newName = ""
                                                        error = null
                                                    } catch (failure: Exception) {
                                                        if (failure is kotlinx.coroutines.CancellationException) throw failure
                                                        error = saveFailed
                                                    } finally {
                                                        isCreating = false
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiquidGlassTextButton(stringResource(R.string.cancel), close, Modifier.weight(1f),
                        enabled = pendingSave == null && !isCreating && !isClosing)
                    LiquidGlassTextButton(
                        text = stringResource(if (deleting != null) R.string.delete else R.string.confirm),
                        modifier = Modifier.weight(1f),
                        tintedColor = if (deleting != null) AppColors.Destructive else AppColors.Accent,
                        enabled = pendingSave == null && !isCreating && !isClosing && (renaming == null || renameText.isNotBlank()),
                        onClick = {
                            val old = renaming
                            val removed = deleting
                            when {
                                old != null -> {
                                    val next = renameText.trim()
                                    isCreating = true
                                    scope.launch {
                                        try {
                                            val result = repository?.renameTag(catalogType, old, next)
                                            if (result != null && !result.success) { error = operationError(result.message); return@launch }
                                            if (repository == null) {
                                                chosen = AnnotationTags.rename(chosen.toList(), old, next).toSet()
                                                created = AnnotationTags.rename(created.toList(), old, next).toSet()
                                                onRename?.invoke(old, next)
                                            }
                                            renaming = null
                                            error = null
                                        } catch (failure: Exception) {
                                            if (failure is kotlinx.coroutines.CancellationException) throw failure
                                            error = saveFailed
                                        } finally { isCreating = false }
                                    }
                                }
                                removed != null -> {
                                    isCreating = true
                                    scope.launch {
                                        try {
                                            val result = repository?.deleteTag(catalogType, removed)
                                            if (result != null && !result.success) { error = operationError(result.message); return@launch }
                                            chosen = chosen - removed
                                            created = created - removed
                                            if (repository == null) onDelete?.invoke(removed)
                                            deleting = null
                                            error = null
                                        } catch (failure: Exception) {
                                            if (failure is kotlinx.coroutines.CancellationException) throw failure
                                            error = saveFailed
                                        } finally { isCreating = false }
                                    }
                                }
                                else -> {
                                    isCreating = true
                                    scope.launch {
                                        try {
                                            if (newName.isNotBlank()) {
                                                val name = newName.trim()
                                                val result = repository?.createTag(catalogType, name, groupId)
                                                if (result != null && !result.success) { error = operationError(result.message); return@launch }
                                                if (repository == null) onCreate?.invoke(name)
                                                val key = if (catalogType == TagTypes.BOOK && repository != null) {
                                                    repository.observeCatalog(catalogType).first().first { it.name == name }.key
                                                } else name
                                                chosen = chosen + key
                                            }
                                            val currentKeys = repository?.observeCatalog(catalogType)?.first()?.map { it.key }?.toSet()
                                            pendingSave = AnnotationTags.normalize(chosen.filter { currentKeys == null || it in currentKeys })
                                        } catch (failure: Exception) {
                                            if (failure is kotlinx.coroutines.CancellationException) throw failure
                                            error = saveFailed
                                        } finally { isCreating = false }
                                    }
                                }
                            }
                        }
                    )
                }
        }
    }
    }
}

@Composable
private fun AnnotationTagChoice(
    name: String, checked: Boolean, managementOnly: Boolean, onChecked: (Boolean) -> Unit,
    onRename: (() -> Unit)?, onDelete: (() -> Unit)?, modifier: Modifier = Modifier
) {
    val host = LocalLiquidGlassMenuHost.current
    val sourceId = remember { Any() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val renameLabel = stringResource(R.string.annotation_tag_rename)
    val deleteLabel = stringResource(R.string.delete)
    val menuWidth = annotationMenuWidth(buildList {
        if (onRename != null) add(renameLabel)
        if (onDelete != null) add(deleteLabel)
    }, extraWidth = 66.dp)
    Row(modifier.fillMaxWidth().heightIn(min = 64.dp).clip(AppRoundedCornerShape(AppRadius.lg))
        .lumiCardSurface(shape = AppRoundedCornerShape(AppRadius.lg)).padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1f).heightIn(min = 56.dp).clip(AppRoundedCornerShape(12.dp))
            .then(if (managementOnly) Modifier else Modifier.toggleable(checked, role = Role.Checkbox, onValueChange = onChecked))
            .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!managementOnly) TagSelectionIndicator(checked)
            Text(name, modifier = Modifier.weight(1f), color = AppColors.TextPrimary,
                fontSize = AppType.BodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (onRename != null || onDelete != null) LiquidGlassIconButton(
            imageVector = AppIcons.DotsThreeVertical,
            contentDescription = stringResource(R.string.annotation_tag_manage), size = 44.dp, iconSize = 20.dp,
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
private fun AnnotationTagNameField(
    value: String, onChange: (String) -> Unit, hint: String,
    modifier: Modifier = Modifier, enabled: Boolean = true
) {
    TagTextField(value, onChange, hint, modifier, enabled)
}
