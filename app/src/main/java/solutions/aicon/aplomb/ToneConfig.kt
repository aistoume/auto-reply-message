package solutions.aicon.aplomb

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 一格「带标签的 prompt 片段」—— 语气和关系共用这一个结构。
 *
 * [guidance] 会原样拼进 prompt，所以用户改一格 = 改一种说话方式。
 */
class Tone(
    val id: String,
    val emoji: String,
    val name: String,
    /** 0xAARRGGBB。 */
    val color: Int,
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

/**
 * 语气 / 关系的存储。
 *
 * 关键取舍：**内置项不整份落盘**。落盘之后用户切了系统语言，名字还留在
 * 旧语言里，看着像 app 坏了。所以只存三样 —— 改过的内置项、自己加的项、
 * 删掉的内置项 —— 内置项文案每次按当前 Locale 现取。
 */
class Catalog(
    private val key: String,
    private val builtins: (Context) -> List<Tone>,
    val maxItems: Int,
) {
    private companion object { const val FILE = "aplomb" }

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private class Saved(
        val overrides: MutableMap<String, Tone> = mutableMapOf(),
        val customs: MutableList<Tone> = mutableListOf(),
        val hidden: MutableSet<String> = mutableSetOf(),
    )

    private fun read(c: Context): Saved {
        val raw = sp(c).getString(key, null) ?: return Saved()
        return runCatching {
            val o = JSONObject(raw)
            val s = Saved()
            o.optJSONObject("overrides")?.let { ov ->
                ov.keys().forEach { k -> s.overrides[k] = Tone.fromJson(ov.getJSONObject(k)) }
            }
            o.optJSONArray("customs")?.let { arr ->
                (0 until arr.length()).forEach { s.customs.add(Tone.fromJson(arr.getJSONObject(it))) }
            }
            o.optJSONArray("hidden")?.let { arr ->
                (0 until arr.length()).forEach { s.hidden.add(arr.getString(it)) }
            }
            s
        }.getOrElse { Saved() }
    }

    private fun write(c: Context, s: Saved) {
        val ov = JSONObject().also { j -> s.overrides.forEach { (k, v) -> j.put(k, v.toJson()) } }
        val cu = JSONArray().also { a -> s.customs.forEach { a.put(it.toJson()) } }
        val hi = JSONArray().also { a -> s.hidden.forEach { a.put(it) } }
        val o = JSONObject().put("overrides", ov).put("customs", cu).put("hidden", hi)
        sp(c).edit().putString(key, o.toString()).apply()
    }

    fun load(c: Context): List<Tone> {
        val s = read(c)
        val list = builtins(c)
            .filterNot { s.hidden.contains(it.id) }
            .map { s.overrides[it.id] ?: it }
            .toMutableList()
        list.addAll(s.customs)
        return list.ifEmpty { builtins(c) }
    }

    /** 内置项记成 override，自定义的整条存。 */
    fun upsert(c: Context, tone: Tone) {
        val s = read(c)
        when {
            builtins(c).any { it.id == tone.id } -> s.overrides[tone.id] = tone
            else -> {
                val i = s.customs.indexOfFirst { it.id == tone.id }
                if (i >= 0) s.customs[i] = tone else s.customs.add(tone)
            }
        }
        s.hidden.remove(tone.id)
        write(c, s)
    }

    fun remove(c: Context, id: String) {
        val s = read(c)
        s.customs.removeAll { it.id == id }
        s.overrides.remove(id)
        if (builtins(c).any { it.id == id }) s.hidden.add(id)
        write(c, s)
    }

    fun reset(c: Context) = sp(c).edit().remove(key).apply()

    fun canAdd(c: Context) = load(c).size < maxItems
}

object ToneConfig {
    /**
     * 出厂十档 —— 按「从暖到远」排。
     *
     * 早先只有四档，全是防守型的；但日常聊天里要把关系往前推的场合其实
     * 更多，只给防守档等于逼用户在「客气地拒绝」和「翻脸」之间二选一。
     */
    fun defaults(c: Context): List<Tone> = listOf(
        Tone("warm", "😊", c.getString(R.string.tone_warm), 0xF2F97316.toInt(), c.getString(R.string.tone_warm_guidance)),
        Tone("thanks", "🙏", c.getString(R.string.tone_thanks), 0xF2EAB308.toInt(), c.getString(R.string.tone_thanks_guidance)),
        Tone("yes", "👍", c.getString(R.string.tone_yes), 0xF222C55E.toInt(), c.getString(R.string.tone_yes_guidance)),
        Tone("humor", "😄", c.getString(R.string.tone_humor), 0xF206B6D4.toInt(), c.getString(R.string.tone_humor_guidance)),
        Tone("comfort", "🤗", c.getString(R.string.tone_comfort), 0xF2F472B6.toInt(), c.getString(R.string.tone_comfort_guidance)),
        Tone("decent", "🙂", c.getString(R.string.tone_decent), 0xF23B82F6.toInt(), c.getString(R.string.tone_decent_guidance)),
        Tone("ask", "❓", c.getString(R.string.tone_ask), 0xF28B5CF6.toInt(), c.getString(R.string.tone_ask_guidance)),
        Tone("needle", "🪡", c.getString(R.string.tone_needle), 0xF2D97706.toInt(), c.getString(R.string.tone_needle_guidance)),
        Tone("table", "🔥", c.getString(R.string.tone_table), 0xF2DC2626.toInt(), c.getString(R.string.tone_table_guidance)),
        Tone("cool", "🧊", c.getString(R.string.tone_cool), 0xF264748B.toInt(), c.getString(R.string.tone_cool_guidance)),
    )

    /** v2 —— v1 存的是整份档位列表，格式和数量都变了，直接换 key 让老数据自然失效。 */
    val store = Catalog("tones_v2", ::defaults, maxItems = 14)

    fun load(c: Context) = store.load(c)
    const val MAX_TONES = 14
}

object RelationConfig {
    /** 第一项「默认」的 guidance 为空 = 不往 prompt 里加任何东西。 */
    fun defaults(c: Context): List<Tone> = listOf(
        Tone("none", "👤", c.getString(R.string.rel_none), 0xF264748B.toInt(), ""),
        Tone("family", "🏠", c.getString(R.string.rel_family), 0xF2EF4444.toInt(), c.getString(R.string.rel_family_guidance)),
        Tone("friend", "🫂", c.getString(R.string.rel_friend), 0xF210B981.toInt(), c.getString(R.string.rel_friend_guidance)),
        Tone("partner", "❤️", c.getString(R.string.rel_partner), 0xF2EC4899.toInt(), c.getString(R.string.rel_partner_guidance)),
        Tone("client", "💼", c.getString(R.string.rel_client), 0xF23B82F6.toInt(), c.getString(R.string.rel_client_guidance)),
        Tone("boss", "🎩", c.getString(R.string.rel_boss), 0xF2A855F7.toInt(), c.getString(R.string.rel_boss_guidance)),
    )

    val store = Catalog("relations_v1", ::defaults, maxItems = 10)

    fun load(c: Context) = store.load(c)

    /** 上次选的那个；找不到就回落到「默认」。 */
    fun current(c: Context): Tone {
        val list = load(c)
        return list.firstOrNull { it.id == Prefs.relationId(c) } ?: list.first()
    }
}
