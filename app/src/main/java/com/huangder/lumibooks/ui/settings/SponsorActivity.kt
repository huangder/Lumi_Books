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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.github.Contributor
import com.huangder.lumibooks.data.github.GitHubContributorsClient
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.DEFAULT_APP_ACCENT_HEX
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.rememberLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.effectiveAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SponsorActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var githubContributorsClient: GitHubContributorsClient

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
                SponsorPage(
                    githubContributorsClient = githubContributorsClient,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
private fun SponsorPage(
    githubContributorsClient: GitHubContributorsClient,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var contributors by remember { mutableStateOf(fallbackContributors) }
    var paymentMethod by rememberSaveable { mutableStateOf(PaymentMethod.WECHAT.name) }
    val selectedPayment = PaymentMethod.valueOf(paymentMethod)

    LaunchedEffect(Unit) {
        val fetched = runCatching { githubContributorsClient.fetchContributors() }.getOrNull()
        if (!fetched.isNullOrEmpty()) {
            // spencer1012 等人的代码经手动合入、无 commit 历史，不在 API 名单里，需从兜底名单补齐
            val known = fetched.map { it.login.lowercase() }.toSet()
            contributors = fetched + fallbackContributors.filter { it.login.lowercase() !in known }
        }
    }

    DetailPage(
        title = stringResource(R.string.sponsor_title),
        onBack = onBack
    ) {
        // 副标题
        Text(
            text = stringResource(R.string.sponsor_desc),
            fontSize = AppType.BodySmall,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(horizontal = AppSpace.lg)
        )

        Spacer(Modifier.height(AppSpace.md))

        SponsorPaymentTagSwitcher(
            selectedPayment = selectedPayment,
            onPaymentSelected = { paymentMethod = it.name },
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
                painter = painterResource(
                    id = if (selectedPayment == PaymentMethod.WECHAT) {
                        R.drawable.donation_qr
                    } else {
                        R.drawable.paypal_donation_qr
                    }
                ),
                contentDescription = stringResource(
                    if (selectedPayment == PaymentMethod.WECHAT) {
                        R.string.sponsor_wechat_qr_desc
                    } else {
                        R.string.sponsor_paypal_qr_desc
                    }
                ),
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(AppRadius.md)),
                contentScale = ContentScale.Fit
            )

            Spacer(Modifier.height(AppSpace.md))

            // 文案
            Text(
                text = stringResource(R.string.sponsor_quote),
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

            // 绘屿浮的赞赏码
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
                    text = stringResource(
                        if (selectedPayment == PaymentMethod.WECHAT) {
                            R.string.sponsor_code_label
                        } else {
                            R.string.sponsor_paypal_code_label
                        }
                    ),
                    fontSize = AppType.Body,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        Spacer(Modifier.height(AppSpace.lg))

        // 感谢列表
        CreditSection(
            title = stringResource(R.string.sponsor_thanks_title),
            names = listOf("雋乂、匿名、匿名、匿名、Jun.、BennyBlack、百年老字号、匿名、匿名、白飘飘")
        )

        Spacer(Modifier.height(AppSpace.md))

        // 开发人员
        DeveloperSection(
            contributors = contributors,
            onContributorClick = { contributor ->
                val url = if (contributor.login.equals("huangder", ignoreCase = true)) {
                    "https://xhslink.com/m/5AbhNhfh7hE"
                } else {
                    contributor.htmlUrl.ifBlank { "https://github.com/${contributor.login}" }
                }
                openExternalLink(context, url)
            }
        )
    }
}

@Composable
private fun SponsorPaymentTagSwitcher(
    selectedPayment: PaymentMethod,
    onPaymentSelected: (PaymentMethod) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val options = listOf(
        PaymentMethod.WECHAT to R.string.sponsor_payment_wechat,
        PaymentMethod.PAYPAL to R.string.sponsor_payment_paypal
    )
    val selectedIndex = options.indexOfFirst { it.first == selectedPayment }.coerceAtLeast(0)
    val indicatorProgress by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(200),
        label = "sponsorPaymentIndicator"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.BgGray)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(1f / options.size)
                .fillMaxSize()
                .graphicsLayer {
                    translationX = indicatorProgress * size.width
                }
                .clip(RoundedCornerShape(18.dp))
                .shadow(2.dp, RoundedCornerShape(18.dp))
                .background(if (isDark) AppColors.CardBg else Color.White)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEach { (payment, labelRes) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onPaymentSelected(payment) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(labelRes),
                        fontSize = AppType.BodySmall,
                        fontWeight = if (payment == selectedPayment) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (payment == selectedPayment) AppColors.TextPrimary else AppColors.TextSecondary
                    )
                }
            }
        }
    }
}

private enum class PaymentMethod {
    WECHAT,
    PAYPAL
}

@Composable
private fun DeveloperSection(
    contributors: List<Contributor>,
    onContributorClick: (Contributor) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg)
    ) {
        Text(
            text = stringResource(R.string.sponsor_dev_title),
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(bottom = AppSpace.xs)
        )
        contributors.forEachIndexed { index, contributor ->
            if (index > 0) Spacer(Modifier.height(AppSpace.sm))
            DeveloperCard(
                contributor = contributor,
                onClick = { onContributorClick(contributor) }
            )
        }
    }
}

@Composable
private fun DeveloperCard(contributor: Contributor, onClick: () -> Unit) {
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
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(AppColors.TextSecondary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = contributor.avatarUrl.withAvatarSize(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.width(AppSpace.sm))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = contributor.login,
                fontSize = AppType.Body,
                color = AppColors.TextPrimary
            )
            if (contributor.login.equals("huangder", ignoreCase = true)) {
                Spacer(Modifier.width(AppSpace.xs))
                Text(
                    text = stringResource(R.string.sponsor_maintainer_badge),
                    fontSize = AppType.Caption,
                    color = AppColors.TextSecondary
                )
            }
        }
        Spacer(Modifier.width(AppSpace.sm))
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun openExternalLink(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        Toast.makeText(context, R.string.network_error, Toast.LENGTH_LONG).show()
    }
}

private fun String.withAvatarSize(size: Int = 96): String {
    val separator = if (contains("?")) "&" else "?"
    return "$this${separator}s=$size"
}

// 首次打开或网络失败时的回退数据（内容从 GitHub API 获取后会用最新数据替换）
private val fallbackContributors = listOf(
    Contributor(
        login = "huangder",
        avatarUrl = "https://avatars.githubusercontent.com/u/69392191?v=4",
        htmlUrl = "https://github.com/huangder",
        contributions = 0
    ),
    Contributor(
        login = "Corundum-Ling",
        avatarUrl = "https://avatars.githubusercontent.com/u/64763642?v=4",
        htmlUrl = "https://github.com/Corundum-Ling",
        contributions = 0
    ),
    Contributor(
        login = "spencer1012",
        avatarUrl = "https://avatars.githubusercontent.com/u/264089283?v=4",
        htmlUrl = "https://github.com/spencer1012",
        contributions = 0
    )
)

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
