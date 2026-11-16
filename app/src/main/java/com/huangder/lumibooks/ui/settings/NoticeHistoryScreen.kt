package com.huangder.lumibooks.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.EARLY_DONOR_NOTICE_ID
import com.huangder.lumibooks.domain.model.ReceivedNotice
import com.huangder.lumibooks.ui.components.DisclosureChevron
import com.huangder.lumibooks.ui.components.GroupedCard
import com.huangder.lumibooks.ui.components.LiquidGlassDialogHost
import com.huangder.lumibooks.ui.components.RemoteNoticeDialog
import com.huangder.lumibooks.ui.components.lumiRowClickable
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import java.text.DateFormat
import java.util.Date

internal const val NOTICE_HISTORY_CATEGORY = "notice_history"

@Composable
internal fun NoticeHistoryRoute(dataStoreManager: DataStoreManager, onBack: () -> Unit) {
    val notices by dataStoreManager.receivedNotices.collectAsStateWithLifecycle(initialValue = null)
    NoticeHistoryScreen(notices, onBack)
}

@Composable
internal fun NoticeHistoryScreen(notices: List<ReceivedNotice>?, onBack: () -> Unit) {
    var selectedNoticeId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedNotice = notices?.firstOrNull { it.id == selectedNoticeId }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
    }

    LiquidGlassDialogHost {
        CollapsingSettingsScaffold(
            title = stringResource(R.string.notice_history_title),
            onBack = onBack,
            lazyListState = rememberLazyListState(),
            lazyContent = {
                if (notices.isNullOrEmpty()) {
                    item {
                        Text(
                            text = stringResource(if (notices == null) R.string.loading else R.string.notice_history_empty),
                            modifier = Modifier.padding(horizontal = AppSpace.page, vertical = AppSpace.lg),
                            fontSize = AppType.BodySmall,
                            color = AppColors.TextSecondary
                        )
                    }
                } else {
                    items(notices, key = { it.id }) { notice ->
                        GroupedCard(modifier = Modifier.padding(bottom = AppSpace.sm)) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .lumiRowClickable { selectedNoticeId = notice.id }
                                    .padding(AppSpace.md),
                                horizontalArrangement = Arrangement.spacedBy(AppSpace.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(AppSpace.xs)
                                ) {
                                    Text(
                                        text = notice.title,
                                        fontSize = AppType.Body,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppColors.TextPrimary
                                    )
                                    Text(
                                        text = dateFormat.format(Date(notice.receivedAt)),
                                        fontSize = AppType.Footnote,
                                        color = AppColors.TextSecondary
                                    )
                                    Text(
                                        text = notice.message,
                                        fontSize = AppType.BodySmall,
                                        color = AppColors.TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                DisclosureChevron()
                            }
                        }
                    }
                }
            }
        )

        if (selectedNotice != null) {
            RemoteNoticeDialog(
                title = selectedNotice.title,
                message = selectedNotice.message,
                imageUrl = selectedNotice.imageUrl,
                imageResId = R.drawable.early_donor_claim_code.takeIf { selectedNotice.id == EARLY_DONOR_NOTICE_ID },
                dismissible = true,
                onConfirm = { selectedNoticeId = null }
            )
        }
    }
}
