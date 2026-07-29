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

    /** 上次用的档位 id —— 轮盘默认高亮它。 */
    fun lastTone(c: Context): String = sp(c).getString("last_tone", "") ?: ""
    fun setLastTone(c: Context, v: String) = sp(c).edit().putString("last_tone", v).apply()
}
