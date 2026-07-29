import Foundation

/**
 主 app 与键盘扩展之间的交接台。

 iOS 的键盘扩展读不了相册、也发不了带图的请求（它拿不到用户刚截的那张图），
 所以分工是：**app 负责出稿，键盘只负责把稿子送进输入框**。app 每出一稿就
 往 App Group 里写一条，用户切到嘴替键盘时，稿子已经在那儿等着了。

 注意：键盘扩展要读到 App Group，必须由用户打开「完全访问」。没打开时
 键盘会显示引导文案而不是空白 —— 空白会被当成 app 坏了。
 */
public enum SharedDrafts {
    public static let appGroup = "group.solutions.aicon.aplomb"
    private static let key = "drafts.v1"
    /// 键盘一屏能翻的条数；再多用户也不会往下找。
    private static let maxKept = 8

    public struct Item: Codable, Identifiable, Equatable {
        public let id: String
        /// 档位 emoji + 名称，让用户在键盘上一眼认出是哪一档
        public let toneEmoji: String
        public let toneName: String
        /// 可直接发送的正文
        public let text: String
        public let createdAt: Date

        public init(id: String = UUID().uuidString,
                    toneEmoji: String,
                    toneName: String,
                    text: String,
                    createdAt: Date = Date()) {
            self.id = id
            self.toneEmoji = toneEmoji
            self.toneName = toneName
            self.text = text
            self.createdAt = createdAt
        }
    }

    private static var store: UserDefaults? { UserDefaults(suiteName: appGroup) }

    /// nil 表示读不到共享容器 —— 键盘那边多半是没开「完全访问」。
    public static func load() -> [Item]? {
        guard let store else { return nil }
        guard let data = store.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([Item].self, from: data)) ?? []
    }

    /// 新稿置顶；同一档位的旧稿会被顶掉，避免键盘上出现一串近乎相同的候选。
    public static func push(_ item: Item) {
        guard let store else { return }
        var items = load() ?? []
        items.removeAll { $0.toneName == item.toneName && $0.text == item.text }
        items.insert(item, at: 0)
        if items.count > maxKept { items = Array(items.prefix(maxKept)) }
        if let data = try? JSONEncoder().encode(items) {
            store.set(data, forKey: key)
        }
    }

    public static func clear() {
        store?.removeObject(forKey: key)
    }
}
