package com.huangder.lumibooks.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.ReaderImageAdjustments
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton

@Composable
internal fun ImageAdjustmentControls(
    settings: ReaderImageAdjustments,
    eInk: Boolean,
    onBrightness: (Float) -> Unit,
    onContrast: (Float) -> Unit,
    onSharpen: (Float) -> Unit,
    onReset: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.reader_image_adjustment_saved_hint), fontSize = 12.sp, color = AppColors.TextSecondary)
        SettingSlider(stringResource(R.string.reader_image_brightness), settings.brightness, -1f..1f, 0.05f,
            { String.format("%+.2f", it) }, onBrightness, onBrightness)
        SettingSlider(stringResource(R.string.reader_image_contrast), settings.contrast, 0.5f..2f, 0.05f,
            { String.format("%.2fx", it) }, onContrast, onContrast)
        if (eInk) {
            Text(stringResource(R.string.reader_image_sharpen_eink), fontSize = 12.sp, color = AppColors.TextSecondary)
        } else {
            SettingSlider(stringResource(R.string.reader_image_sharpen), settings.sharpen, 0f..1f, 0.05f,
                { "${(it * 100).toInt()}%" }, onSharpen, onSharpen)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            LiquidGlassTextButton(
                text = stringResource(R.string.reader_image_adjustment_reset),
                onClick = onReset,
                enabled = !settings.isNeutral,
                modifier = Modifier.height(40.dp)
            )
        }
    }
}
