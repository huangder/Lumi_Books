package com.huangder.lumibooks.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
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
import com.huangder.lumibooks.domain.model.DEFAULT_APP_ACCENT_HEX
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.rememberLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.effectiveAppTheme
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
            val appAccentColor by dataStoreManager.appAccentColor.collectAsState(initial = DEFAULT_APP_ACCENT_HEX)
            val globalFontMode by dataStoreManager.globalFontMode.collectAsState(initial = "system")
            val liquidGlassTransparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = 0.55f)
            val liquidGlassHdrHighlightEnabled by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = false)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = "system")
            val predictiveBackEnabled by dataStoreManager.predictiveBackEnabled.collectAsState(initial = true)
            val isDark = when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val capability = rememberLiquidGlassCapability(view = LocalView.current)
            val resolvedAppTheme = effectiveAppTheme(appTheme, capability)

            EBookReaderTheme(
                darkTheme = isDark,
                dynamicColor = resolvedAppTheme == "material3",
                appTheme = resolvedAppTheme,
                appAccentColor = appAccentColor,
                liquidGlassTransparency = liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = liquidGlassHdrHighlightEnabled,
                globalFontMode = globalFontMode
            ) {
                com.huangder.lumibooks.ui.components.ConfigurableActivityBack(
                    predictiveBackEnabled = predictiveBackEnabled,
                    onBack = { finish() }
                )
                FeedbackPage(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun FeedbackPage(onBack: () -> Unit) {
    val context = LocalContext.current

    DetailPage(
        title = stringResource(R.string.feedback_title),
        onBack = onBack
    ) {
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
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://huangder.top")))
                }.onFailure {
                    Toast.makeText(context, R.string.network_error, Toast.LENGTH_LONG).show()
                }
            }
        )

        Spacer(Modifier.height(AppSpace.md))

        FeedbackLinkSection(
            label = stringResource(R.string.feedback_github_issues),
            title = stringResource(R.string.feedback_github_issues_desc),
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/huangder/Lumi_Books/issues"))
                    )
                }.onFailure {
                    Toast.makeText(context, R.string.network_error, Toast.LENGTH_LONG).show()
                }
            }
        )

        Spacer(Modifier.height(AppSpace.md))

        FeedbackLinkSection(
            label = stringResource(R.string.feedback_qq_channel),
            title = stringResource(R.string.feedback_qq_channel_desc),
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://pd.qq.com/s/29t6pms4a?b=9"))
                    )
                }.onFailure {
                    Toast.makeText(context, R.string.network_error, Toast.LENGTH_LONG).show()
                }
            }
        )
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
