package com.huangder.lumibooks.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.dictionary.*
import com.huangder.lumibooks.ui.components.*
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.*
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun LocalDictionarySheet(query: String?, glassBackdrop: Backdrop?, forceSolid: Boolean,
    onDismiss: () -> Unit, onExternal: () -> Unit, onWebSearch: () -> Unit,
    viewModel: DictionaryViewModel = hiltViewModel()) {
    if (query == null) return
    val result by viewModel.lookup.collectAsState()
    val translation by viewModel.translation.collectAsState()
    LaunchedEffect(query) { viewModel.search(query) }
    DisposableEffect(Unit) { onDispose { viewModel.stopSearch() } }
    LocalDictionarySheetContent(query, result, glassBackdrop, forceSolid, onDismiss, onExternal, onWebSearch,
        translation = translation, onRetryTranslation = viewModel::retryTranslation) { descriptors, popupBackdrop, dismiss ->
        DictionarySourceDialog(descriptors, viewModel.repository, backdrop = popupBackdrop ?: glassBackdrop, onDismiss = dismiss)
    }
}

@Composable
internal fun LocalDictionarySheetContent(query: String, result: DictionaryLookupResult?, glassBackdrop: Backdrop?, forceSolid: Boolean,
    onDismiss: () -> Unit, onExternal: () -> Unit, onWebSearch: () -> Unit,
    translation: com.huangder.lumibooks.translation.TranslationState = com.huangder.lumibooks.translation.TranslationState.Hidden,
    onRetryTranslation: () -> Unit = {},
    sourceDialog: @Composable (List<DictionaryDescriptor>, Backdrop?, () -> Unit) -> Unit) {
    val context = LocalContext.current
    var detailId by remember(query) { mutableStateOf<String?>(null) }
    var showSource by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val offset = remember { Animatable(1f) }
    val motion = LocalMotionEnabled.current && !LocalEInkMode.current
    val listState = rememberLazyListState()
    val detailState = rememberLazyListState()
    // The AI item can appear before local lookup finishes. Do not let LazyColumn
    // anchor to that item when dictionary hits are subsequently inserted above it.
    LaunchedEffect(query, result) { listState.scrollToItem(0) }
    val popupBackdrop = rememberLayerBackdrop()
    val capturePopupBackdrop = LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current
    LaunchedEffect(query) { if (motion) offset.animateBottomSheetIn() else offset.snapTo(0f) }
    LaunchedEffect(closing) { if (closing) { if (motion) offset.animateBottomSheetOut() else offset.snapTo(1f); onDismiss() } }
    // Recreate the handler when changing dictionary levels. A completed predictive
    // back keeps progress at 1 until its UI is removed; level two stays in the
    // same sheet, so the level-one container must receive a fresh progress value.
    val backProgress = key(detailId) {
        ConfigurableBottomSheetBackHandler(enabled = !showSource) {
            if (detailId != null) detailId = null else closing = true
        }
    }
    val detail = result?.results?.firstOrNull { it.dictionary.id == detailId }
    LaunchedEffect(detailId) { detailState.scrollToItem(0) }
    Box(Modifier.fillMaxSize().then(if (capturePopupBackdrop) Modifier.layerBackdrop(popupBackdrop) else Modifier)) {
        Box(Modifier.fillMaxSize().background(AppColors.Scrim.copy(alpha = 0.20f * (1f - offset.value.coerceIn(0f, 1f))))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { closing = true })
        LiquidGlassColumnSheetContainer(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.7f)
                .materialBottomSheetMotion(offset.value, if (detailId == null) backProgress else 0f),
            contentModifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            fallbackColor = AppColors.CardBg, shape = AppRoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            backdrop = glassBackdrop, forceFallback = forceSolid || LocalEInkMode.current
        ) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(targetState = detailId, modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                    transitionSpec = { dictionaryPageTransition(initialState != null, targetState != null, motion).using(null) },
                    label = "dictionaryHeader") { pageId ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (pageId != null) LiquidGlassIconButton(imageVector = AppIcons.CaretLeft,
                            contentDescription = stringResource(R.string.dictionary_back_results),
                            onClick = { detailId = null }, size = 44.dp, iconSize = 20.dp)
                        Text(if (pageId == null) query else result?.results?.firstOrNull { it.dictionary.id == pageId }?.dictionary?.name
                            ?: stringResource(R.string.menu_dictionary), modifier = Modifier.weight(1f).padding(start = 8.dp),
                            fontSize = AppType.Section, color = AppColors.TextPrimary,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                LiquidGlassIconButton(imageVector = AppIcons.X, contentDescription = stringResource(R.string.close),
                    onClick = { closing = true }, size = 44.dp, iconSize = 20.dp)
            }
            Spacer(Modifier.height(12.dp))
            if (result == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            AnimatedContent(targetState = detailId, modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                transitionSpec = { dictionaryPageTransition(initialState != null, targetState != null, motion).using(null) },
                label = "dictionaryPage") { pageId ->
                if (pageId == null) {
                    Column(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("dictionary-results"),
                            state = listState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(result?.results.orEmpty(), key = { it.dictionary.id }) { hit ->
                                val interaction = remember { MutableInteractionSource() }
                                val pressed by interaction.collectIsPressedAsState()
                                Column(Modifier.fillMaxWidth().clip(AppRoundedCornerShape(8.dp))
                                    .background(AppColors.TextPrimary.copy(alpha = if (pressed) 0.08f else 0f))
                                    .clickable(interactionSource = interaction, indication = null) { detailId = hit.dictionary.id }
                                    .padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val first = hit.entries.first()
                                    Text(first.headword, color = AppColors.TextPrimary, fontSize = AppType.Section, fontWeight = FontWeight.Bold)
                                    if (first.pronunciation.isNotBlank()) Text(first.pronunciation, fontSize = AppType.BodySmall, color = AppColors.TextSecondary)
                                    Text(hit.entries.flatMap { it.senses }.take(2).joinToString("\n") { if (it.partOfSpeech.isBlank()) it.definition else "${it.partOfSpeech}  ${it.definition}" }, maxLines = 4, overflow = TextOverflow.Ellipsis,
                                        fontSize = AppType.Body, color = AppColors.TextPrimary)
                                    Text(hit.dictionary.name, fontSize = AppType.Caption, color = AppColors.TextSecondary)
                                    if (hit.partiallyFiltered) Text(stringResource(R.string.dictionary_partial_filtered), fontSize = AppType.Caption, color = AppColors.TextSecondary)
                                }
                                HorizontalDivider(color = AppColors.Divider)
                            }
                            if (result != null && result.results.isEmpty()) item {
                                Text(stringResource(when {
                                    result.filtered -> R.string.dictionary_all_filtered
                                    result.enabledCount == 0 -> R.string.dictionary_no_installed
                                    else -> R.string.dictionary_no_result
                                }), modifier = Modifier.padding(vertical = 20.dp), color = AppColors.TextSecondary)
                            }
                            if (result?.failedDictionaries?.isNotEmpty() == true) item {
                                Text(stringResource(R.string.dictionary_lookup_failed), color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
                            }
                            if (translation != com.huangder.lumibooks.translation.TranslationState.Hidden) item(key = "ai-translation") {
                                com.huangder.lumibooks.translation.TranslationResultCard(translation, onRetryTranslation)
                            }
                            item { DictionaryDisclaimerFooter { showSource = true } }
                        }
                        HorizontalDivider(color = AppColors.Divider)
                        DictionaryAction(stringResource(R.string.dictionary_web), AppIcons.Globe, onWebSearch)
                        HorizontalDivider(color = AppColors.Divider)
                        DictionaryAction(stringResource(R.string.dictionary_external), AppIcons.BookOpen, onExternal)
                        HorizontalDivider(color = AppColors.Divider)
                        DictionaryAction(stringResource(R.string.dictionary_manage), AppIcons.Gear) { openDictionarySettings(context) }
                    }
                } else {
                    val selected = result?.results?.firstOrNull { it.dictionary.id == pageId }
                    LazyColumn(Modifier.fillMaxSize(), state = detailState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(selected?.entries.orEmpty(), key = { it.id }) { entry -> DictionaryEntryContent(entry) }
                        if (selected?.partiallyFiltered == true && selected.entries.none { it.partiallyFiltered }) item {
                            Text(stringResource(R.string.dictionary_partial_filtered), fontSize = AppType.Caption, color = AppColors.TextSecondary)
                        }
                        if (result != null && selected == null) item {
                            Text(stringResource(R.string.dictionary_all_filtered), color = AppColors.TextSecondary)
                        }
                        item { DictionaryDisclaimerFooter { showSource = true } }
                    }
                }
            }
        }
    }
    if (showSource) {
        val descriptors = detail?.let { listOf(it.dictionary) }
            ?: result?.results?.map { it.dictionary }.orEmpty()
        sourceDialog(descriptors, popupBackdrop.takeIf { capturePopupBackdrop }) { showSource = false }
    }
}

