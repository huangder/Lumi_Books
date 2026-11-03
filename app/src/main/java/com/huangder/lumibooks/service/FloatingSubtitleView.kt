package com.huangder.lumibooks.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.core.graphics.ColorUtils
import com.huangder.lumibooks.R
import com.huangder.lumibooks.tts.FloatingSubtitleSettings
import com.huangder.lumibooks.ui.icons.AppIcons

internal enum class FloatingSubtitleMode { SUBTITLE, CONTROLS, LEFT, RIGHT }

internal class FloatingSubtitleView(
    context: Context,
    private val onTouch: (MotionEvent) -> Boolean,
    private val onSubtitleClick: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onReturn: () -> Unit
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    val subtitle = object : TextView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            // Horizontally scrolling TextView uses a very wide layout; centering that layout hides short text.
            val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            val overflowing = paint.measureText(text.toString()) > available
            setHorizontallyScrolling(overflowing)
            gravity = Gravity.CENTER_VERTICAL or if (overflowing) Gravity.START else Gravity.CENTER_HORIZONTAL
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply {
        gravity = Gravity.CENTER
        includeFontPadding = false
        setSingleLine(true)
        textSize = 16f
        setPadding(dp(16), 0, dp(16), 0)
        setOnTouchListener { _, event -> onTouch(event) }
        setOnClickListener { onSubtitleClick() }
        contentDescription = context.getString(R.string.tts_expand_controls)
    }
    private val full = FrameLayout(context)
    private val controls = LinearLayout(context).apply { gravity = Gravity.CENTER }
    private val mini = FrameLayout(context)
    private val miniBar = View(context)
    private val miniRow = LinearLayout(context).apply { gravity = Gravity.CENTER }
    private val cover = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = rounded(Color.rgb(221, 228, 231), dp(4).toFloat())
        clipToOutline = true
        setOnTouchListener { _, event -> onTouch(event) }
        setOnClickListener { onSubtitleClick() }
        contentDescription = context.getString(R.string.tts_restore_subtitle)
    }
    private val play = button(AppIcons.Play, R.string.tts_play, onToggle)
    private val miniPlay = button(AppIcons.Play, R.string.tts_play, onToggle)
    private val miniClose = button(AppIcons.X, R.string.tts_stop, onClose)
    private var ink = Color.WHITE
    private var mode = FloatingSubtitleMode.SUBTITLE

    init {
        clipChildren = true
        addView(full, LayoutParams(-1, -1))
        full.addView(subtitle, LayoutParams(-1, dp(56)))
        full.addView(controls, LayoutParams(-1, dp(52)).apply { topMargin = dp(56) })
        controls.addView(button(AppIcons.SkipBack, R.string.tts_previous_sentence, onPrevious), LinearLayout.LayoutParams(dp(44), dp(44)))
        controls.addView(play, LinearLayout.LayoutParams(dp(44), dp(44)))
        controls.addView(button(AppIcons.SkipForward, R.string.tts_next_sentence, onNext), LinearLayout.LayoutParams(dp(44), dp(44)))
        controls.addView(button(AppIcons.CornersOut, R.string.tts_return_to_book, onReturn), LinearLayout.LayoutParams(dp(44), dp(44)))
        controls.addView(button(AppIcons.X, R.string.tts_stop, onClose), LinearLayout.LayoutParams(dp(44), dp(44)))
        addView(mini, LayoutParams(-1, -1))
        mini.addView(miniBar, LayoutParams(-1, dp(40), Gravity.CENTER))
        mini.addView(miniRow, LayoutParams(-1, -1))
        mini.setOnTouchListener { _, event -> onTouch(event) }
        miniBar.setOnTouchListener { _, event -> onTouch(event) }
        miniBar.setOnClickListener { onSubtitleClick() }
        miniBar.contentDescription = context.getString(R.string.tts_restore_subtitle)
        mini.visibility = GONE
        controls.visibility = INVISIBLE
    }

    private fun button(icon: ImageVector, label: Int, action: () -> Unit) = ImageButton(context).apply {
        setImageDrawable(FloatingVectorDrawable(icon))
        contentDescription = context.getString(label)
        tooltipText = contentDescription
        background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x33FFFFFF), null,
            rounded(Color.WHITE, dp(22).toFloat()))
        setPadding(dp(11), dp(11), dp(11), dp(11))
        scaleType = ImageView.ScaleType.FIT_CENTER
        setOnClickListener { action() }
    }

    fun setCover(bitmap: Bitmap?) {
        cover.setImageBitmap(bitmap)
    }

    fun setPlaying(playing: Boolean) {
        listOf(play, miniPlay).forEach {
            it.setImageDrawable(FloatingVectorDrawable(if (playing) AppIcons.Pause else AppIcons.Play))
            it.setColorFilter(ink)
            it.contentDescription = context.getString(if (playing) R.string.tts_pause else R.string.tts_play)
            it.tooltipText = it.contentDescription
        }
    }

    fun appearance(settings: FloatingSubtitleSettings) {
        val base = Color.parseColor(settings.backgroundColorHex)
        ink = if (ColorUtils.calculateLuminance(base) < .46) Color.WHITE else Color.BLACK
        val color = ColorUtils.setAlphaComponent(base, (settings.backgroundOpacity * 255).toInt())
        full.background = rounded(color, settings.cornerRadiusDp * density)
        // The outer end stays rounded; the end against the display edge is flush.
        val radius = dp(20).toFloat()
        miniBar.background = GradientDrawable().apply {
            setColor(color)
            cornerRadii = if (mode == FloatingSubtitleMode.LEFT)
                floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
            else floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
        }
        subtitle.setTextColor(ink)
        for (index in 0 until controls.childCount) (controls.getChildAt(index) as ImageButton).setColorFilter(ink)
        miniPlay.setColorFilter(ink)
        miniClose.setColorFilter(ink)
        val height = dp(settings.heightDp.toInt())
        subtitle.layoutParams = LayoutParams(-1, height)
        controls.layoutParams = LayoutParams(-1, dp(52)).apply { topMargin = height }
    }

    fun showMode(target: FloatingSubtitleMode, animate: Boolean) {
        val previous = mode
        mode = target
        val docked = target == FloatingSubtitleMode.LEFT || target == FloatingSubtitleMode.RIGHT
        if (docked) {
            miniRow.removeAllViews()
            val items = if (target == FloatingSubtitleMode.LEFT) listOf(miniClose, miniPlay, cover)
                else listOf(cover, miniPlay, miniClose)
            miniRow.setPadding(dp(12), dp(4), dp(12), dp(4))
            items.forEach { miniRow.addView(it, LinearLayout.LayoutParams(
                dp(if (it === cover) 36 else 44), dp(if (it === cover) 48 else 44))) }
        }
        listOf(full, mini, controls).forEach { it.animate().cancel() }
        full.visibility = VISIBLE
        mini.visibility = if (docked || previous == FloatingSubtitleMode.LEFT || previous == FloatingSubtitleMode.RIGHT) VISIBLE else GONE
        controls.visibility = if (target == FloatingSubtitleMode.CONTROLS || previous == FloatingSubtitleMode.CONTROLS) VISIBLE else INVISIBLE
        fun fade(view: View, visible: Boolean) {
            if (animate) view.animate().alpha(if (visible) 1f else 0f).setDuration(180).withEndAction {
                if (!visible) view.visibility = INVISIBLE
            }.start()
            else { view.alpha = if (visible) 1f else 0f; view.visibility = if (visible) VISIBLE else INVISIBLE }
        }
        fade(full, !docked)
        fade(mini, docked)
        fade(controls, target == FloatingSubtitleMode.CONTROLS)
        subtitle.contentDescription = context.getString(if (target == FloatingSubtitleMode.CONTROLS)
            R.string.tts_collapse_controls else R.string.tts_expand_controls)
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }
}

