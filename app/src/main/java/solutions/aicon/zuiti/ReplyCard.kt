package solutions.aicon.zuiti

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * 回复卡 —— 悬浮在聊天界面上方，机主在这里读潜台词、挑语气、一键填入。
 *
 * 卡片本身不发消息：填入输入框之后，发送键仍然由机主自己按。
 */
object ReplyCard {
    private var current: View? = null

    fun dismiss(wm: WindowManager) {
        current?.let { runCatching { wm.removeView(it) } }
        current = null
    }

    /** 只显示一行状态（生成中 / 出错）。 */
    fun showStatus(c: Context, wm: WindowManager, text: String) {
        show(c, wm, null, null, text, emptyList(), {}, {})
    }

    /**
     * @param draft    生成结果；null = 仅状态
     * @param tones    情绪切换条，点一下换档重出
     * @param onTone   换档回调
     * @param onInsert 填入输入框
     */
    fun show(
        c: Context,
        wm: WindowManager,
        draft: ZuitiEngine.Draft?,
        activeTone: Tone?,
        status: String?,
        tones: List<Tone>,
        onTone: (Tone) -> Unit,
        onInsert: (String) -> Unit,
    ) {
        dismiss(wm)
        val d = c.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.argb(246, 24, 24, 27)); cornerRadius = 20 * d
            }
            setPadding(dp(18), dp(14), dp(18), dp(12))
        }

        // ── 头：标题 + 关闭 ──
        val head = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(TextView(c).apply {
            text = if (activeTone != null) "${activeTone.emoji} ${activeTone.name}"
            else c.getString(R.string.app_name)
            setTextColor(Color.rgb(245, 158, 11)); textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        draft?.theirLanguage?.takeIf { it.isNotBlank() }?.let {
            head.addView(TextView(c).apply {
                text = "🌐 $it"
                setTextColor(Color.GRAY); textSize = 11f
                setPadding(0, 0, dp(10), 0)
            })
        }
        head.addView(TextView(c).apply {
            text = "✕"; setTextColor(Color.LTGRAY); textSize = 17f
            setPadding(dp(8), 0, dp(2), dp(4))
            setOnClickListener { dismiss(wm) }
        })
        card.addView(head)

        if (status != null) {
            card.addView(TextView(c).apply {
                text = status; setTextColor(Color.WHITE); textSize = 14f
                setPadding(0, dp(10), 0, dp(6))
            })
        }

        if (draft != null) {
            // ── 潜台词（机主语言）──
            if (draft.subtext.isNotBlank()) {
                card.addView(TextView(c).apply {
                    text = c.getString(R.string.card_subtext, draft.subtext)
                    setTextColor(Color.rgb(190, 190, 200)); textSize = 12f
                    setPadding(0, dp(8), 0, 0)
                })
            }
            if (draft.risk.isNotBlank() && draft.risk != "无" && !draft.risk.equals("none", true)) {
                card.addView(TextView(c).apply {
                    text = c.getString(R.string.card_risk, draft.risk)
                    setTextColor(Color.rgb(251, 191, 36)); textSize = 11f
                    setPadding(0, dp(4), 0, 0)
                })
            }

            // ── 正文（对方语言）——卡片主体 ──
            val replyBox = ScrollView(c).apply {
                background = GradientDrawable().apply {
                    setColor(Color.argb(28, 255, 255, 255)); cornerRadius = 12 * d
                }
                setPadding(dp(12), dp(10), dp(12), dp(10))
                addView(TextView(c).apply {
                    text = draft.reply
                    setTextColor(Color.WHITE); textSize = 16f
                    setLineSpacing(0f, 1.25f)
                })
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (c.resources.displayMetrics.heightPixels * 0.26f).toInt(),
                ).apply { topMargin = dp(10) }
            }
            card.addView(replyBox)

            if (draft.note.isNotBlank()) {
                card.addView(TextView(c).apply {
                    text = c.getString(R.string.card_note, draft.note)
                    setTextColor(Color.rgb(150, 150, 160)); textSize = 11f
                    setPadding(0, dp(8), 0, 0)
                })
            }

            // ── 动作：填入 / 复制 ──
            val actions = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(12), 0, 0)
            }
            actions.addView(pill(c, c.getString(R.string.card_insert), Color.rgb(5, 150, 105)) {
                onInsert(draft.reply)
            })
            actions.addView(pill(c, c.getString(R.string.card_copy), Color.rgb(63, 63, 70)) {
                (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("zuiti", draft.reply))
                Toast.makeText(c, R.string.card_copied, Toast.LENGTH_SHORT).show()
            })
            card.addView(actions)
        }

        // ── 情绪切换条：随时换档重出，不用退回轮盘 ──
        if (tones.isNotEmpty()) {
            val strip = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            tones.forEach { t ->
                val on = t.id == activeTone?.id
                strip.addView(TextView(c).apply {
                    text = "${t.emoji} ${t.name}"
                    setTextColor(if (on) Color.WHITE else Color.rgb(170, 170, 180))
                    textSize = 12f
                    background = GradientDrawable().apply {
                        setColor(if (on) t.color else Color.argb(30, 255, 255, 255))
                        cornerRadius = 999f
                    }
                    setPadding(dp(14), dp(6), dp(14), dp(6))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { rightMargin = dp(8) }
                    setOnClickListener { onTone(t) }
                })
            }
            card.addView(HorizontalScrollView(c).apply {
                isHorizontalScrollBarEnabled = false
                setPadding(0, dp(12), 0, 0)
                addView(strip)
            })
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            c.resources.displayMetrics.widthPixels - dp(20),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // 不抢焦点：机主还要在下面的输入框里打字/按发送
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP; y = dp(56) }
        current = card
        runCatching { wm.addView(card, lp) }
    }

    private fun pill(c: Context, label: String, colour: Int, onTap: () -> Unit): TextView {
        val d = c.resources.displayMetrics.density
        return TextView(c).apply {
            text = label
            setTextColor(Color.WHITE); textSize = 14f
            background = GradientDrawable().apply { setColor(colour); cornerRadius = 999f }
            setPadding((22 * d).toInt(), (8 * d).toInt(), (22 * d).toInt(), (8 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { rightMargin = (10 * d).toInt() }
            setOnClickListener { onTap() }
        }
    }
}