private fun dictionaryPageTransition(fromDetail: Boolean, toDetail: Boolean, motion: Boolean) =
    if (!motion) {
        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
    } else if (!fromDetail && toDetail) {
        (slideInHorizontally(tween(280), initialOffsetX = { it }) +
            fadeIn(tween(280))) togetherWith
            (slideOutHorizontally(tween(280), targetOffsetX = { -it }) +
                fadeOut(tween(280)))
    } else {
        (slideInHorizontally(tween(280), initialOffsetX = { -it }) +
            fadeIn(tween(280))) togetherWith
            (slideOutHorizontally(tween(280), targetOffsetX = { it }) +
                fadeOut(tween(280)))
    }

@Composable
private fun DictionaryAction(text: String, icon: ImageVector, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).clickable(onClick = action).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = AppColors.TextSecondary)
        Text(text, color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
    }
}

@Composable
private fun DictionaryEntryContent(entry: DictionaryEntry) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(entry.headword, fontSize = AppType.Section, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        if (entry.pronunciation.isNotBlank()) Text(entry.pronunciation, color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
        if (entry.kind.isNotBlank()) Text(entry.kind, color = AppColors.TextSecondary, fontSize = AppType.Caption)
        entry.senses.forEachIndexed { index, sense ->
            if (sense.partOfSpeech.isNotBlank()) Text(sense.partOfSpeech, color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
            Text("${index + 1}. ${sense.definition}", color = AppColors.TextPrimary, fontSize = AppType.Body)
            sense.examples.forEach { example ->
                Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(example.text, color = AppColors.TextPrimary, fontSize = AppType.BodySmall)
                    if (example.translation.isNotBlank()) Text(example.translation, color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
                }
            }
            if (sense.related.isNotEmpty()) Text(sense.related.joinToString(" · "), color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
        }
        if (entry.partiallyFiltered) Text(stringResource(R.string.dictionary_partial_filtered), fontSize = AppType.Caption, color = AppColors.TextSecondary)
        if (entry.sourceUrl.startsWith("https://")) Text(stringResource(R.string.dictionary_entry_source),
            modifier = Modifier.clickable {
                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(entry.sourceUrl))) }
            }.padding(vertical = 10.dp), fontSize = AppType.Caption, color = AppColors.TextSecondary)
        HorizontalDivider(color = AppColors.Divider)
    }
}
