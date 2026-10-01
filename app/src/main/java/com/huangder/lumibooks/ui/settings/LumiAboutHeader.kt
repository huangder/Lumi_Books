package com.huangder.lumibooks.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.LumiEasterEgg
import com.huangder.lumibooks.ui.components.ReplacingToast
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled

@Composable
internal fun LumiAboutHeader(
    unlocked: Boolean,
    currentVersion: String,
    isCheckingUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    onUnlock: ((Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var headerClicks by rememberSaveable { mutableStateOf(0) }
    var playUnlockAnimation by remember { mutableStateOf(false) }
    val headerExit = remember { Animatable(if (unlocked) 1f else 0f) }
    val headerReveal = remember { Animatable(if (unlocked) 1f else 0f) }
    val motionEnabled = LocalMotionEnabled.current
    val toast = remember(context) { ReplacingToast(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && (context as? android.app.Activity)?.isChangingConfigurations != true) {
                headerClicks = 0
                toast.cancel()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            toast.cancel()
        }
    }
    LaunchedEffect(unlocked) {
        if (unlocked) {
            if (playUnlockAnimation && motionEnabled) {
                headerExit.animateTo(1f, tween(200))
                headerReveal.animateTo(1f, tween(300))
            } else {
                headerExit.snapTo(1f)
                headerReveal.snapTo(1f)
            }
            playUnlockAnimation = false
        }
    }

    // ── 版本主视觉与就地更新检查 ──
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.md)
            .aspectRatio(1.46f)
            .testTag("lumi_about_header")
            .shadow(8.dp, AppRoundedCornerShape(AppRadius.lg), ambientColor = Color(0x06000000), spotColor = Color(0x06000000))
            .clip(AppRoundedCornerShape(AppRadius.lg))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (unlocked) {
                    toast.show("我已经安放好啦！")
                } else if (headerClicks < LumiEasterEgg.REQUIRED_CLICKS) {
                    headerClicks = LumiEasterEgg.nextClickCount(headerClicks, unlocked)
                    val remaining = LumiEasterEgg.REQUIRED_CLICKS - headerClicks
                    if (remaining > 0) {
                        toast.show("麻烦再点击${remaining}次喵")
                    } else {
                        playUnlockAnimation = true
                        onUnlock { succeeded ->
                            if (succeeded) toast.show("彩蛋安置好了喵（*/∇＼*）")
                            else {
                                headerClicks = 4
                                playUnlockAnimation = false
                            }
                        }
                    }
                }
            }
    ) {
        Image(
            painter = painterResource(R.drawable.about_header),
            contentDescription = null,
            modifier = Modifier.matchParentSize().graphicsLayer {
                    alpha = 1f - headerExit.value
                    scaleX = 1f - 0.12f * headerExit.value
                    scaleY = 1f - 0.12f * headerExit.value
                },
            contentScale = ContentScale.Crop
        )
        if (unlocked) {
            Image(
                painter = painterResource(R.drawable.about_header_lumi_chan),
                contentDescription = null,
                modifier = Modifier.matchParentSize().graphicsLayer { alpha = headerReveal.value },
                contentScale = ContentScale.Crop
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = AppSpace.lg, bottom = AppSpace.lg)
        ) {
            Text(
                text = currentVersion,
                color = if (unlocked) Color(0xFF4B3540) else Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(AppSpace.sm))
            Box(
                modifier = Modifier
                    .testTag("lumi_check_update")
                    .width(120.dp)
                    .height(40.dp)
                    .clip(AppRoundedCornerShape(AppRadius.capsule))
                    .background(if (unlocked) Color.White.copy(alpha = 0.68f) else Color.White.copy(alpha = 0.36f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { if (!isCheckingUpdate) onCheckUpdate() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(if (isCheckingUpdate) R.string.checking_update else R.string.check_update),
                    color = if (unlocked) Color(0xFF4B3540) else Color.White,
                    fontSize = AppType.BodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

}
