package com.huangder.lumibooks.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.FangSong
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FeedbackActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val appTheme by dataStoreManager.appTheme.collectAsState(initial = "lumi")
            val liquidGlassTransparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = 0.55f)
            val liquidGlassHdrHighlightEnabled by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = false)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = "system")
            val predictiveBackEnabled by dataStoreManager.predictiveBackEnabled.collectAsState(initial = true)
            val isDark = when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            EBookReaderTheme(
                darkTheme = isDark,
                dynamicColor = appTheme == "material3",
                appTheme = appTheme,
                liquidGlassTransparency = liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = liquidGlassHdrHighlightEnabled
            ) {
                com.huangder.lumibooks.ui.components.ConfigurableActivityBack(
                    predictiveBackEnabled = predictiveBackEnabled,
                    onBack = { finish() }
                )
                com.huangder.lumibooks.ui.components.LiquidGlassDialogHost(
                    modifier = Modifier.fillMaxSize()
                ) {
                    FeedbackPage(onBack = { finish() })
                }
            }
        }
    }
}

@Composable
private fun FeedbackPage(onBack: () -> Unit) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // 顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpace.sm, vertical = AppSpace.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassIconButton(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    onClick = onBack,
                    settingsBackButton = true
                )
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.feedback_title), fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = FangSong, color = AppColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }

            Spacer(Modifier.height(AppSpace.md))

            // 副标题
            Text(
                text = stringResource(R.string.feedback_desc),
                fontSize = AppType.BodySmall,
                color = AppColors.TextSecondary,
                modifier = Modifier.padding(horizontal = AppSpace.lg)
            )

            Spacer(Modifier.height(AppSpace.md))

            // 二维码卡片
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpace.lg)
                    .shadow(12.dp, RoundedCornerShape(AppRadius.lg), ambientColor = Color(0x06000000), spotColor = Color(0x06000000))
                    .clip(RoundedCornerShape(AppRadius.lg))
                    .background(AppColors.CardBg)
                    .padding(AppSpace.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 二维码
                Image(
                    painter = painterResource(id = R.drawable.feedback_qr),
                    contentDescription = stringResource(R.string.feedback_qr_desc),
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(AppRadius.md)),
                    contentScale = ContentScale.Fit
                )

                Spacer(Modifier.height(AppSpace.md))

                // 文案
                Text(
                    text = stringResource(R.string.feedback_thanks),
                    fontSize = AppType.Body,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(AppSpace.lg))

            FeedbackLinkSection(
                label = stringResource(R.string.feedback_website),
                title = "huangder.top",
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://huangder.top")))
                }
            )

            Spacer(Modifier.height(AppSpace.md))

            FeedbackLinkSection(
                label = stringResource(R.string.feedback_github_issues),
                title = stringResource(R.string.feedback_github_issues_desc),
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/huangder/Lumi_Books/issues"))
                    )
                }
            )

            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
private fun FeedbackLinkSection(
    label: String,
    title: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg)
    ) {
        Text(
            text = label,
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(bottom = AppSpace.xs)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(AppRadius.md), ambientColor = Color(0x04000000), spotColor = Color(0x04000000))
                .clip(RoundedCornerShape(AppRadius.md))
                .background(AppColors.CardBg)
                .clickable(onClick = onClick)
                .padding(AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = AppType.Body,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(AppSpace.sm))
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
