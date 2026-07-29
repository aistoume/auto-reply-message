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
