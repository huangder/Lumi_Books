package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.huangder.lumibooks.domain.model.LibraryFolder
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FolderCover(
    folder: LibraryFolder,
    cornerRadius: Dp,
    previewBooks: List<Book?> = List(4) { null },
    modifier: Modifier = Modifier
) {
    val coverPath = folder.coverPath
    val hasPreview = coverPath == null && folder.previewBookIds?.isNotEmpty() == true
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
        if (hasPreview) {
            FolderBookPreview(
                previewBooks = previewBooks,
                modifier = Modifier.fillMaxSize()
            )
        } else {
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

@Composable
private fun FolderBookPreview(
    previewBooks: List<Book?>,
    modifier: Modifier = Modifier
) {
    val slotBackground = AppColors.BgGray
    val outerPadding = 8.dp
    val slotGap = 6.dp
    Column(
        modifier = modifier
            .background(AppColors.CardBg)
            .padding(outerPadding),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(slotGap)
    ) {
        repeat(2) { rowIndex ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(slotGap)
            ) {
                repeat(2) { columnIndex ->
                    val slotIndex = rowIndex * 2 + columnIndex
                    val book = previewBooks.getOrNull(slotIndex)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .background(slotBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        val coverPath = book?.coverPath?.takeIf { it.isNotBlank() }
                        if (coverPath != null) {
                            AsyncImage(
                                model = coverPath,
                                contentDescription = book.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (book != null) {
                            Text(
                                text = book.title,
                                color = AppColors.TextPrimary,
                                fontSize = 9.sp,
                                lineHeight = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
