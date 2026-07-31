import Foundation
import SwiftUI

/// 一格「带标签的 prompt 片段」—— 语气和关系共用这一个结构。
struct Tone: Identifiable, Codable, Equatable {
    var id: String
    var emoji: String
    var name: String
    /// 十六进制 "#RRGGBB"
    var colorHex: String
    /// 写给模型的说明 —— 用户改这里等于改说话方式
    var guidance: String

    var color: Color { Color(hex: colorHex) }
}

/**
 语气 / 关系的存储模型。

 关键取舍：**内置项不整份落盘**。落盘之后用户切了系统语言，名字还留在
 旧语言里，看着像 app 坏了。所以只存三样东西 —— 用户改过的内置项、
 用户自己加的项、用户删掉的内置项 —— 内置项的文案每次按当前语言现取。
 */
struct CatalogStore {
    let storageKey: String
    let builtins: () -> [Tone]
    let maxItems: Int

    private struct Saved: Codable {
        var overrides: [String: Tone] = [:]   // 改过的内置项
        var customs: [Tone] = []              // 自己加的
        var hidden: [String] = []             // 删掉的内置项
    }

    private func read() -> Saved {
        guard let data = UserDefaults.standard.data(forKey: storageKey),
              let s = try? JSONDecoder().decode(Saved.self, from: data)
        else { return Saved() }
        return s
    }

    private func write(_ s: Saved) {
        guard let data = try? JSONEncoder().encode(s) else { return }
        UserDefaults.standard.set(data, forKey: storageKey)
    }

    func load() -> [Tone] {
        let s = read()
        let hidden = Set(s.hidden)
        var list = builtins()
            .filter { !hidden.contains($0.id) }
            .map { s.overrides[$0.id] ?? $0 }
        list.append(contentsOf: s.customs)
        return list.isEmpty ? builtins() : list
    }

    /// 保存一项：内置的记成 override，自定义的整条存。
    func upsert(_ tone: Tone) {
        var s = read()
        if builtins().contains(where: { $0.id == tone.id }) {
            s.overrides[tone.id] = tone
        } else if let i = s.customs.firstIndex(where: { $0.id == tone.id }) {
            s.customs[i] = tone
        } else {
            s.customs.append(tone)
        }
        s.hidden.removeAll { $0 == tone.id }
        write(s)
    }

    func remove(_ id: String) {
        var s = read()
        s.customs.removeAll { $0.id == id }
        s.overrides[id] = nil
        if builtins().contains(where: { $0.id == id }), !s.hidden.contains(id) {
            s.hidden.append(id)
        }
        write(s)
    }

    func reset() {
        UserDefaults.standard.removeObject(forKey: storageKey)
    }

    func canAdd() -> Bool { load().count < maxItems }
}

enum ToneConfig {
    /// 出厂档位 —— 按「从暖到远」排。
    ///
    /// 早先只有四档，全是防守型的；但日常聊天里要把关系往前推的场合其实
    /// 更多，只给防守档等于逼用户在「客气地拒绝」和「翻脸」之间二选一。
    static var builtins: [Tone] {
        [
            Tone(id: "warm",    emoji: "😊", name: L("tone.warm"),    colorHex: "#F97316", guidance: L("tone.warm.g")),
            Tone(id: "thanks",  emoji: "🙏", name: L("tone.thanks"),  colorHex: "#EAB308", guidance: L("tone.thanks.g")),
            Tone(id: "yes",     emoji: "👍", name: L("tone.yes"),     colorHex: "#22C55E", guidance: L("tone.yes.g")),
            Tone(id: "humor",   emoji: "😄", name: L("tone.humor"),   colorHex: "#06B6D4", guidance: L("tone.humor.g")),
            Tone(id: "comfort", emoji: "🤗", name: L("tone.comfort"), colorHex: "#F472B6", guidance: L("tone.comfort.g")),
            Tone(id: "decent",  emoji: "🙂", name: L("tone.decent"),  colorHex: "#3B82F6", guidance: L("tone.decent.g")),
            Tone(id: "ask",     emoji: "❓", name: L("tone.ask"),     colorHex: "#8B5CF6", guidance: L("tone.ask.g")),
            Tone(id: "needle",  emoji: "🪡", name: L("tone.needle"),  colorHex: "#D97706", guidance: L("tone.needle.g")),
            Tone(id: "table",   emoji: "🔥", name: L("tone.table"),   colorHex: "#DC2626", guidance: L("tone.table.g")),
            Tone(id: "cool",    emoji: "🧊", name: L("tone.cool"),    colorHex: "#64748B", guidance: L("tone.cool.g")),
        ]
    }

    static let store = CatalogStore(
        storageKey: "tones.v3", builtins: { builtins }, maxItems: 14
    )

    static func load() -> [Tone] { store.load() }
}

enum RelationConfig {
    /// 第一项「默认」的 guidance 为空 = 不往 prompt 里加任何东西。
    static var builtins: [Tone] {
        [
            Tone(id: "none",    emoji: "👤", name: L("rel.none"),    colorHex: "#64748B", guidance: ""),
            Tone(id: "family",  emoji: "🏠", name: L("rel.family"),  colorHex: "#EF4444", guidance: L("rel.family.g")),
            Tone(id: "friend",  emoji: "🫂", name: L("rel.friend"),  colorHex: "#10B981", guidance: L("rel.friend.g")),
            Tone(id: "partner", emoji: "❤️", name: L("rel.partner"), colorHex: "#EC4899", guidance: L("rel.partner.g")),
            Tone(id: "client",  emoji: "💼", name: L("rel.client"),  colorHex: "#3B82F6", guidance: L("rel.client.g")),
            Tone(id: "boss",    emoji: "🎩", name: L("rel.boss"),    colorHex: "#A855F7", guidance: L("rel.boss.g")),
        ]
    }

    static let store = CatalogStore(
        storageKey: "relations.v2", builtins: { builtins }, maxItems: 10
    )

    static func load() -> [Tone] { store.load() }
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
