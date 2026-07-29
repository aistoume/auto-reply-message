package solutions.aicon.zuiti

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 情绪档位 — 轮盘上的每一格。
 *
 * 出厂三档沿用「嘴替」方法论的分级（体面 / 绵里藏针 / 掀桌），第四格
 * 「冷处理」补上「先不表态」这个真实高频需求。每一格的 [guidance] 会
 * 原样拼进 prompt，所以用户自定义一格 = 自定义一种说话方式。
 */
class Tone(
    val id: String,
    val emoji: String,
    val name: String,
    /** 0xAARRGGBB — 轮盘按钮颜色。 */
    val color: Int,
    /** 写给模型的档位说明（用户可改）。 */
    val guidance: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("emoji", emoji).put("name", name)
        .put("color", color).put("guidance", guidance)

    companion object {
        fun fromJson(o: JSONObject) = Tone(
            o.optString("id", java.util.UUID.randomUUID().toString()),
            o.optString("emoji", "💬"),
            o.optString("name", ""),
            if (o.has("color")) o.optInt("color") else 0xF23B82F6.toInt(),
            o.optString("guidance"),
        )
    }
}

object ToneConfig {
    private const val FILE = "zuiti"
    private const val KEY = "tones_v1"

    /** 出厂四档。名称走 string 资源，所以中英界面各自成立。 */
    fun defaults(c: Context): List<Tone> = listOf(
        Tone(
            "decent", "🙂", c.getString(R.string.tone_decent), 0xF23B82F6.toInt(),
            c.getString(R.string.tone_decent_guidance),
        ),
        Tone(
            "needle", "🪡", c.getString(R.string.tone_needle), 0xF2D97706.toInt(),
            c.getString(R.string.tone_needle_guidance),
        ),
        Tone(
            "table", "🔥", c.getString(R.string.tone_table), 0xF2DC2626.toInt(),
            c.getString(R.string.tone_table_guidance),
        ),
        Tone(
            "cool", "🧊", c.getString(R.string.tone_cool), 0xF264748B.toInt(),
            c.getString(R.string.tone_cool_guidance),
        ),
    )

    fun load(c: Context): List<Tone> {
        val raw = c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return defaults(c)
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Tone.fromJson(arr.getJSONObject(it)) }
                .takeIf { it.isNotEmpty() } ?: defaults(c)
        }.getOrElse { defaults(c) }
    }

    fun save(c: Context, tones: List<Tone>) {
        val arr = JSONArray().also { a -> tones.forEach { a.put(it.toJson()) } }
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY, arr.toString()).apply()
    }

    fun reset(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY).apply()

    /** 轮盘一屏最多放 6 格，再多就挤了。 */
    const val MAX_TONES = 6
}
