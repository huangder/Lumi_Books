package com.huangder.lumibooks.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.SwipeRevealItem
import com.huangder.lumibooks.ui.components.lumiCardSurface
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType

@Composable
internal fun AuthorizedFoldersScreen(
    onBack: () -> Unit,
    viewModel: AuthorizedFoldersViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.removalFailed) {
        if (state.removalFailed) {
            Toast.makeText(context, R.string.authorized_folders_remove_failed, Toast.LENGTH_SHORT).show()
            viewModel.clearRemovalError()
        }
    }
    AuthorizedFoldersContent(state, onBack, viewModel::removeFolder)
}

@Composable
internal fun AuthorizedFoldersContent(
    state: AuthorizedFoldersUiState,
    onBack: () -> Unit,
    onRemove: (AuthorizedFolderEntry) -> Unit
) {
    CollapsingSettingsScaffold(
        title = stringResource(R.string.authorized_folders_title),
        onBack = onBack,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpace.md),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                stringResource(R.string.authorized_folders_hint),
                color = AppColors.TextSecondary,
                fontSize = AppType.Caption,
                lineHeight = 18.sp,
                modifier = Modifier.padding(
                    horizontal = AppSpace.lg - AppSpace.md,
                    vertical = AppSpace.sm
                )
            )
            when {
                state.loading -> Text(stringResource(R.string.loading), color = AppColors.TextSecondary)
                state.folders.isEmpty() -> Text(
                    stringResource(R.string.authorized_folders_empty),
                    color = AppColors.TextSecondary,
                    fontSize = AppType.Body,
                    modifier = Modifier.padding(vertical = AppSpace.lg)
                )
                else -> state.folders.forEach { folder ->
                    key(folder.uri, state.retryKey) {
                        SwipeRevealItem(onDelete = { onRemove(folder) }) {
                            val shape = AppRoundedCornerShape(AppRadius.lg)
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(shape)
                                    .lumiCardSurface(shape = shape)
                                    .padding(horizontal = AppSpace.md, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(AppIcons.FolderOpen, null, tint = AppColors.TextPrimary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        folder.name,
                                        fontSize = AppType.BodySmall,
                                        lineHeight = 20.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppColors.TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        folder.path,
                                        fontSize = AppType.Caption,
                                        lineHeight = 16.sp,
                                        color = AppColors.TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
