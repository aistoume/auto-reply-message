package solutions.aicon.aplomb

import android.content.Context

/** 机主的设置 —— 全部只存在本机。 */
object Prefs {
    private const val FILE = "aplomb"

    const val PROVIDER_ANTHROPIC = "anthropic"
    const val PROVIDER_OPENAI = "openai"
    const val PROVIDER_GEMINI = "gemini"
    const val PROVIDER_OPENROUTER = "openrouter"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun provider(c: Context): String = sp(c).getString("provider", PROVIDER_ANTHROPIC) ?: PROVIDER_ANTHROPIC
    fun setProvider(c: Context, v: String) = sp(c).edit().putString("provider", v).apply()

    /** 每家 key 各存一格，来回切不丢。 */
    fun keyFor(c: Context, provider: String): String = sp(c).getString("key_$provider", "") ?: ""
    fun setKeyFor(c: Context, provider: String, k: String) =
        sp(c).edit().putString("key_$provider", k.trim()).apply()

    fun activeKey(c: Context): String = keyFor(c, provider(c))

    /**
     * 机主自己的语言 —— 分析和建议永远用这个写。空 = 跟随系统。
     * 存的是语言名（"中文" / "English"），直接拼进 prompt 最省事。
     */
    fun myLanguageLabel(c: Context): String {
        val saved = sp(c).getString("my_lang", "") ?: ""
        if (saved.isNotBlank()) return saved
        val tag = java.util.Locale.getDefault().language
        return if (tag == "zh") "中文" else "English"
    }
    fun setMyLanguageLabel(c: Context, v: String) = sp(c).edit().putString("my_lang", v).apply()

    /** 机主的自我设定：身份、口头禅、底线，拼进 prompt。 */
    fun persona(c: Context): String = sp(c).getString("persona", "") ?: ""
    fun setPersona(c: Context, v: String) = sp(c).edit().putString("persona", v).apply()

    /** 上次用的档位 id —— 选择器默认高亮它。 */
    fun lastTone(c: Context): String = sp(c).getString("last_tone", "") ?: ""
    fun setLastTone(c: Context, v: String) = sp(c).edit().putString("last_tone", v).apply()

    /** 上次选的关系 id —— 默认「没有特殊关系」。 */
    fun relationId(c: Context): String = sp(c).getString("relation_id", "none") ?: "none"
    fun setRelationId(c: Context, v: String) = sp(c).edit().putString("relation_id", v).apply()

    // ── 电池 ──────────────────────────────────────────────────────────
    // 自带 key 时完全绕开服务器，所以这几项只在「没填 key」的路径上用。

    /** 匿名设备 id —— 只用来记额度，第一次用时生成。不是广告 id。 */
    fun deviceId(c: Context): String {
        val saved = sp(c).getString("device_id", "") ?: ""
        if (saved.isNotBlank()) return saved
        val fresh = java.util.UUID.randomUUID().toString()
        sp(c).edit().putString("device_id", fresh).apply()
        return fresh
    }

    /** 服务器换回来的电池令牌。 */
    fun batteryToken(c: Context): String = sp(c).getString("battery_token", "") ?: ""
    fun setBatteryToken(c: Context, v: String) = sp(c).edit().putString("battery_token", v).apply()
}
