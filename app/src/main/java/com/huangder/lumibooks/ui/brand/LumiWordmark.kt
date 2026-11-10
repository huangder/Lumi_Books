package com.huangder.lumibooks.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.ui.animation.AppEasing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The release codename shown under "Lumi". A brand word: never translated. */
object LumiCodename {
    const val CURRENT = "Filmy"
}

private val TightLines = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

/**
 * Two lines in the system typeface: a small, wide-tracked "Lumi" above a very heavy codename. On entrance "Lumi" fades
 * in first; the codename follows ~180 ms later, rising and settling slightly in scale.
 */
@Composable
fun LumiWordmark(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    animate: Boolean = true,
    codename: String = LumiCodename.CURRENT,
    /** Applied to the codename line only, e.g. a shared-element key. */
    codenameModifier: Modifier = Modifier
) {
    val colors = lumiDawnColors()
    val motion = animate && lumiDawnMotion()
    val lumiIn = remember { Animatable(if (motion) 0f else 1f) }
    val nameIn = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(motion) {
        if (!motion) {
            lumiIn.snapTo(1f); nameIn.snapTo(1f)
            return@LaunchedEffect
        }
        launch { lumiIn.animateTo(1f, tween(520, easing = AppEasing.Decelerate)) }
        delay(180)
        nameIn.animateTo(1f, tween(760, easing = AppEasing.Smooth))
    }
    val lumiSize = if (compact) 12.sp else 20.sp
    val nameSize = if (compact) 30.sp else 60.sp
    Column(
        modifier.clearAndSetSemantics { contentDescription = "Lumi $codename" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Lumi",
            style = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = lumiSize,
                lineHeight = lumiSize,
                letterSpacing = 0.22.em,
                color = colors.inkSecondary,
                lineHeightStyle = TightLines
            ),
            modifier = Modifier.graphicsLayer {
                alpha = lumiIn.value
                translationY = (1f - lumiIn.value) * 4.dp.toPx()
            }
        )
        val n = nameIn.value
        Text(
            text = codename,
            style = TextStyle(
                fontWeight = FontWeight.Black,
                fontSize = nameSize,
                lineHeight = nameSize,
                letterSpacing = (-0.035f).em,
                color = colors.ink,
                lineHeightStyle = TightLines
            ),
            modifier = Modifier
                // The font's ascent leaves room above the capitals; pull the codename up so the
                // two lines read as one mark.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val pull = (nameSize.toPx() * 0.12f).roundToInt()
                    layout(placeable.width, (placeable.height - pull).coerceAtLeast(0)) {
                        placeable.place(0, -pull)
                    }
                }
                .then(codenameModifier)
                // Layer-only entrance (no relayout per frame): fade, rise and a slight settle in scale.
                .graphicsLayer {
                    alpha = n
                    translationY = (1f - n) * 10.dp.toPx()
                    val s = 1.04f - 0.04f * n
                    scaleX = s
                    scaleY = s
                }
        )
    }
}
