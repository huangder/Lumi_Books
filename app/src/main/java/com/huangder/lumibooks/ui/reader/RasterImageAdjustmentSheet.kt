package com.huangder.lumibooks.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.ReaderImageAdjustments
import com.huangder.lumibooks.ui.components.*
import com.huangder.lumibooks.ui.theme.AppColors

@Composable
internal fun RasterImageAdjustmentSheet(
    settings: ReaderImageAdjustments, eInk: Boolean,
    onBrightness: (Float) -> Unit, onContrast: (Float) -> Unit, onSharpen: (Float) -> Unit,
    onReset: () -> Unit, onDismiss: () -> Unit
) {
    val offset = remember { Animatable(1f) }
    var closing by remember { mutableStateOf(false) }
    val backProgress = ConfigurableBottomSheetBackHandler { closing = true }
    LaunchedEffect(Unit) { if (eInk) offset.snapTo(0f) else offset.animateBottomSheetIn() }
    LaunchedEffect(closing) {
        if (closing) { if (!eInk) offset.animateBottomSheetOut(); onDismiss() }
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { closing = true })
        LiquidGlassColumnSheetContainer(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.65f)
                .materialBottomSheetMotion(offset.value, backProgress),
            contentModifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            fallbackColor = AppColors.CardBg,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reader_image_adjustment), Modifier.weight(1f), fontSize = 18.sp, color = AppColors.TextPrimary)
                LiquidGlassTextButton(
                    text = stringResource(R.string.confirm),
                    onClick = { closing = true },
                    modifier = Modifier.height(40.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            ImageAdjustmentControls(settings, eInk, onBrightness, onContrast, onSharpen, onReset)
        }
    }
}
