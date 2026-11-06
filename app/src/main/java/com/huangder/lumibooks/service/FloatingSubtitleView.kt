package com.huangder.lumibooks.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
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
import kotlin.math.max

internal enum class FloatingSubtitleMode { SUBTITLE, CONTROLS, LEFT, RIGHT }

/**
 * Cross-app listening overlay. The subtitle bar (cover, sentence, play/pause) stays at the top in
 * both bar and card modes so expanding never moves what the finger is on; the card adds book
 * details, chapter progress and the remaining controls underneath. Docked, it becomes an edge tab
 * holding a round cover with a progress ring.
 */
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
    private fun dp(value: Float) = (value * density).toInt()
    val subtitle = object : TextView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            // Horizontally scrolling TextView uses a very wide layout; centering that layout hides short text.
            val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            val overflowing = paint.measureText(text.toString()) > available
            setHorizontallyScrolling(overflowing)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply {
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        includeFontPadding = false
        setSingleLine(true)
        textSize = 15f
        setPadding(dp(12), 0, dp(8), 0)
        setOnTouchListener { _, event -> onTouch(event) }
        setOnClickListener { onSubtitleClick() }
        contentDescription = context.getString(R.string.tts_expand_controls)
    }
    private val card = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val bar = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val cover = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        setOnTouchListener { _, event -> onTouch(event) }
        setOnClickListener { onSubtitleClick() }
        contentDescription = context.getString(R.string.tts_expand_controls)
    }
    private val play = button(AppIcons.Play, R.string.tts_play, onToggle)
    private val details = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setOnTouchListener { _, event -> onTouch(event) }
    }
    private val meta = TextView(context).apply {
        includeFontPadding = false
        setSingleLine(true)
        ellipsize = TextUtils.TruncateAt.END
        textSize = 12f
        gravity = Gravity.CENTER_VERTICAL
    }
    private val progressBar = ProgressLine(context)
    private val controls = LinearLayout(context).apply { gravity = Gravity.CENTER }
    private val controlButtons = listOf(
        button(AppIcons.SkipBack, R.string.tts_previous_sentence, onPrevious),
        button(AppIcons.SkipForward, R.string.tts_next_sentence, onNext),
        button(AppIcons.CornersOut, R.string.tts_return_to_book, onReturn),
        button(AppIcons.X, R.string.tts_stop, onClose)
    )
    private val dock = DockTab(context).apply {
        setOnTouchListener { _, event -> onTouch(event) }
        setOnClickListener { onSubtitleClick() }
        contentDescription = context.getString(R.string.tts_restore_subtitle)
    }
    private var ink = Color.WHITE
    private var mode = FloatingSubtitleMode.SUBTITLE
    private var coverBitmap: Bitmap? = null
    private var coverRadius = 0f
    private var title: String? = null
    private var chapter: String? = null

    init {
        clipChildren = true
        addView(card, LayoutParams(-1, -1))
        card.addView(bar, LinearLayout.LayoutParams(-1, dp(56)))
        bar.addView(cover)
        bar.addView(subtitle, LinearLayout.LayoutParams(0, -1, 1f))
        bar.addView(play)
        card.addView(details, LinearLayout.LayoutParams(-1, dp(DETAILS_HEIGHT_DP)))
        details.addView(meta, LinearLayout.LayoutParams(-1, dp(18)).apply {
            marginStart = dp(16); marginEnd = dp(16); topMargin = dp(2)
        })
        details.addView(progressBar, LinearLayout.LayoutParams(-1, dp(3)).apply {
            marginStart = dp(16); marginEnd = dp(16); topMargin = dp(8)
        })
        details.addView(controls, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(5) })
        controlButtons.forEach {
            controls.addView(it, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                marginStart = dp(6); marginEnd = dp(6)
            })
        }
        addView(dock, LayoutParams(-1, -1))
        details.visibility = INVISIBLE
        dock.visibility = GONE
        updateCover()
        updateMeta()
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
        coverBitmap = bitmap
        updateCover()
        dock.cover = bitmap
    }

    /** Book title and a localized chapter label; either may be missing while loading. */
    fun setInfo(title: String?, chapter: String?) {
        if (this.title == title && this.chapter == chapter) return
        this.title = title
        this.chapter = chapter
        updateMeta()
    }

    fun setProgress(progress: Float) {
        progressBar.progress = progress
        dock.progress = progress
    }

    fun setPlaying(playing: Boolean) {
        play.setImageDrawable(FloatingVectorDrawable(if (playing) AppIcons.Pause else AppIcons.Play))
        play.setColorFilter(ink)
        play.contentDescription = context.getString(if (playing) R.string.tts_pause else R.string.tts_play)
        play.tooltipText = play.contentDescription
        dock.playing = playing
    }

    fun appearance(settings: FloatingSubtitleSettings) {
        val base = Color.parseColor(settings.backgroundColorHex)
        ink = if (ColorUtils.calculateLuminance(base) < .46) Color.WHITE else Color.BLACK
        val color = ColorUtils.setAlphaComponent(base, (settings.backgroundOpacity * 255).toInt())
        // A hairline in the text colour keeps the card readable over backgrounds of the same tone.
        val hairline = ColorUtils.setAlphaComponent(ink, 0x1F)
        card.background = rounded(color, settings.cornerRadiusDp * density).apply {
            setStroke(max(1, dp(.5f)), hairline)
        }
        subtitle.setTextColor(ink)
        meta.setTextColor(ColorUtils.setAlphaComponent(ink, 0xB3))
        play.setColorFilter(ink)
        controlButtons.forEach { it.setColorFilter(ink) }
        progressBar.ink = ink
        dock.apply(ink, mode == FloatingSubtitleMode.LEFT)
        val height = dp(settings.heightDp)
        bar.layoutParams = LinearLayout.LayoutParams(-1, height)
        // The cover is inset evenly from the bar's top, bottom and start, so it follows the bar height.
        val coverSize = (height - dp(16)).coerceIn(dp(24), dp(40))
        val inset = (height - coverSize) / 2
        cover.layoutParams = LinearLayout.LayoutParams(coverSize, coverSize).apply { marginStart = inset }
        coverRadius = minOf(dp(8).toFloat(), settings.cornerRadiusDp * density)
        cover.outlineProvider = RoundedOutline(coverRadius)
        play.layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
            marginEnd = (inset - dp(2)).coerceAtLeast(dp(4))
        }
        play.setPadding(dp(10), dp(10), dp(10), dp(10))
        updateCover()
    }

    fun showMode(target: FloatingSubtitleMode, animate: Boolean) {
        val previous = mode
        mode = target
        val docked = target == FloatingSubtitleMode.LEFT || target == FloatingSubtitleMode.RIGHT
        val wasDocked = previous == FloatingSubtitleMode.LEFT || previous == FloatingSubtitleMode.RIGHT
        dock.leftEdge = target == FloatingSubtitleMode.LEFT
        listOf(card, dock, details).forEach { it.animate().cancel() }
        card.visibility = VISIBLE
        dock.visibility = if (docked || wasDocked) VISIBLE else GONE
        details.visibility = if (target == FloatingSubtitleMode.CONTROLS || previous == FloatingSubtitleMode.CONTROLS) VISIBLE else INVISIBLE
        fun fade(view: View, visible: Boolean) {
            if (animate) view.animate().alpha(if (visible) 1f else 0f).setDuration(180).withEndAction {
                if (!visible) view.visibility = INVISIBLE
            }.start()
            else { view.alpha = if (visible) 1f else 0f; view.visibility = if (visible) VISIBLE else INVISIBLE }
        }
        fade(card, !docked)
        fade(dock, docked)
        fade(details, target == FloatingSubtitleMode.CONTROLS)
        val description = context.getString(if (target == FloatingSubtitleMode.CONTROLS)
            R.string.tts_collapse_controls else R.string.tts_expand_controls)
        subtitle.contentDescription = description
        cover.contentDescription = description
    }

    private fun updateCover() {
        val bitmap = coverBitmap
        if (bitmap != null) {
            cover.setImageBitmap(bitmap)
            cover.background = null
            cover.scaleType = ImageView.ScaleType.CENTER_CROP
            cover.setPadding(0, 0, 0, 0)
            cover.clearColorFilter()
        } else {
            cover.setImageDrawable(FloatingVectorDrawable(AppIcons.Headphones))
            cover.background = rounded(ColorUtils.setAlphaComponent(ink, 0x1F), coverRadius)
            cover.scaleType = ImageView.ScaleType.FIT_CENTER
            cover.setPadding(dp(8), dp(8), dp(8), dp(8))
            cover.setColorFilter(ColorUtils.setAlphaComponent(ink, 0xB3))
        }
    }

    private fun updateMeta() {
        val parts = listOfNotNull(title?.takeIf(String::isNotBlank), chapter?.takeIf(String::isNotBlank))
        meta.text = parts.joinToString(" · ")
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    companion object {
        /** Height the card adds below the subtitle bar for details, progress and controls. */
        const val DETAILS_HEIGHT_DP = 88f
        const val CARD_MIN_WIDTH_DP = 280f
        const val DOCK_WIDTH_DP = 60f
        const val DOCK_HEIGHT_DP = 64f
    }
}

