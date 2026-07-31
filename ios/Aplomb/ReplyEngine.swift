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
        /// 正文的回译 —— 机主语言。两者同语言时为空。
        /// 存在的理由：正文可能是机主读不顺的语言，发出去前得知道自己在说什么。
        let replyGloss: String
        /// 发送建议 —— 机主语言
        let note: String
    }

    static func prompt(
        tone: Tone, relation: Tone?, myLanguage: String, persona: String, extra: String = ""
    ) -> String {
        // 关系决定「这句话说出去要承担什么」，所以放在档位之前交代
        let relationLine = (relation?.guidance ?? "").isEmpty
            ? ""
            : "\n\n【对方是谁】\(relation!.name)\n【这层关系怎么说话】\(relation!.guidance)"
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
        【档位要求】\(tone.guidance)\(relationLine)\(personaLine)\(extraLine)

        【第一步：先认语言 —— 别跳过】
        看清楚这段对话在用什么语言。可能是中文、英文、日文…也可能是**混着用**（中英夹杂在很多人的聊天里是常态）。
        判断依据按优先级：① 对方最后一条消息的语言；② 整段对话里对方用得最多的语言。
        如果对方本来就混着用，你**也照着混**——别强行统一成一种，那不像同一个人在说话。

        【语言规则】
        - reply（正文）：用你上面认出来的语言写，包括混用的比例。像母语者在手机上打的字，不要翻译腔。
        - replyGloss（回译）：把 reply 完整翻成 \(myLanguage)，让机主知道自己正要发出去的是什么。如果 reply 本身就是 \(myLanguage)，这里留空字符串。
        - theirLanguage：如实写出你认出的语言（混用就写「中英夹杂」这样）。
        - subtext / risk / note：一律用 \(myLanguage) 写给机主看。

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
            replyGloss: str("replyGloss"),
            note: str("note")
        )
    }
}
