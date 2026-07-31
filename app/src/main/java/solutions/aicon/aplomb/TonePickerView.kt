package solutions.aicon.aplomb

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 语气选择面板 —— 点悬浮球后从底部弹出。
 *
 * 换掉原来的扇形轮盘：轮盘绕着悬浮球铺开，一屏最多摆 6 格就开始互相压，
 * 而档位现在有 10 个、上面还多了一层关系。网格能一眼扫完，也不受悬浮球
 * 位置影响 —— 球贴在角上时轮盘有一半格子会被挤到屏幕外。
 *
 * 上排是关系（单选，选完留着，下次还是它），下面是语气网格（点一下即出稿）。
 */
@SuppressLint("ViewConstructor")
class TonePickerView(
    context: Context,
    private val onPick: (relation: Tone, tone: Tone) -> Unit,
    private val onDismiss: () -> Unit,
) : LinearLayout(context) {

    private val d = context.resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    private var relation: Tone = RelationConfig.current(context)
    private lateinit var relRow: LinearLayout

    init {
        orientation = VERTICAL
        gravity = Gravity.BOTTOM
        setBackgroundColor(Color.argb(140, 0, 0, 0))
        // 点面板外的暗区 = 取消。子 view 自己消费掉事件，所以不会误触。
        setOnClickListener { onDismiss() }

        val sheet = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(26))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2FFFFFF"))
                cornerRadii = floatArrayOf(
                    dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(),
                    0f, 0f, 0f, 0f,
                )
            }
            isClickable = true   // 吃掉点击，别穿透到背景的取消
        }

        sheet.addView(label(context.getString(R.string.picker_who)))
        relRow = LinearLayout(context).apply { orientation = VERTICAL }
        sheet.addView(relRow)
        renderRelations()

        sheet.addView(label(context.getString(R.string.picker_tone)).apply {
            setPadding(0, dp(16), 0, dp(8))
        })
        sheet.addView(toneGrid())

        addView(
            sheet,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun label(text: String) = TextView(context).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.parseColor("#6B7280"))
        setPadding(dp(4), 0, 0, dp(8))
    }

    // ── 关系：横向 chip，自动换行 ────────────────────────────────────

    private fun renderRelations() {
        relRow.removeAllViews()
        val items = RelationConfig.load(context)
        var line = newLine()
        var used = 0
        val maxW = context.resources.displayMetrics.widthPixels - dp(32)

        items.forEach { r ->
            val chip = relationChip(r)
            // 估宽：emoji + 文字 + 内边距。测量一次比精确布局便宜得多。
            val w = dp(28) + (r.name.length * dp(11)) + dp(24)
            if (used + w > maxW && used > 0) {
                relRow.addView(line); line = newLine(); used = 0
            }
            line.addView(chip)
            used += w + dp(8)
        }
        if (line.childCount > 0) relRow.addView(line)
    }

    private fun newLine() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        setPadding(0, 0, 0, dp(8))
    }

    private fun relationChip(r: Tone): View {
        val on = r.id == relation.id
        return TextView(context).apply {
            text = "${r.emoji}  ${r.name}"
            textSize = 13f
            setTextColor(if (on) Color.WHITE else Color.parseColor("#111827"))
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = GradientDrawable().apply {
                setColor(if (on) r.color else Color.parseColor("#EFEFF2"))
                cornerRadius = dp(999).toFloat()
            }
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { rightMargin = dp(8) }
            setOnClickListener {
                relation = r
                Prefs.setRelationId(context, r.id)
                renderRelations()
            }
        }
    }

    // ── 语气：四列网格 ────────────────────────────────────────────────

    private fun toneGrid(): View {
        val tones = ToneConfig.load(context)
        val cols = 4
        val grid = LinearLayout(context).apply { orientation = VERTICAL }
        val cellW = (context.resources.displayMetrics.widthPixels - dp(32) - dp(8) * (cols - 1)) / cols
        val last = Prefs.lastTone(context)

        tones.chunked(cols).forEach { rowItems ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                setPadding(0, 0, 0, dp(8))
            }
            rowItems.forEach { t ->
                row.addView(toneCell(t, cellW, t.id == last))
            }
            grid.addView(row)
        }

        // 档位多了以后一屏未必装得下，包一层滚动；限高避免面板顶到状态栏。
        return ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(grid)
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                minOf(
                    dp(360),
                    (context.resources.displayMetrics.heightPixels * 0.45f).toInt(),
                ),
            )
        }
    }

    private fun toneCell(t: Tone, w: Int, wasLast: Boolean): View {
        val box = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(10))
            background = GradientDrawable().apply {
                setColor(t.color)
                cornerRadius = dp(14).toFloat()
                // 上次用过的加一圈白边 —— 十个格子里找回上次那个，全靠这个
                if (wasLast) setStroke(dp(3), Color.WHITE)
            }
            layoutParams = LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = dp(8)
            }
            setOnClickListener {
                Prefs.setLastTone(context, t.id)
                onPick(relation, t)
            }
        }
        box.addView(TextView(context).apply {
            text = t.emoji
            textSize = 24f
            gravity = Gravity.CENTER
        })
        box.addView(TextView(context).apply {
            text = t.name
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(2), dp(4), dp(2), 0)
        })
        return box
    }
}