private class RoundedOutline(private val radius: Float) : android.view.ViewOutlineProvider() {
    override fun getOutline(view: View, outline: android.graphics.Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, radius)
    }
}

/** Thin chapter progress line drawn in the overlay's text colour. */
private class ProgressLine(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    var ink = Color.WHITE
        set(value) { field = value; invalidate() }
    var progress = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field != clamped) { field = clamped; invalidate() }
        }

    override fun onDraw(canvas: Canvas) {
        val radius = height / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = ColorUtils.setAlphaComponent(ink, 0x38)
        canvas.drawRoundRect(rect, radius, radius, paint)
        if (progress <= 0f) return
        rect.right = max(height.toFloat(), width * progress)
        paint.color = ink
        canvas.drawRoundRect(rect, radius, radius, paint)
    }
}

/**
 * A round cover and chapter progress ring beside the display edge, with no backing tab.
 * While paused the cover dims under a play glyph.
 */
private class DockTab(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shaderMatrix = Matrix()
    private val arc = RectF()
    private val headphones = FloatingVectorDrawable(AppIcons.Headphones)
    private val playGlyph = FloatingVectorDrawable(AppIcons.Play)
    private var ink = Color.WHITE
    var leftEdge = false
        set(value) { field = value; invalidate() }
    var playing = false
        set(value) { if (field != value) { field = value; invalidate() } }
    var progress = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field != clamped) { field = clamped; invalidate() }
        }
    var cover: Bitmap? = null
        set(value) {
            field = value
            image.shader = value?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            invalidate()
        }

    fun apply(ink: Int, left: Boolean) {
        this.ink = ink
        leftEdge = left
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val ringWidth = 2.5f * density
        val outer = 22f * density
        val cx = if (leftEdge) w - 8f * density - outer else 8f * density + outer
        val cy = h / 2f
        arc.set(cx - outer + ringWidth / 2, cy - outer + ringWidth / 2, cx + outer - ringWidth / 2, cy + outer - ringWidth / 2)
        ring.strokeWidth = ringWidth
        ring.color = ColorUtils.setAlphaComponent(ink, 0x38)
        canvas.drawOval(arc, ring)
        if (progress > 0f) {
            ring.color = ink
            canvas.drawArc(arc, -90f, 360f * progress, false, ring)
        }

        val inner = outer - ringWidth - 2.5f * density
        val bitmap = cover
        if (bitmap != null) {
            val scale = max(inner * 2 / bitmap.width, inner * 2 / bitmap.height)
            shaderMatrix.setScale(scale, scale)
            shaderMatrix.postTranslate(cx - bitmap.width * scale / 2, cy - bitmap.height * scale / 2)
            image.shader?.setLocalMatrix(shaderMatrix)
            canvas.drawCircle(cx, cy, inner, image)
        } else {
            fill.color = ColorUtils.setAlphaComponent(ink, 0x1F)
            canvas.drawCircle(cx, cy, inner, fill)
            if (playing) drawIcon(canvas, headphones, cx, cy, 18f, ColorUtils.setAlphaComponent(ink, 0xB3))
        }
        if (!playing) {
            if (bitmap != null) {
                fill.color = 0x73000000
                canvas.drawCircle(cx, cy, inner, fill)
            }
            drawIcon(canvas, playGlyph, cx, cy, 18f, if (bitmap != null) Color.WHITE else ink)
        }
    }

    private fun drawIcon(canvas: Canvas, icon: Drawable, cx: Float, cy: Float, sizeDp: Float, color: Int) {
        val half = (sizeDp * density / 2).toInt()
        icon.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
        icon.colorFilter = android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
        icon.draw(canvas)
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
