package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi

/**
 * 贝塞尔 G2 连续曲线圆角——曲率在连接处平滑过渡，比普通圆角更圆润有机
 */
private class G2ContinuousShape(private val cornerRadius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): Outline {
        val w = size.width
        val h = size.height
        val r = cornerRadius.coerceAtMost(minOf(w, h) / 2f)
        // G2 贝塞尔近似系数：切线长度 ≈ 0.5523 * r
        val a = 0.5523f * r

        return Outline.Generic(
            Path().apply {
                // 从左上角圆弧结束后的位置开始，顺时针绘制
                moveTo(0f, r)
                // 左上角
                cubicTo(0f, r - a, r - a, 0f, r, 0f)
                // 上边
                lineTo(w - r, 0f)
                // 右上角
                cubicTo(w - r + a, 0f, w, r - a, w, r)
                // 右边
                lineTo(w, h - r)
                // 右下角
                cubicTo(w, h - r + a, w - r + a, h, w - r, h)
                // 下边
                lineTo(r, h)
                // 左下角
                cubicTo(r - a, h, 0f, h - r + a, 0f, h - r)
                close()
            }
        )
    }
}

/**
 * 通用编辑输入对话框 —— 卡片风格
 *
 * @param title   顶栏标题（如"修改书本信息"、"修改昵称"）
 * @param fields  需要编辑的字段列表，每项为 (标签, 占位符, 初始值)
 * @param onBack  点击返回箭头
 * @param onConfirm 点击确认，参数为新值列表（与 fields 一一对应）
 */
@Composable
fun EditInputDialog(
    title: String,
    fields: List<Triple<String, String, String>>,  // (label, placeholder, initialValue)
    onBack: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    // 每个字段的编辑状态
    val values = fields.mapIndexed { index, (_, _, initial) ->
        remember { mutableStateOf(initial) }
    }

    val density = LocalDensity.current
    val g2Shape = remember(density) {
        G2ContinuousShape(with(density) { 36.dp.toPx() })
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(g2Shape)
            .background(AppColors.CardBg)
            .padding(horizontal = AppSpace.lg, vertical = 22.dp)
    ) {
        // ── 顶栏：返回 ← | 标题 | 确认 ✓ ──
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 返回箭头（左）
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = AppColors.TextPrimary,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(AppRadius.full))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onBack
                    )
                    .padding(8.dp)
            )

            // 标题（中）
            Text(
                text = title,
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                fontFamily = KaiTi,
                color = AppColors.TextPrimary,
                modifier = Modifier.align(Alignment.Center)
            )

            // 确认按钮（右）—— 黑色圆形 + 白色对勾
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(AppRadius.full))
                    .background(AppColors.TextPrimary)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onConfirm(values.map { it.value }) }
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "确认",
                    tint = AppColors.CardBg
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ── 输入字段列表 ──
        fields.forEachIndexed { index, (label, placeholder, _) ->
            Text(
                text = label,
                fontSize = AppType.Caption,
                color = AppColors.TextSecondary,
                modifier = Modifier.padding(bottom = AppSpace.sm)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppRadius.lg))
                    .background(AppColors.BgGray)
                    .padding(horizontal = AppSpace.md, vertical = 14.dp)
            ) {
                if (values[index].value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = AppType.Body,
                        color = AppColors.TextSecondary
                    )
                }
                // 使用 BasicTextField 实现可编辑但无边框的输入
                androidx.compose.foundation.text.BasicTextField(
                    value = values[index].value,
                    onValueChange = { values[index].value = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = AppType.Body,
                        color = AppColors.TextPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Box {
                            if (values[index].value.isEmpty()) {
                                // 占位符已在外部绘制
                            }
                            innerTextField()
                        }
                    }
                )
            }

            if (index < fields.lastIndex) {
                Spacer(Modifier.height(AppSpace.md))
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}