/** Render the same Fluent icons as the Compose reader in a system overlay's native views. */
private class FloatingVectorDrawable(private val vector: ImageVector) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paths = mutableMapOf<VectorPath, android.graphics.Path>()
    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / vector.viewportWidth, bounds.height() / vector.viewportHeight)
        drawGroup(canvas, vector.root)
        canvas.restore()
    }
    private fun drawGroup(canvas: Canvas, group: VectorGroup) {
        canvas.save()
        canvas.translate(group.translationX + group.pivotX, group.translationY + group.pivotY)
        canvas.rotate(group.rotation)
        canvas.scale(group.scaleX, group.scaleY)
        canvas.translate(-group.pivotX, -group.pivotY)
        for (index in 0 until group.size) when (val node = group[index]) {
            is VectorGroup -> drawGroup(canvas, node)
            is VectorPath -> {
                val path = paths.getOrPut(node) { PathParser().addPathNodes(node.pathData).toPath().apply {
                    fillType = node.pathFillType
                }.asAndroidPath() }
                paint.color = (node.fill as? SolidColor)?.value?.toArgb() ?: Color.WHITE
                paint.alpha = (node.fillAlpha * 255).toInt()
                paint.style = Paint.Style.FILL
                if (node.fill != null) canvas.drawPath(path, paint)
            }
        }
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
