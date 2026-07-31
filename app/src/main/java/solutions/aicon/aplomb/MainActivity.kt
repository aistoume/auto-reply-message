package solutions.aicon.aplomb

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * 设置页 —— 一次配好就不用再来：AI、语言、自我设定、情绪档位。
 * 日常使用全在悬浮球里。
 */
class MainActivity : AppCompatActivity() {

    private val providerIds = listOf(
        Prefs.PROVIDER_ANTHROPIC, Prefs.PROVIDER_OPENAI,
        Prefs.PROVIDER_GEMINI, Prefs.PROVIDER_OPENROUTER,
    )
    private lateinit var keyArea: LinearLayout
    private lateinit var toneArea: LinearLayout
    private lateinit var relArea: LinearLayout
    private lateinit var batteryLine: TextView
    private lateinit var kbdBtn: Button

    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(40))
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.app_name); textSize = 26f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.tagline); textSize = 14f
            setTextColor(Color.GRAY); setPadding(0, dp(4), 0, dp(20))
        })

        // ── 1 · 开关 ──
        root.addView(section(getString(R.string.sec_run)))
        root.addView(Button(this).apply {
            text = getString(R.string.btn_start)
            setOnClickListener {
                if (Prefs.activeKey(this@MainActivity).isBlank()) {
                    toast(getString(R.string.need_key)); return@setOnClickListener
                }
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    )
                    toast(getString(R.string.need_overlay)); return@setOnClickListener
                }
                val svc = Intent(this@MainActivity, BubbleService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
                else startService(svc)
                toast(getString(R.string.bubble_on))
            }
        })
        root.addView(Button(this).apply {
            text = getString(R.string.btn_stop)
            setOnClickListener {
                startService(Intent(this@MainActivity, BubbleService::class.java).setAction(BubbleService.ACTION_STOP))
                toast(getString(R.string.bubble_off))
            }
        })
        kbdBtn = Button(this).apply {
            setOnClickListener {
                toast(getString(R.string.kbd_howto))
                runCatching { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
            }
        }
        root.addView(kbdBtn)

        // ── 2 · AI ──
        root.addView(section(getString(R.string.sec_ai)))
        val spinner = Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf(
                    getString(R.string.provider_anthropic), getString(R.string.provider_openai),
                    getString(R.string.provider_gemini), getString(R.string.provider_openrouter),
                ),
            )
        }
        root.addView(spinner)
        keyArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(keyArea)
        spinner.setSelection(providerIds.indexOf(Prefs.provider(this)).coerceAtLeast(0))
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                Prefs.setProvider(this@MainActivity, providerIds[pos]); renderKey()
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        renderKey()

        // ── 3 · 我的语言 ──
        root.addView(section(getString(R.string.sec_lang)))
        root.addView(TextView(this).apply {
            text = getString(R.string.lang_help); textSize = 12f; setTextColor(Color.GRAY)
        })
        root.addView(EditText(this).apply {
            hint = getString(R.string.lang_hint)
            setText(Prefs.myLanguageLabel(this@MainActivity))
            addTextChangedListener(watcher { Prefs.setMyLanguageLabel(this@MainActivity, it) })
        })

        // ── 4 · 我是谁 ──
        root.addView(section(getString(R.string.sec_persona)))
        root.addView(TextView(this).apply {
            text = getString(R.string.persona_help); textSize = 12f; setTextColor(Color.GRAY)
        })
        root.addView(EditText(this).apply {
            hint = getString(R.string.persona_hint)
            setText(Prefs.persona(this@MainActivity))
            minLines = 2
            addTextChangedListener(watcher { Prefs.setPersona(this@MainActivity, it) })
        })

        // ── 5 · 电池 ──
        root.addView(section(getString(R.string.sec_battery)))
        batteryLine = TextView(this).apply {
            text = getString(R.string.battery_claiming); textSize = 15f
        }
        root.addView(batteryLine)
        root.addView(TextView(this).apply {
            text = getString(R.string.battery_help); textSize = 12f; setTextColor(Color.GRAY)
            setPadding(0, dp(6), 0, dp(8))
        })
        root.addView(Button(this).apply {
            text = getString(R.string.battery_topup)
            setOnClickListener { Subscriptions.showPaywall(this@MainActivity) { refreshBattery() } }
        })

        // ── 6 · 对方是谁 ──
        root.addView(section(getString(R.string.sec_relations)))
        root.addView(TextView(this).apply {
            text = getString(R.string.relations_help); textSize = 12f; setTextColor(Color.GRAY)
            setPadding(0, 0, 0, dp(8))
        })
        relArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(relArea)
        root.addView(Button(this).apply {
            text = getString(R.string.rel_add)
            setOnClickListener {
                if (!RelationConfig.store.canAdd(this@MainActivity)) {
                    toast(getString(R.string.rel_full)); return@setOnClickListener
                }
                editItem(RelationConfig.store, null, isRelation = true)
            }
        })
        root.addView(Button(this).apply {
            text = getString(R.string.rel_reset)
            setOnClickListener { RelationConfig.store.reset(this@MainActivity); renderAll() }
        })

        // ── 7 · 情绪档位 ──
        root.addView(section(getString(R.string.sec_tones)))
        root.addView(TextView(this).apply {
            text = getString(R.string.tones_help); textSize = 12f; setTextColor(Color.GRAY)
            setPadding(0, 0, 0, dp(8))
        })
        toneArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(toneArea)
        root.addView(Button(this).apply {
            text = getString(R.string.tone_add)
            setOnClickListener {
                if (!ToneConfig.store.canAdd(this@MainActivity)) {
                    toast(getString(R.string.tone_full)); return@setOnClickListener
                }
                editItem(ToneConfig.store, null, isRelation = false)
            }
        })
        root.addView(Button(this).apply {
            text = getString(R.string.tone_reset)
            setOnClickListener { ToneConfig.store.reset(this@MainActivity); renderAll() }
        })
        renderAll()

        setContentView(ScrollView(this).apply { addView(root) })
        refreshBattery()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        kbdBtn.text = getString(
            if (AplombKeyboard.isActive) R.string.kbd_on else R.string.kbd_enable
        )
    }

    // ── AI key ─────────────────────────────────────────────────────────

    private fun renderKey() {
        keyArea.removeAllViews()
        val p = Prefs.provider(this)
        val saved = Prefs.keyFor(this, p)
        val input = EditText(this).apply {
            hint = when (p) {
                Prefs.PROVIDER_OPENAI -> "sk-…"
                Prefs.PROVIDER_GEMINI -> "AQ.… / AIza…"
                Prefs.PROVIDER_OPENROUTER -> "sk-or-…"
                else -> "sk-ant-…"
            }
            setText(saved)
            addTextChangedListener(watcher { Prefs.setKeyFor(this@MainActivity, p, it) })
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = if (saved.isBlank()) View.VISIBLE else View.GONE
        }
        row.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = getString(R.string.paste)
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString()?.trim()
                if (clip.isNullOrBlank()) toast(getString(R.string.clipboard_empty)) else input.setText(clip)
            }
        })
        keyArea.addView(TextView(this).apply {
            text = getString(R.string.key_saved, saved.takeLast(4))
            visibility = if (saved.isBlank()) View.GONE else View.VISIBLE
            setPadding(0, dp(10), 0, dp(10))
            setOnClickListener { visibility = View.GONE; row.visibility = View.VISIBLE }
        })
        keyArea.addView(row)
    }

    // ── 情绪档位编辑 ───────────────────────────────────────────────────

    private fun renderAll() {
        renderCatalog(relArea, RelationConfig.store, RelationConfig.load(this), isRelation = true)
        renderCatalog(toneArea, ToneConfig.store, ToneConfig.load(this), isRelation = false)
    }

    private fun renderCatalog(
        area: LinearLayout,
        catalog: Catalog,
        items: List<Tone>,
        isRelation: Boolean,
    ) {
        area.removeAllViews()
        items.forEach { t ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    setColor(Color.argb(14, 128, 128, 128)); cornerRadius = dp(10).toFloat()
                }
            }
            row.addView(TextView(this).apply {
                text = t.emoji; textSize = 22f
                setPadding(0, 0, dp(12), 0)
            })
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply { text = t.name; textSize = 15f })
                addView(TextView(this@MainActivity).apply {
                    // 「默认」关系本来就没有设定，留白看着像没加载出来，给一句说明
                    text = t.guidance.ifBlank {
                        if (isRelation) getString(R.string.rel_none_guidance) else ""
                    }
                    textSize = 11f; setTextColor(Color.GRAY)
                    maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                })
            })
            row.setOnClickListener { editItem(catalog, t, isRelation) }
            area.addView(row)
            area.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(1, dp(8))
            })
        }
    }

    /** 编辑一格 —— 语气和关系共用，差别只在标题和提示语。 */
    private fun editItem(catalog: Catalog, existing: Tone?, isRelation: Boolean) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val emoji = EditText(this).apply {
            hint = getString(R.string.tone_emoji_hint); setText(existing?.emoji ?: "💬")
        }
        val name = EditText(this).apply {
            hint = getString(if (isRelation) R.string.rel_name_hint else R.string.tone_name_hint)
            setText(existing?.name ?: "")
        }
        val guidance = EditText(this).apply {
            hint = getString(if (isRelation) R.string.rel_guidance_hint else R.string.tone_guidance_hint)
            setText(existing?.guidance ?: "")
            minLines = 3
        }
        box.addView(emoji); box.addView(name); box.addView(guidance)

        // 颜色：一排预设点
        val palette = listOf(
            0xF23B82F6.toInt(), 0xF2D97706.toInt(), 0xF2DC2626.toInt(),
            0xF210B981.toInt(), 0xF2A855F7.toInt(), 0xF264748B.toInt(),
        )
        var picked = existing?.color ?: palette[0]
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0)
        }
        fun paintStrip() {
            strip.removeAllViews()
            palette.forEach { colour ->
                strip.addView(TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { rightMargin = dp(10) }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL; setColor(colour)
                        if (colour == picked) setStroke(dp(3), Color.BLACK)
                    }
                    setOnClickListener { picked = colour; paintStrip() }
                })
            }
        }
        paintStrip()
        box.addView(strip)

        val addTitle = if (isRelation) R.string.rel_add else R.string.tone_add
        val editTitle = if (isRelation) R.string.rel_edit else R.string.tone_edit
        val b = AlertDialog.Builder(this)
            .setTitle(if (existing == null) addTitle else editTitle)
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val n = name.text.toString().trim()
                val g = guidance.text.toString().trim()
                // 关系可以只有名字没设定（「默认」就是这样）；语气必须说清怎么说话
                if (n.isBlank() || (!isRelation && g.isBlank())) {
                    toast(getString(R.string.tone_incomplete)); return@setPositiveButton
                }
                catalog.upsert(
                    this,
                    Tone(
                        existing?.id ?: java.util.UUID.randomUUID().toString(),
                        emoji.text.toString().trim().ifBlank { "💬" }, n, picked, g,
                    ),
                )
                renderAll()
            }
            .setNegativeButton(android.R.string.cancel, null)
        if (existing != null) {
            b.setNeutralButton(R.string.tone_delete) { _, _ ->
                if (catalog.load(this).size <= 1) {
                    toast(getString(R.string.tone_need_one)); return@setNeutralButton
                }
                catalog.remove(this, existing.id); renderAll()
            }
        }
        b.show()
    }

    // ── 电池 ───────────────────────────────────────────────────────────

    /**
     * 刷电池条。自带 key 的人根本不走服务器，显示格数只会让人以为额度用完了
     * 就不能用了 —— 所以那种情况直接说明在用自己的 key。
     */
    private fun refreshBattery() {
        if (Prefs.activeKey(this).isNotBlank()) {
            batteryLine.text = getString(R.string.battery_own_key)
            return
        }
        batteryLine.text = getString(R.string.battery_claiming)
        Thread {
            val b = BatteryClient.battery(this)
            runOnUiThread {
                batteryLine.text = when {
                    b == null -> getString(R.string.battery_offline)
                    b.bars <= 0 -> getString(R.string.battery_empty)
                    b.subscribed -> getString(R.string.battery_month, b.bars, b.barsTotal)
                    else -> getString(R.string.battery_free, b.bars, b.barsTotal)
                }
            }
        }.start()
    }

    // ── 小工具 ─────────────────────────────────────────────────────────

    private fun section(title: String) = TextView(this).apply {
        text = title; textSize = 16f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(28), 0, dp(8))
    }

    private fun watcher(onChange: (String) -> Unit) = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) = onChange(s?.toString().orEmpty())
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
