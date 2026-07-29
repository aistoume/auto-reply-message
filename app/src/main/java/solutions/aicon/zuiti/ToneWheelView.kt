package solutions.aicon.zuiti

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 情绪轮盘 —— 点悬浮球后扇形展开，选哪一格就用哪种语气回。
 *
 * 围绕悬浮球当前位置铺开，并自动朝屏幕中心一侧展开，所以球贴在哪条边
 * 都不会把格子甩出屏外。支持 1–6 格（[ToneConfig.MAX_TONES]）。
 */
@SuppressLint("ViewConstructor")
class ToneWheelView(
    context: Context,
    private val cx: Float,
    private val cy: Float,
    private val tones: List<Tone>,
    private val onSelect: (Tone) -> Unit,
    private val onDismiss: () -> Unit,
) : View(context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val radius = dp(112f)
    private val buttonR = dp(38f)

    /** 每格的圆心坐标。 */
    private val positions: List<Pair<Tone, Pair<Float, Float>>>

    init {
        isFocusableInTouchMode = true
        val sw = context.resources.displayMetrics.widthPixels
        val sh = context.resources.displayMetrics.heightPixels
        // 朝屏幕中心展开
        val base = Math.toDegrees(
            Math.atan2((sw / 2f - cx).toDouble(), -(sh / 2f - cy).toDouble())
        ).toFloat()
        val step = when (tones.size) { 1 -> 0f; 2 -> 52f; 3 -> 48f; 4 -> 44f; else -> 40f }
        positions = tones.mapIndexed { i, t ->
            val ang = base + (i - (tones.size - 1) / 2f) * step
            val rad = Math.toRadians(ang.toDouble())
            t to ((cx + (sin(rad) * radius).toFloat()) to (cy - (cos(rad) * radius).toFloat()))
        }
    }

    private var highlight: String? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(2f); color = Color.argb(160, 255, 255, 255)
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(4f); color = Color.WHITE
    }
    private val connector = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(2f); color = Color.argb(110, 255, 255, 255)
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = dp(26f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = dp(12f)
        color = Color.WHITE; isFakeBoldText = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.argb(105, 0, 0, 0))
        val last = Prefs.lastTone(context)
        positions.forEach { (tone, p) ->
            val (x, y) = p
            canvas.drawLine(cx, cy, x, y, connector)
            fill.color = tone.color
            canvas.drawCircle(x, y, buttonR, fill)
            canvas.drawCircle(x, y, buttonR, if (highlight == tone.id) glow else border)
            // 上次用过的档位加一圈细提示
            if (highlight == null && tone.id == last) {
                canvas.drawCircle(x, y, buttonR + dp(5f), border)
            }
            canvas.drawText(tone.emoji, x, y - dp(2f), glyphPaint)
            canvas.drawText(tone.name, x, y + buttonR + dp(16f), labelPaint)
        }
    }

    private fun toneAt(sx: Float, sy: Float): Tone? =
        positions.firstOrNull { (_, p) -> hypot(sx - p.first, sy - p.second) <= buttonR + dp(8f) }?.first

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val t = toneAt(e.rawX, e.rawY)
                if (t?.id != highlight) { highlight = t?.id; invalidate() }
            }
            MotionEvent.ACTION_UP -> {
                val t = toneAt(e.rawX, e.rawY)
                if (t != null) onSelect(t) else onDismiss()
            }
            MotionEvent.ACTION_CANCEL -> onDismiss()
        }
        return true
    }
}
