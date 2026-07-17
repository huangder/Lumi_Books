package com.huangder.lumibooks.ui.welcome

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.KaiTi
import androidx.compose.ui.res.stringResource

// 设计规范颜色 - 浅色模式
private val AccentColor = Color(0xFFE85D5D)
private val LightTextSecondary = Color(0xFF6E6E73)
private val LightBgGray = Color(0xFFF2F2F7)
private val LightBackground = Color(0xFFFBFBFC)
private val LightCardBg = Color.White

// 深色模式颜色
private val DarkTextSecondary = Color(0xFF98989D)
private val DarkBgGray = Color(0xFF2C2C2E)
private val DarkBackground = Color(0xFF000000)
private val DarkCardBg = Color(0xFF1C1C1E)

@Composable
fun WelcomeScreen(
    onContinue: () -> Unit,
    onExit: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showTermsOfService by remember { mutableStateOf(false) }

    // 根据深浅模式动态获取颜色
    val backgroundColor = if (isDark) DarkBackground else LightBackground
    val textPrimary = if (isDark) Color.White else Color.Black
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val bgGray = if (isDark) DarkBgGray else LightBgGray
    val cardBg = if (isDark) DarkCardBg else LightCardBg

    // 处理返回键：如果容器打开则关闭容器，否则退出应用
    val isSheetOpen = showPrivacyPolicy || showTermsOfService
    BackHandler(enabled = isSheetOpen) {
        showPrivacyPolicy = false
        showTermsOfService = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 上半部分 - 推到中间
            Spacer(modifier = Modifier.weight(1f))

            // App Icon
            Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = "Lumi Icon",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 欢迎使用（系统字体）
            Text(
                text = stringResource(R.string.welcome_title),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )

            // Lumi
            Text(
                text = "Lumi",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = AccentColor
            )

            // 下半部分 - 推到底部
            Spacer(modifier = Modifier.weight(1f))

            // 隐私说明区域
            Column(
                modifier = Modifier.padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 隐私文字（带可点击的链接）
                val privacyText = stringResource(R.string.welcome_privacy)
                val andText = stringResource(R.string.welcome_and)
                val termsText = stringResource(R.string.welcome_terms)
                val annotatedText = buildAnnotatedString {
                    append("Lumi 是一款纯粹的本地图书阅读器。我们绝不会在未经许可的情况下收集任何个人信息，且承诺永久不设网络账号服务。所有的阅读数据均储存在您的本地设备中，请务必定期做好数据备份以防丢失。Lumi 坚持最小权限原则，不会向您索取任何无关的敏感权限。点击\"继续\"按钮，即表示您已阅读并同意")

                    // 隐私政策链接
                    pushStringAnnotation(tag = "PRIVACY", annotation = "privacy")
                    withStyle(style = SpanStyle(color = AccentColor, fontWeight = FontWeight.Medium)) {
                        append(privacyText)
                    }
                    pop()

                    append(andText)

                    // 用户协议链接
                    pushStringAnnotation(tag = "TERMS", annotation = "terms")
                    withStyle(style = SpanStyle(color = AccentColor, fontWeight = FontWeight.Medium)) {
                        append(termsText)
                    }
                    pop()

                    append("。")
                }

                var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                BasicText(
                    text = annotatedText,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        color = textSecondary,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    ),
                    onTextLayout = { textLayoutResult = it },
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures { offset ->
                            textLayoutResult?.let { layoutResult ->
                                val position = layoutResult.getOffsetForPosition(offset)
                                val privacyAnnotations = annotatedText.getStringAnnotations(
                                    tag = "PRIVACY",
                                    start = position,
                                    end = position
                                )
                                val termsAnnotations = annotatedText.getStringAnnotations(
                                    tag = "TERMS",
                                    start = position,
                                    end = position
                                )
                                when {
                                    privacyAnnotations.isNotEmpty() -> showPrivacyPolicy = true
                                    termsAnnotations.isNotEmpty() -> showTermsOfService = true
                                }
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 按钮区域
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 退出按钮
                Button(
                    onClick = onExit,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = bgGray,
                        contentColor = textPrimary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.welcome_exit),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // 继续按钮
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentColor,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = stringResource(R.string.welcome_continue),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // 隐私政策底部弹窗（带动画）
        AnimatedVisibility(
            visible = showPrivacyPolicy,
            enter = fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
        ) {
            PolicyBottomSheet(
                title = stringResource(R.string.welcome_privacy_title),
                content = getPrivacyPolicyContent(),
                isDark = isDark,
                visible = showPrivacyPolicy,
                onDismiss = { showPrivacyPolicy = false }
            )
        }

        // 用户协议底部弹窗（带动画）
        AnimatedVisibility(
            visible = showTermsOfService,
            enter = fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
        ) {
            PolicyBottomSheet(
                title = stringResource(R.string.welcome_terms_title),
                content = getTermsOfServiceContent(),
                isDark = isDark,
                visible = showTermsOfService,
                onDismiss = { showTermsOfService = false }
            )
        }
    }
}

@Composable
private fun PolicyBottomSheet(
    title: String,
    content: String,
    isDark: Boolean,
    visible: Boolean,
    onDismiss: () -> Unit
) {
    val textPrimary = if (isDark) Color.White else Color.Black
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val bgGray = if (isDark) DarkBgGray else LightBgGray
    val cardBg = if (isDark) DarkCardBg else LightCardBg

    // 容器滑入动画（独立于遮罩）
    val containerOffsetY = remember { androidx.compose.animation.core.Animatable(1f) }

    LaunchedEffect(visible) {
        if (visible) {
            // 滑入动画（更快）
            containerOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )
        } else {
            // 滑出动画
            containerOffsetY.animateTo(
                targetValue = 1f,
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 遮罩层（由外层 AnimatedVisibility 控制渐显/渐隐）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (isDark) 0.4f else 0.1f))
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                }
        )

        // 容器层（滑入动画）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    translationY = containerOffsetY.value * size.height
                }
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                )
                .background(cardBg, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .padding(top = 24.dp, start = 24.dp, end = 24.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = KaiTi,
                    color = textPrimary
                )

                Spacer(modifier = Modifier.weight(1f))

                // 关闭按钮
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .background(bgGray, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 内容（带格式化标题）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                FormattedPolicyContent(
                    content = content,
                    textColor = textPrimary,
                    secondaryColor = textSecondary
                )
            }
        }
    }
}

