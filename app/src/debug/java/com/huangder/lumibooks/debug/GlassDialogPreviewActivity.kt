package com.huangder.lumibooks.debug

import android.os.Bundle
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Alignment
import com.huangder.lumibooks.domain.model.LiquidGlassStyle
import androidx.compose.ui.viewinterop.AndroidView
import com.huangder.lumibooks.ui.components.*
import com.huangder.lumibooks.ui.theme.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay

/** Optical fixture only; never changes books, reading positions or saved preferences. */
class GlassDialogPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = intent.getBooleanExtra("dark", false)
            val native = intent.getBooleanExtra("native", false)
            EBookReaderTheme(appTheme = "liquid_glass", darkTheme = dark,
                liquidGlassStyle = LiquidGlassStyle.fromStoredValue(intent.getStringExtra("style"))) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, intent.getFloatExtra("fontScale", 1f).coerceIn(0.8f, 2f)),
                    LocalLiquidGlassTransparency provides intent.getFloatExtra("transparency", 1f),
                    LocalReduceTransparency provides intent.getBooleanExtra("blurOnly", false)
                ) {
                    val backdrop = rememberLayerBackdrop()
                    var open by remember { mutableStateOf(false) }
                    var nested by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { delay(300); open = true }
                    LiquidGlassDialogHost(Modifier.fillMaxSize(),
                        backdrop = backdrop.takeUnless { native }, snapshotContent = native) {
                        Box(Modifier.fillMaxSize().then(if (native) Modifier else Modifier.layerBackdrop(backdrop))) {
                            Canvas(Modifier.fillMaxSize()) {
                                val cell = 12.dp.toPx()
                                for (row in 0..(size.height / cell).toInt()) {
                                    for (col in 0..(size.width / cell).toInt()) {
                                        drawRect(if ((row + col) % 2 == 0) Color(0xFF327CAB) else Color(0xFFE5C59B),
                                            Offset(col * cell, row * cell), Size(cell, cell))
                                    }
                                }
                            }
                            if (native) AndroidView(
                                modifier = Modifier.fillMaxWidth().padding(top = 120.dp),
                                factory = { EditText(it).apply {
                                    setText("Native editor · background pixels\n0123456789\nThe reader viewport stays still.")
                                    setTextColor(android.graphics.Color.BLACK)
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    clearFocus()
                                } }
                            )
                            Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
                                repeat(15) { Text("PAGE $it · abc 0123456789", color = Color.Black, fontSize = 18.sp) }
                                LiquidGlassTextButton("Open glass", onClick = { open = true })
                            }
                        }
                        val presentation = intent.getStringExtra("presentation")
                        if (open && presentation == "alert") LiquidGlassAlertDialog(
                            onDismissRequest = { open = false },
                            titleText = "保存阅读设置？",
                            text = { Text("更改将应用到当前书籍，其他书籍的阅读设置保持独立。") },
                            actions = listOf(
                                AppDialogAction(if (intent.getBooleanExtra("longLabels", false)) "继续并应用这些阅读设置" else "继续", { nested = true }),
                                AppDialogAction("取消", { open = false }, AppDialogActionRole.Cancel)
                            )
                        ) else if (open && presentation == "editor") LiquidGlassDialog(
                            onDismissRequest = { open = false }
                        ) {
                            EditInputDialog("编辑书籍", listOf(Triple("书名", "输入书名", "阅读与生活"),
                                Triple("作者", "输入作者", "Lumi")),
                                onBack = { open = false }, onConfirm = { open = false })
                        } else if (open && presentation == "sheet") LiquidGlassDialog(
                            onDismissRequest = { open = false }, alignment = Alignment.BottomCenter,
                            shape = AppRoundedCornerShape(AppPresentation.SheetCorner)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                                AppSheetHeader("阅读设置", onClose = { open = false })
                                Spacer(Modifier.height(16.dp))
                                Box(Modifier.fillMaxWidth().lumiCardSurface(AppColors.BgGray,
                                    AppShapes.concentric(AppPresentation.SheetCorner, 24.dp)).padding(16.dp)) {
                                    Text("清晰的内容表面\n操作控件保留液态玻璃", color = AppColors.TextPrimary)
                                }
                                Spacer(Modifier.height(16.dp))
                                LiquidGlassTextButton("更多设置", { nested = true })
                            }
                        } else if (open) LiquidGlassDialog(
                            onDismissRequest = { open = false },
                            contentScrimColor = (if (dark) Color.Black else Color.White).copy(alpha = 0.35f)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                                Text("Glass dialog", color = AppColors.TextPrimary, fontSize = 24.sp)
                                Text("Sharp foreground / blurred page", color = AppColors.TextPrimary)
                                LiquidGlassTextButton("Nested dialog", onClick = { nested = true })
                                LiquidGlassTextButton("Close", onClick = { open = false })
                            }
                        }
                        if (nested) LiquidGlassAlertDialog(
                            onDismissRequest = { nested = false },
                            title = { Text("Nested glass", color = AppColors.TextPrimary) },
                            text = { Text("The previous dialog must return on close.", color = AppColors.TextPrimary) },
                            confirmButton = { LiquidGlassTextButton("Back", onClick = { nested = false }) }
                        )
                    }
                }
            }
        }
    }
}
