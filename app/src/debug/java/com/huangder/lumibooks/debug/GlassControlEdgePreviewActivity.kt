package com.huangder.lumibooks.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.ui.components.*
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Visual/gesture fixture only: no saved preferences, files, network calls or library data. */
class GlassControlEdgePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = intent.getBooleanExtra("dark", false)
            EBookReaderTheme(
                appTheme = intent.getStringExtra("theme") ?: "liquid_glass",
                darkTheme = dark,
                motionPreference = if (intent.getBooleanExtra("reduced", false)) MotionPreference.REDUCED else MotionPreference.STANDARD
            ) {
                CompositionLocalProvider(
                    LocalLiquidGlassControlEdgeEnabled provides !intent.getBooleanExtra("legacy", false),
                    LocalLiquidGlassControlEdgeForceCanvas provides intent.getBooleanExtra("canvas", false),
                    LocalLiquidGlassTransparency provides intent.getFloatExtra("transparency", 0.55f)
                ) {
                    Samples(
                        dark = dark,
                        background = intent.getStringExtra("background") ?: "white",
                        noBackdrop = intent.getBooleanExtra("noBackdrop", false),
                        artwork = intent.getBooleanExtra("artwork", false)
                    )
                }
            }
        }
    }
}

@Composable
private fun Samples(dark: Boolean, background: String, noBackdrop: Boolean, artwork: Boolean) {
    val backdrop = rememberLayerBackdrop()
    val foreground = if (dark) Color.White else Color(0xFF17171A)
    val surface = if (dark) Color(0xFF202023) else Color(0xFFF7F7F9)
    var clicks by remember { mutableIntStateOf(0) }
    val click = { clicks++; Unit }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(
            if (background == "content") Brush.linearGradient(listOf(Color(0xFF81BCE3), Color(0xFFE4ABBE), Color(0xFFE9D5A1)))
            else Brush.verticalGradient(List(2) {
                if (dark) Color(0xFF151518) else if (background == "gray") Color(0xFFF0EFF4) else Color.White
            })
        )) {
            if (background == "content") Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                repeat(24) { Text("Lumi · Reading  /  0123456789", color = Color.Black.copy(alpha = 0.24f), fontSize = 20.sp) }
            }
        }
        CompositionLocalProvider(
            LocalLiquidGlassBackdrop provides backdrop.takeUnless { noBackdrop },
            LocalLumiBackgroundBackdrop provides backdrop.takeIf { artwork && !noBackdrop }
        ) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .verticalScroll(rememberScrollState()).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text("Glass controls · " + (if (LocalLiquidGlassControlEdgeEnabled.current) "New" else "Before") +
                    (if (LocalLiquidGlassControlEdgeForceCanvas.current) " · Canvas" else ""), color = foreground)
                LiquidGlassSurface(
                    shape = CircleShape, fallbackColor = surface,
                    contentScrimColor = surface.copy(alpha = 0.58f), controlEdge = true,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf(AppIcons.Gear, AppIcons.Info, AppIcons.Trash, AppIcons.DotsThreeVertical).forEach {
                            Icon(it, null, Modifier.size(24.dp), tint = foreground)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    LiquidGlassTextButton("编辑", click, modifier = Modifier.width(88.dp).height(46.dp), contentColor = foreground)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LiquidGlassIconButton(AppIcons.ListBullets, "List", click, size = 48.dp, contentColor = foreground)
                        LiquidGlassIconButton(AppIcons.DotsThreeVertical, "More", click, size = 48.dp, contentColor = foreground)
                    }
                }
                LiquidGlassTextButton("继续", click, modifier = Modifier.fillMaxWidth().height(52.dp),
                    tintedColor = Color(0xFFE6AD00), contentColor = Color.White)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    LiquidGlassTextButton("Disabled", click, enabled = false, contentColor = foreground)
                    LiquidGlassSurface(CircleShape, Color.Black, Modifier.size(48.dp),
                        contentScrimColor = Color.Black.copy(alpha = 0.85f), onClick = click, controlEdge = true) {
                        Icon(AppIcons.Plus, null, Modifier.size(24.dp), tint = Color.White)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiquidGlassSurface(CircleShape, surface, Modifier.size(36.dp), onClick = click, controlEdge = true) {
                        Icon(AppIcons.ArrowLeft, null, Modifier.size(18.dp), tint = foreground)
                    }
                    LiquidGlassSurface(CircleShape, surface, Modifier.height(36.dp).weight(1f), controlEdge = true) {
                        Text("Reader title capsule", color = foreground, fontSize = 12.sp)
                    }
                }
                Text("Clicks: $clicks", color = foreground)
                FloatingTabBar(selectedIndex = 0, onTabSelected = { click() }, liquidGlassBackdrop = backdrop.takeUnless { noBackdrop })
                Text("Tap, hold, drag outside and release", color = foreground, fontSize = 12.sp)
            }
        }
    }
}
