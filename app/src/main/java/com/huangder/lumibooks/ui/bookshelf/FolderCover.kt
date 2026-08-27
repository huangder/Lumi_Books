package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.huangder.lumibooks.domain.model.LibraryFolder
import com.huangder.lumibooks.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FolderCover(
    folder: LibraryFolder,
    cornerRadius: Dp,
    modifier: Modifier = Modifier
) {
    val coverPath = folder.coverPath
    var imageLoaded by remember(coverPath) { mutableStateOf(false) }
    val useDarkBadge by produceState<Boolean?>(initialValue = null, coverPath) {
        value = coverPath?.let { path ->
            withContext(Dispatchers.IO) {
                runCatching { FolderCoverLuminance.decodeUsesDarkBadge(path) }.getOrNull()
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(AppColors.CardBg),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(AppColors.Accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = folder.name,
                tint = AppColors.Accent,
                modifier = Modifier.fillMaxSize(0.56f)
            )
        }

        if (coverPath != null) {
            AsyncImage(
                model = coverPath,
                contentDescription = folder.name,
                contentScale = ContentScale.Crop,
                onLoading = { imageLoaded = false },
                onSuccess = { imageLoaded = true },
                onError = { imageLoaded = false },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (imageLoaded) {
            val iconColor = if (useDarkBadge == true) Color.Black else Color.White
            val contrastColor = if (useDarkBadge == true) Color.White else Color.Black
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(28.dp)
                    .shadow(
                        elevation = 3.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = contrastColor.copy(alpha = 0.42f),
                        spotColor = contrastColor.copy(alpha = 0.48f)
                    )
                    .clip(CircleShape)
                    .background(contrastColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
