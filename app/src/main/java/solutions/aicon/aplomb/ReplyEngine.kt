package solutions.aicon.aplomb

import android.content.Context
import org.json.JSONObject
import solutions.aicon.aplomb.ai.AnthropicClient
import solutions.aicon.aplomb.ai.GeminiClient
import solutions.aicon.aplomb.ai.OpenAIClient

/**
 * 嘴替引擎 —— 把一屏聊天变成「能发出去」的一句话。
 *
 * 方法论沿用 zuiti-skill 的五层结构，但为手机场景做了取舍：潜台词翻译、
 * 误读风险、按档位出的回复、发送建议全保留；长期策略移到卡片的展开区，
 * 因为站在对话框前的人先要的是「现在发什么」。
 *
 * 语言是这里的关键分工：
 *   • 回复正文 → 跟对方说的语言走（对方发英文就回英文）
 *   • 分析/建议 → 一律用机主自己的语言，机主永远读母语
 */
object ReplyEngine {

    class Draft(
        /** 对方使用的语言（BCP-47 或语言名，仅供展示）。 */
        val theirLanguage: String,
        /** 潜台词翻译 —— 机主语言。 */
        val subtext: String,
        /** 误读风险 —— 机主语言；模型不确定时会写在这里。 */
        val risk: String,
        /** 可直接发送的正文 —— 对方语言。 */
        val reply: String,
        /** 正文的回译 —— 机主语言；同语言时为空。
         *  正文可能是机主读不顺的语言，发出去前得知道自己在说什么。 */
        val replyGloss: String,
        /** 发送建议 / 长期策略 —— 机主语言。 */
        val note: String,
    )

    /**
     * @param b64      当前聊天界面的截图
     * @param tone     选中的情绪档位
     * @param extra    用户临时补充的意图（可空）
     */
    fun draft(context: Context, b64: String, tone: Tone, extra: String = ""): Draft? {
        val myLang = Prefs.myLanguageLabel(context)
        val persona = Prefs.persona(context).trim()
        val prompt = buildPrompt(tone, myLang, persona, extra)
        val raw = runCatching { vision(context, b64, prompt) }.getOrNull() ?: return null
        return parse(raw)
    }

    private fun buildPrompt(tone: Tone, myLang: String, persona: String, extra: String): String {
        val personaLine = if (persona.isBlank()) ""
        else "\n机主补充的自我设定（影响语气与身份，但不改变上面的分档要求）：$persona"
        val extraLine = if (extra.isBlank()) ""
        else "\n机主这次特别交代（优先满足）：$extra"
        return """
你是「嘴替」——替机主回那些不好回的消息。你面前是一张聊天截图，请先看懂，再替机主说话。

先判断谁是谁：截图里靠右/用主题色的气泡是机主自己发的，靠左的是对方。最新一条通常在底部。

然后按下面的档位替机主写回复。

【本次档位】${tone.name}
【档位要求】${tone.guidance}$personaLine$extraLine

【第一步：先认语言 —— 别跳过】
看清楚这段对话在用什么语言。可能是中文、英文、日文…也可能是**混着用**（中英夹杂在很多人的聊天里是常态）。
判断依据按优先级：① 对方最后一条消息的语言；② 整段对话里对方用得最多的语言。
如果对方本来就混着用，你**也照着混**——别强行统一成一种，那不像同一个人在说话。

【语言规则】
- reply（正文）：用你上面认出来的语言写，包括混用的比例。像母语者在手机上打的字，不要翻译腔。
- replyGloss（回译）：把 reply 完整翻成 $myLang，让机主知道自己正要发出去的是什么。如果 reply 本身就是 $myLang，这里留空字符串。
- theirLanguage：如实写出你认出的语言（混用就写「中英夹杂」这样）。
- subtext / risk / note：一律用 $myLang 写给机主看。

【分寸底线 —— 任何档位都适用】
- 不要脏字、人身攻击、威胁、造谣。
- 最狠的档位也是「把规则讲清楚、守住底线」，不是撕破脸骂人。
- 不替机主承诺钱、时间、法律责任等你无从知晓的事；需要机主自己定的，写进 note 提醒他，别写进 reply。
- 涉及家暴、自伤、人身安全的对话，reply 要保守，note 里提示走现实求助渠道。

只输出下面这个 JSON，不要代码块、不要多余的话：
{
  "theirLanguage": "对方使用的语言",
  "subtext": "潜台词翻译：对方这句话真正想干什么（施压/试探/甩锅/卖惨/铺垫要求…），一到两句说透",
  "risk": "误读风险：截图信息不足或有歧义的地方；判断很确定就写「无」",
  "reply": "可以直接发出去的正文，只要正文本身，不要引号不要解释",
  "replyGloss": "reply 的回译（机主语言）；reply 已是机主语言时留空",
  "note": "发送建议：这句发出去会怎样、后续可能怎么走、机主要自己确认什么"
}
""".trim()
    }

    private fun parse(raw: String): Draft? {
        val body = raw.substringAfter('{', "").substringBeforeLast('}', "")
        if (body.isBlank()) return null
        return runCatching {
            val o = JSONObject("{$body}")
            val reply = o.optString("reply").trim()
            if (reply.isBlank()) return null
            Draft(
                theirLanguage = o.optString("theirLanguage").trim(),
                subtext = o.optString("subtext").trim(),
                risk = o.optString("risk").trim(),
                reply = reply,
                replyGloss = o.optString("replyGloss").trim(),
                note = o.optString("note").trim(),
            )
        }.getOrNull()
    }

    /** 视觉调用 —— 四家 provider，key 由机主自带。阻塞，走 IO 线程。 */
    private fun vision(context: Context, b64: String, prompt: String): String =
        when (Prefs.provider(context)) {
            Prefs.PROVIDER_GEMINI ->
                GeminiClient.visionText(Prefs.keyFor(context, Prefs.PROVIDER_GEMINI), b64, prompt)
            Prefs.PROVIDER_OPENAI -> OpenAIClient.visionText(
                Prefs.keyFor(context, Prefs.PROVIDER_OPENAI), b64, prompt, model = "gpt-5.6-sol",
            )
            Prefs.PROVIDER_OPENROUTER -> OpenAIClient.visionText(
                Prefs.keyFor(context, Prefs.PROVIDER_OPENROUTER), b64, prompt,
                model = "openrouter/free", baseUrl = OpenAIClient.OPENROUTER_BASE,
            )
            // 读潜台词是这个产品的核心，默认给到质量档。
            else -> AnthropicClient.explain(
                Prefs.keyFor(context, Prefs.PROVIDER_ANTHROPIC), b64, prompt,
                model = "claude-sonnet-5",
            )
        }
}
