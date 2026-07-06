package com.huangder.lumibooks.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SponsorActivity : ComponentActivity() {

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
                SponsorPage(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun SponsorPage(onBack: () -> Unit) {
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
                Text("赞助开发", fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = FangSong, color = AppColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }

            Spacer(Modifier.height(AppSpace.md))

            // 副标题
            Text(
                text = "捐赠：如果你觉得不错的话，欢迎支持开发",
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
                    painter = painterResource(id = R.drawable.donation_qr),
                    contentDescription = "捐赠二维码",
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(AppRadius.md)),
                    contentScale = ContentScale.Fit
                )

                Spacer(Modifier.height(AppSpace.md))

                // 文案
                Text(
                    text = "\"如果觉得不错，请我吃个饭吧😊\"",
                    fontSize = AppType.Body,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "( ^ - ^ )o 🍱\"",
                    fontSize = AppType.Caption,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(AppSpace.md))

                // 给屿浮的赞赏码
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(AppRadius.capsule))
                        .background(Color(0xFFD4A542))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { /* 复制或分享 */ },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "给屿浮 的赞赏码",
                        fontSize = AppType.Body,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(AppSpace.lg))

            // 感谢列表
            CreditSection(
                title = "感谢列表（排名不分先后）",
                names = listOf("雋乂")
            )

            Spacer(Modifier.height(AppSpace.md))

            // 技术支持
            CreditSection(
                title = "技术支持（排名不分先后）",
                names = listOf("Corundum-Ling")
            )

            Spacer(Modifier.height(AppSpace.md))

            // 开发人员
            CreditSection(
                title = "开发人员",
                names = listOf("huangder")
            )

            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
private fun CreditSection(title: String, names: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg)
    ) {
        Text(
            text = title,
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
                .padding(AppSpace.md)
        ) {
            names.forEachIndexed { index, name ->
                if (index > 0) Spacer(Modifier.height(AppSpace.xs))
                Text(
                    text = name,
                    fontSize = AppType.Body,
                    color = AppColors.TextPrimary
                )
            }
        }
    }
}