@Composable
private fun FormattedPolicyContent(
    content: String,
    textColor: Color,
    secondaryColor: Color
) {
    val lines = content.lines()
    var index = 0

    while (index < lines.size) {
        val line = lines[index].trim()

        when {
            // 第一行是主标题（如"Lumi 隐私政策"、"Lumi 用户协议"）
            index == 0 && line.isNotEmpty() -> {
                Text(
                    text = line,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            // 空行
            line.isEmpty() -> {
                Spacer(modifier = Modifier.height(8.dp))
            }
            // 一级标题（一、二、三...或阿拉伯数字开头的标题）
            line.matches(Regex("^[一二三四五六七八九十]+、.*")) ||
            line.matches(Regex("^\\d+\\.\\s*[^\\d].*")) && !line.matches(Regex("^\\d+\\.\\d+.*")) -> {
                Text(
                    text = line,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }
            // 二级标题（3.1, 3.2, 4.1 等）
            line.matches(Regex("^\\d+\\.\\d+\\s+.*")) -> {
                Text(
                    text = line,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            // 列表项（以 • 或 - 开头）
            line.startsWith("•") || line.startsWith("-") || line.startsWith("❌") -> {
                Text(
                    text = line,
                    fontSize = 14.sp,
                    color = textColor,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                )
            }
            // 元信息行（生效日期、最近更新、应用名称、包名、开发者、邮箱）
            line.startsWith("生效日期") || line.startsWith("最近更新") ||
            line.startsWith("应用名称") || line.startsWith("包名") ||
            line.startsWith("开发者") || line.startsWith("联系邮箱") -> {
                Text(
                    text = line,
                    fontSize = 13.sp,
                    color = secondaryColor,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            // 核心原则（加粗显示）
            line.startsWith("核心原则") -> {
                Text(
                    text = line,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            // 普通段落
            else -> {
                Text(
                    text = line,
                    fontSize = 14.sp,
                    color = textColor,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        index++
    }
}

private fun getPrivacyPolicyContent(): String {
    return """
Lumi 隐私政策

生效日期：2026年6月29日
最近更新：2026年7月6日
应用名称：Lumi
包名：com.huangder.lumibooks
开发者：huangder
联系邮箱：huangder0104@126.com

一、引言

Lumi（以下简称"本应用"）是一款开源的本地电子书阅读器，以 MIT 许可证发布。我们深知隐私对您的重要性，本隐私政策旨在向您说明我们如何处理您的个人信息。

核心原则：本应用仅在检查更新时连接 GitHub 服务器，不收集、不上传任何个人信息。

二、开源声明

本应用为开源软件，源代码托管于 GitHub。您可以自由查看、修改和分发本应用的源代码。开源不意味着放弃隐私保护——我们依然严格遵守最小数据收集原则。

三、权限说明

本应用仅申请以下 Android 系统权限，且每项权限均有明确的用途说明：

• READ_EXTERNAL_STORAGE（Android 12 及以下）- 读取您设备上的电子书文件（EPUB、TXT、PDF）
• READ_MEDIA_IMAGES（Android 13 及以上）- 读取电子书中的封面图片资源

• INTERNET — 检查应用更新及条款/政策变更

本应用不申请以下权限：

• ❌ 位置信息权限
• ❌ 相机/麦克风权限
• ❌ 通讯录权限
• ❌ 电话状态权限
• ❌ 写入外部存储权限
• ❌ 任何其他敏感权限

四、数据处理说明

4.1 本应用处理的数据

以下数据仅存储在您的本地设备上，不会被传输至任何外部服务器：

• 电子书文件 - 您主动选择打开的 EPUB、TXT、PDF 文件
• 阅读进度 - 当前阅读章节和页码位置
• 书签记录 - 您手动添加的书签
• 阅读时长 - 每次阅读的开始时间、结束时间、持续时长
• 阅读偏好 - 字号、主题、行距等显示设置
• 最近阅读 - 最近打开的书籍列表及时间

4.2 本应用不处理的数据

• ❌ 不收集您的个人身份信息（姓名、邮箱、电话等）
• ❌ 不收集您的设备标识信息（IMEI、Android ID、MAC 地址等）
• ❌ 不收集您的阅读内容或阅读行为数据
• ❌ 不使用任何分析或追踪工具
• ❌ 不使用任何广告 SDK
• ❌ 不进行任何数据上传或同步

五、数据存储与安全

5.1 存储方式

所有数据均存储在应用的私有沙盒目录中，遵循 Android 系统的应用隔离机制。其他应用无法直接访问本应用的私有数据。

5.2 数据安全

• 应用仅在检查更新时连接 GitHub，不进行其他网络通信
• 数据存储在 Android 应用沙盒中，受系统级隔离保护
• 本应用不收集任何可识别您身份的信息

5.3 数据保留

• 您可以随时在应用内清除阅读历史、书签等数据
• 卸载应用将自动删除所有应用数据
• 电子书源文件由您自行管理，本应用不会修改或删除您的原始文件

六、第三方服务

本应用不集成任何第三方服务，包括但不限于：

• ❌ 无第三方分析服务
• ❌ 无第三方广告服务
• ❌ 无第三方推送服务
• ❌ 无云同步服务
• ❌ 无崩溃报告服务

七、儿童隐私

本应用不针对 13 岁以下儿童，不会故意收集儿童的个人信息。如果您是儿童的监护人，发现儿童可能向本应用提供了个人信息，请联系我们，我们将及时删除相关信息。

八、联系我们

如果您对本隐私政策有任何疑问、意见或建议，请通过以下方式联系我们：

• 开发者：huangder
• 联系邮箱：huangder0104@126.com
• GitHub Issues：欢迎在项目仓库提交问题反馈

九、隐私政策的变更

如果我们决定更新本隐私政策，我们将在应用内发布更新后的版本，并更新"最近更新"日期。重大变更将以应用内通知的方式告知您。

本隐私政策最后更新于 2026年7月6日。
    """.trimIndent()
}

private fun getTermsOfServiceContent(): String {
    return """
Lumi 用户协议

生效日期：2026年6月29日
最近更新：2026年7月6日
应用名称：Lumi
包名：com.huangder.lumibooks
开发者：huangder
联系邮箱：huangder0104@126.com

一、协议的接受

欢迎使用 Lumi（以下简称"本应用"）。在使用本应用之前，请仔细阅读并充分理解本用户协议。

下载、安装、打开或使用本应用，即表示您已阅读、理解并同意受本协议的约束。如果您不同意本协议的任何条款，请停止使用本应用。

二、应用说明

2.1 应用功能

Lumi 是一款本地电子书阅读器，主要功能包括：

• 阅读 EPUB、TXT、PDF 格式的电子书
• 管理本地电子书文件
• 记录阅读进度和书签
• 自定义阅读界面（字号、主题、行距等）
• 查看阅读时长统计

2.2 应用性质

本应用为本地优先应用（仅检查更新时联网）：

• 本应用不连接互联网
• 本应用不提供任何在线内容或服务
• 本应用不提供用户账号注册或登录功能
• 所有数据均存储在您的本地设备上

2.3 开源许可

本应用以 MIT 许可证开源发布，您可以：

• 自由查看源代码
• 在个人或商业项目中使用本应用
• 修改源代码并重新分发
• 在保留版权声明的前提下进行二次开发

三、用户权利与义务

3.1 您的权利

• 您有权免费使用本应用的所有功能
• 您有权在应用内自由管理您的阅读数据
• 您有权随时卸载本应用并删除所有数据
• 您有权查看、修改和分发本应用的源代码
• 您有权对本应用提出反馈和建议

3.2 您的义务

• 您应确保使用本应用阅读的电子书文件不侵犯他人知识产权
• 您不应利用本应用从事任何违反法律法规的活动
• 您在二次开发或分发时应保留原始版权声明

四、知识产权

4.1 应用知识产权

本应用以 MIT 许可证开源发布。源代码、界面设计、图标等组成部分的知识产权归开发者 huangder 所有，MIT 许可证允许您在保留版权声明的前提下自由使用。

4.2 用户内容

您通过本应用阅读的电子书文件及其内容的知识产权归原始版权人所有。本应用仅提供阅读功能，不主张对您阅读内容的任何权利。

五、免责声明

5.1 数据安全

• 本应用尽最大努力保护您的数据安全，但不对因设备故障、系统崩溃等原因导致的数据丢失承担责任
• 建议您定期备份重要的电子书文件和阅读数据
• 卸载应用将导致应用数据不可恢复地删除

5.2 文件兼容性

• 本应用支持 EPUB、TXT、PDF 格式，但不保证对所有同格式文件的完美兼容
• 部分电子书文件可能因格式不规范而无法正常显示

5.3 使用风险

• 您应自行承担使用本应用的一切风险
• 本应用按"现状"提供，不作任何明示或暗示的保证
• 开源代码按"原样"提供，不附带任何形式的担保

六、隐私保护

本应用高度重视您的隐私保护。核心要点：

• 本应用不收集任何个人信息
• 本应用不连接互联网
• 所有数据存储在您的本地设备上
• 源代码公开透明，欢迎审查

七、协议的变更

我们保留随时修改本协议的权利。修改后的协议将在应用内发布，并更新"最近更新"日期。继续使用本应用即表示您接受修改后的协议。

八、适用法律

本协议的解释和执行适用中华人民共和国法律。因本协议引起的或与本协议有关的任何争议，应友好协商解决；协商不成的，任何一方均可向开发者所在地人民法院提起诉讼。

九、联系我们

如果您对本用户协议有任何疑问、意见或建议，请通过以下方式联系我们：

• 开发者：huangder
• 联系邮箱：huangder0104@126.com
• GitHub Issues：欢迎在项目仓库提交问题反馈

本用户协议最后更新于 2026年7月6日。
    """.trimIndent()
}
