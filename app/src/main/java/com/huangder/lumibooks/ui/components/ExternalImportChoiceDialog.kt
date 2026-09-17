package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.AppColors

/** One authorized folder offered as a destination for an externally opened book. */
data class ExternalImportFolder(
    val name: String,
    val treeUri: String
)

/**
 * Asks how an externally opened book should be stored, but only when an authorized folder is
 * available. Without one the caller keeps copying files into the app without asking.
 */
@Composable
fun ExternalImportChoiceDialog(
    fileCount: Int,
    folders: List<ExternalImportFolder>,
    onCopyIntoApp: () -> Unit,
    onMoveToFolder: (ExternalImportFolder) -> Unit,
    onDismiss: () -> Unit
) {
    val showFolderList = folders.size > 1

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 460.dp),
        shape = RoundedCornerShape(32.dp),
        // Keep the backdrop visible through the surface so the dialog reads as real glass.
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.70f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.external_import_title),
                color = AppColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (fileCount > 1) {
                    stringResource(R.string.external_import_message_multiple, fileCount)
                } else {
                    stringResource(R.string.external_import_message)
                },
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(4.dp))
            if (showFolderList) {
                if (folders.size > 1) {
                    Text(
                        text = stringResource(R.string.external_import_choose_folder),
                        color = AppColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                folders.forEach { folder ->
                    ExternalImportOption(
                        title = stringResource(R.string.external_import_move_to_folder, folder.name),
                        detail = null,
                        primary = false
                    ) {
                        onMoveToFolder(folder)
                    }
                }
                if (folders.size > 1) {
                    ExternalImportOption(
                        title = stringResource(R.string.external_import_copy),
                        detail = stringResource(R.string.external_import_copy_detail),
                        primary = false
                    ) {
                        onCopyIntoApp()
                    }
                }
            } else {
                ExternalImportOption(
                    title = stringResource(R.string.external_import_copy),
                    detail = stringResource(R.string.external_import_copy_detail),
                    primary = true
                ) {
                    onCopyIntoApp()
                }
                folders.firstOrNull()?.let { folder ->
                    ExternalImportOption(
                        title = stringResource(R.string.external_import_move_to_folder, folder.name),
                        detail = stringResource(R.string.external_import_move_detail),
                        primary = false
                    ) {
                        onMoveToFolder(folder)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            LiquidGlassTextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun ExternalImportOption(
    title: String,
    detail: String?,
    primary: Boolean,
    onClick: () -> Unit
) {
    LiquidGlassButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        tintedColor = if (primary) AppColors.Accent else AppColors.BgGray,
        prominentShadow = primary,
        contentColor = if (primary) AppColors.OnAccent else AppColors.TextPrimary
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = if (primary) AppColors.OnAccent else AppColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            detail?.let {
                Text(
                    text = it,
                    color = if (primary) {
                        AppColors.OnAccent.copy(alpha = 0.78f)
                    } else {
                        AppColors.TextSecondary
                    },
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
