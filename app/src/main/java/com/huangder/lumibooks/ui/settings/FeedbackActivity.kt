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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.FangSong
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
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = "system")
            val isDark = when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            EBookReaderTheme(darkTheme = isDark) {
                FeedbackPage(onBack = { finish() })
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
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = AppColors.TextPrimary)
                }
                Spacer(Modifier.weight(1f))
                Text("问题反馈", fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = FangSong, color = AppColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }

            Spacer(Modifier.height(AppSpace.md))

            // 副标题
            Text(
                text = "欢迎提交 Bug 报告或功能建议，帮助我们做得更好",
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
                    contentDescription = "反馈表单二维码",
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(AppRadius.md)),
                    contentScale = ContentScale.Fit
                )

                Spacer(Modifier.height(AppSpace.md))

                // 文案
                Text(
                    text = "感谢您支持 Lumi 开发！",
                    fontSize = AppType.Body,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(AppSpace.lg))

            // 官网链接
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpace.lg)
            ) {
                Text(
                    text = "官网",
                    fontSize = AppType.Caption,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(bottom = AppSpace.xs)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(6.dp, RoundedCornerShape(AppRadius.md), ambientColor = Color(0x04000000), spotColor = Color(0x04000000))
                        .clip(RoundedCornerShape(AppRadius.md))
                        .background(AppColors.CardBg)
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://huangder.top"))
                            context.startActivity(intent)
                        }
                        .padding(AppSpace.md)
                ) {
                    Text(
                        text = "huangder.top",
                        fontSize = AppType.Body,
                        color = AppColors.Accent,
                        textDecoration = TextDecoration.Underline
                    )
                }
            }

            Spacer(Modifier.height(120.dp))
        }
    }
}
