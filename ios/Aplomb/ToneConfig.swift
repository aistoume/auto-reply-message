import Foundation
import SwiftUI

/// 情绪档位 —— 与 Android 端 `ToneConfig.kt` 同构（同样的 id / 文案 / 颜色）。
struct Tone: Identifiable, Codable, Equatable {
    var id: String
    var emoji: String
    var name: String
    /// 十六进制 "#RRGGBB"
    var colorHex: String
    /// 写给模型的档位说明 —— 用户改这里等于改说话方式
    var guidance: String

    var color: Color { Color(hex: colorHex) }
}

enum ToneConfig {
    private static let key = "tones.v1"

    static let defaults: [Tone] = [
        Tone(
            id: "decent", emoji: "🙂", name: "体面", colorHex: "#3B82F6",
            guidance: "保留关系、留有余地，对方容易接受。把事情往前推，不追究、不上价值，但也不白白答应对方没说清的要求。"
        ),
        Tone(
            id: "needle", emoji: "🪡", name: "绵里藏针", colorHex: "#D97706",
            guidance: "把问题摆到明面上，守住底线，既不硬怼也不妥协。用具体的事实、数字、时间点说话，让对方自己意识到成本，语气始终客气。"
        ),
        Tone(
            id: "table", emoji: "🔥", name: "掀桌", colorHex: "#DC2626",
            guidance: "把规则讲清楚、不再退让，接受关系可能就此结束。仍然只讲事实和后果，不带脏字、不人身攻击、不威胁。"
        ),
        Tone(
            id: "cool", emoji: "🧊", name: "冷处理", colorHex: "#64748B",
            guidance: "先不表态、不接话茬、把决定权留在自己手里。简短、平静、不解释、不承诺，给自己留出时间。"
        ),
    ]

    /// 轮盘一屏最多放 6 格。
    static let maxTones = 6

    static func load() -> [Tone] {
        guard let data = UserDefaults.standard.data(forKey: key),
              let tones = try? JSONDecoder().decode([Tone].self, from: data),
              !tones.isEmpty
        else { return defaults }
        return tones
    }

    static func save(_ tones: [Tone]) {
        guard let data = try? JSONEncoder().encode(tones) else { return }
        UserDefaults.standard.set(data, forKey: key)
    }

    static func reset() {
        UserDefaults.standard.removeObject(forKey: key)
    }
}

extension Color {
    init(hex: String) {
        var s = hex.trimmingCharacters(in: .whitespaces)
        if s.hasPrefix("#") { s.removeFirst() }
        let v = UInt64(s, radix: 16) ?? 0x3B82F6
        self.init(
            .sRGB,
            red: Double((v >> 16) & 0xFF) / 255,
            green: Double((v >> 8) & 0xFF) / 255,
            blue: Double(v & 0xFF) / 255
        )
    }
}


/**
 对方是谁 —— 在挑语气之前先定的那一层。

 同一句「掀桌」，对老板和对恋人根本是两回事：一个要顾权力差，一个要顾
 情绪分量。关系不定，语气就是空转。

 结构上直接复用 [Tone]（本质都是「带标签的 prompt 片段」），只是存在
 另一个 key 下，编辑器也共用一套。
 */
enum RelationConfig {
    private static let key = "relations.v1"

    /// 第一项是「没有特殊关系」—— guidance 为空表示不往 prompt 里加任何东西。
    static let defaults: [Tone] = [
        Tone(id: "none", emoji: "👤", name: "默认", colorHex: "#64748B", guidance: ""),
        Tone(
            id: "family", emoji: "🏠", name: "家人", colorHex: "#EF4444",
            guidance: "血缘关系断不掉，话说重了要长期承受。可以直接、可以划界限，但别翻旧账、别下人格定论；把这一件事说清楚就够，保住关系比赢这一局重要。"
        ),
        Tone(
            id: "friend", emoji: "🫂", name: "朋友", colorHex: "#10B981",
            guidance: "平等关系，没有上下级。可以坦率，允许一点玩笑和自嘲；但别拿「关系好」当借口替对方做决定或替自己越界。"
        ),
        Tone(
            id: "partner", emoji: "❤️", name: "恋人", colorHex: "#EC4899",
            guidance: "情绪的分量大于道理。先接住对方的感受，再讲事情本身；不翻旧账、不做人身评判、不用冷暴力和最后通牒。"
        ),
        Tone(
            id: "client", emoji: "💼", name: "客户", colorHex: "#3B82F6",
            guidance: "有明确的商业边界。守住范围、节点、价钱三件事；客气但不白让，该书面确认的说清楚，别让口头承诺变成默认义务。"
        ),
        Tone(
            id: "boss", emoji: "🎩", name: "老板", colorHex: "#A855F7",
            guidance: "存在权力差，硬顶没好处。不顶撞，但也别默认接受没说清的要求；把成本、排期、优先级摆到台面上，让对方在几个选项里选，而不是你单方面吞下。"
        ),
    ]

    static let maxRelations = 8

    static func load() -> [Tone] {
        guard let data = UserDefaults.standard.data(forKey: key),
              let list = try? JSONDecoder().decode([Tone].self, from: data),
              !list.isEmpty
        else { return defaults }
        return list
    }

    static func save(_ list: [Tone]) {
        guard let data = try? JSONEncoder().encode(list) else { return }
        UserDefaults.standard.set(data, forKey: key)
    }

    static func reset() { UserDefaults.standard.removeObject(forKey: key) }
}
