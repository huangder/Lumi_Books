package com.huangder.lumibooks.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.ReaderImageAdjustments
import com.huangder.lumibooks.ui.components.*
import com.huangder.lumibooks.ui.theme.AppColors
import androidx.compose.ui.graphics.Color

@Composable
internal fun RasterImageAdjustmentSheet(
    settings: ReaderImageAdjustments, eInk: Boolean,
    cropMode: Int, manualCrop: RasterManualCrop, pageWidth: Int?,
    onCropModeChange: (Int) -> Unit, onManualCropChange: (RasterManualCrop) -> Unit,
    onManualCropPreview: (RasterManualCrop) -> Unit,
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
            shape = com.huangder.lumibooks.ui.theme.AppRoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reader_image_adjustment), Modifier.weight(1f), fontSize = 18.sp, color = AppColors.TextPrimary)
                LiquidGlassTextButton(
                    text = stringResource(R.string.confirm),
                    onClick = { closing = true },
                    modifier = Modifier.height(40.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.reader_raster_horizontal_crop), fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
            Spacer(Modifier.height(10.dp))
            val modes = listOf(R.string.reader_crop_off, R.string.reader_crop_auto, R.string.reader_crop_manual)
            LiquidGlassSegmentedControl(
                itemCount = 3, selectedIndex = cropMode, onSelected = onCropModeChange,
                modifier = Modifier.fillMaxWidth()
            ) { index, selected ->
                Text(stringResource(modes[index]), fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) AppColors.TextPrimary else AppColors.TextSecondary)
            }
            if (cropMode == 2) {
                Spacer(Modifier.height(12.dp))
                val sliderMax = ((pageWidth ?: 1000) * 0.45f).toInt().coerceAtLeast(1)
                val sliderActive = if (eInk) Color.Black else AppColors.ControlActive
                val sliderInactive = if (eInk) Color(0xFFE0E0E0) else AppColors.BgGray
                SettingSlider(
                    label = stringResource(R.string.reader_crop_left_px),
                    value = manualCrop.leftPx.coerceIn(0, sliderMax).toFloat(),
                    range = 0f..sliderMax.toFloat(),
                    step = 1f,
                    format = { "${it.toInt()} px" },
                    onChange = { value -> onManualCropChange(manualCrop.copy(leftPx = value.toInt())) },
                    onPreview = { value -> onManualCropPreview(manualCrop.copy(leftPx = value.toInt())) },
                    sliderActiveColor = sliderActive,
                    sliderInactiveColor = sliderInactive,
                    forceNonGlass = eInk
                )
                Spacer(Modifier.height(10.dp))
                SettingSlider(
                    label = stringResource(R.string.reader_crop_right_px),
                    value = manualCrop.rightPx.coerceIn(0, sliderMax).toFloat(),
                    range = 0f..sliderMax.toFloat(),
                    step = 1f,
                    format = { "${it.toInt()} px" },
                    onChange = { value -> onManualCropChange(manualCrop.copy(rightPx = value.toInt())) },
                    onPreview = { value -> onManualCropPreview(manualCrop.copy(rightPx = value.toInt())) },
                    sliderActiveColor = sliderActive,
                    sliderInactiveColor = sliderInactive,
                    forceNonGlass = eInk
                )
            }
            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Divider))
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.reader_image_adjustment_hint),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.reader_image_adjustment_saved_hint),
                fontSize = 12.sp,
                color = AppColors.TextSecondary
            )
            Spacer(Modifier.height(16.dp))
            ImageAdjustmentControls(settings, eInk, onBrightness, onContrast, onSharpen, onReset,
                additionalResetEnabled = cropMode != 0 || manualCrop != RasterManualCrop(), showSavedHint = false)
        }
    }
}
