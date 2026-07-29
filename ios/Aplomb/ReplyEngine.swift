import Foundation

/**
 嘴替引擎的 iOS 版 —— prompt 与 Android 端逐字对齐（`ReplyEngine.kt`），
 两端出稿必须是同一个东西，否则「同一个产品」这句话就不成立。

 语言分工：回复正文跟对方语言走，分析和建议永远用机主母语。
 */
enum ReplyEngine {

    struct Draft: Equatable {
        /// 对方使用的语言（仅供展示）
        let theirLanguage: String
        /// 潜台词翻译 —— 机主语言
        let subtext: String
        /// 误读风险 —— 机主语言
        let risk: String
        /// 可直接发送的正文 —— 对方语言
        let reply: String
        /// 发送建议 —— 机主语言
        let note: String
    }

    static func prompt(tone: Tone, myLanguage: String, persona: String, extra: String = "") -> String {
        let personaLine = persona.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? ""
            : "\n机主补充的自我设定（影响语气与身份，但不改变上面的分档要求）：\(persona)"
        let extraLine = extra.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? ""
            : "\n机主这次特别交代（优先满足）：\(extra)"

        return """
        你是「嘴替」——替机主回那些不好回的消息。你面前是一张聊天截图，请先看懂，再替机主说话。

        先判断谁是谁：截图里靠右/用主题色的气泡是机主自己发的，靠左的是对方。最新一条通常在底部。

        然后按下面的档位替机主写回复。

        【本次档位】\(tone.name)
        【档位要求】\(tone.guidance)\(personaLine)\(extraLine)

        【语言规则 —— 很重要】
        - 回复正文（reply）必须使用**对方正在使用的语言**。对方发英文就回英文，发日文就回日文，中文就回中文。不要翻译腔，要像母语者在手机上打的字。
        - 其余所有字段（subtext / risk / note）一律用**\(myLanguage)**写给机主看。

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
          "note": "发送建议：这句发出去会怎样、后续可能怎么走、机主要自己确认什么"
        }
        """
    }

    /// 模型偶尔会在 JSON 前后带一句话或裹代码块，所以取第一个 `{` 到最后一个 `}`。
    static func parse(_ raw: String) -> Draft? {
        guard let start = raw.firstIndex(of: "{"),
              let end = raw.lastIndex(of: "}"),
              start < end
        else { return nil }
        let slice = String(raw[start...end])
        guard let data = slice.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return nil }
        let reply = (obj["reply"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !reply.isEmpty else { return nil }
        func str(_ k: String) -> String {
            (obj[k] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return Draft(
            theirLanguage: str("theirLanguage"),
            subtext: str("subtext"),
            risk: str("risk"),
            reply: reply,
            note: str("note")
        )
    }
}
