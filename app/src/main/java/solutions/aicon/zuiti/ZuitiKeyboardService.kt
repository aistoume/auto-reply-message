package solutions.aicon.zuiti

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 嘴替键盘 —— 只做一件事：把选中的回复送进当前 app 的输入框。
 *
 * Android 允许一个 app 往别人的输入框里写字的正规途径只有输入法，所以
 * 这里存在一个「键盘」。它不打字：没有键位，只有一条状态栏和一个「换回
 * 我的键盘」。填入之后发送键仍然由机主自己按。
 */
class ZuitiKeyboardService : InputMethodService() {

    private var status: TextView? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(24, 24, 27))
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        status = TextView(this).apply {
            text = getString(R.string.kbd_hint)
            setTextColor(Color.WHITE); textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        bar.addView(status)
        bar.addView(TextView(this).apply {
            text = getString(R.string.kbd_hand_back)
            setTextColor(Color.rgb(245, 158, 11)); textSize = 14f
            setPadding(dp(12), 0, 0, 0)
            setOnClickListener { handBack() }
        })
        return bar
    }

    fun setStatus(text: String) = status?.post { status?.text = text }

    /** 把 [text] 写进当前聚焦的输入框；没有输入框时返回 false。 */
    fun commit(text: String): Boolean {
        val ic = currentInputConnection ?: return false
        ic.beginBatchEdit()
        // 清掉已有内容，反复填入不会叠加
        ic.deleteSurroundingText(MAX_CLEAR, MAX_CLEAR)
        val ok = ic.commitText(text, 1)
        ic.endBatchEdit()
        return ok
    }

    /** 交还给机主原来的键盘。 */
    fun handBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) switchToPreviousInputMethod()
        else @Suppress("DEPRECATION")
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .showInputMethodPicker()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        status?.text = getString(R.string.kbd_hint)
    }

    companion object {
        private const val MAX_CLEAR = 2000

        @Volatile
        var instance: ZuitiKeyboardService? = null
            private set

        val isActive: Boolean get() = instance != null
    }
}
