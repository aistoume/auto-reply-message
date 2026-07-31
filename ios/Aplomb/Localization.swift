import Foundation
import SwiftUI

/**
 界面语言 —— 可以在 app 里直接切，不用重启。

 iOS 原生的做法是把用户丢去「系统设置 › Aplomb › 语言」，跳出去一趟才
 能改。这里改成自己管：把选中语言的 .lproj 单独加载成一个 bundle，L()
 从它取词；切换时 objectWillChange 一发，整棵视图树重画，立刻变。
 */
@MainActor
final class AppLanguage: ObservableObject {
    static let shared = AppLanguage()

    enum Choice: String, CaseIterable, Identifiable {
        case system, zh, en
        var id: String { rawValue }
        /// 这些名字故意不本地化 —— 选语言的列表要用各自的语言写，
        /// 否则界面已经是看不懂的语言时，用户找不到自己那一项。
        var label: String {
            switch self {
            case .system: return L("lang.system")
            case .zh: return "中文"
            case .en: return "English"
            }
        }
        var lproj: String? {
            switch self {
            case .system: return nil
            case .zh: return "zh-Hans"
            case .en: return "en"
            }
        }
    }

    @Published private(set) var choice: Choice {
        didSet { Localizer.use(choice.lproj) }
    }

    private init() {
        let saved = UserDefaults.standard.string(forKey: "appLanguage") ?? Choice.system.rawValue
        choice = Choice(rawValue: saved) ?? .system
        Localizer.use(choice.lproj)
    }

    func set(_ c: Choice) {
        UserDefaults.standard.set(c.rawValue, forKey: "appLanguage")
        choice = c
    }
}

/// 当前语言包。nil = 跟随系统，走主 bundle 的常规查找。
private enum Localizer {
    nonisolated(unsafe) static var bundle: Bundle?

    static func use(_ lproj: String?) {
        guard let lproj,
              let path = Bundle.main.path(forResource: lproj, ofType: "lproj"),
              let b = Bundle(path: path)
        else { bundle = nil; return }
        bundle = b
    }
}

/// 取本地化文案。缺条目时回落到 key 本身，不会出现空白 UI。
func L(_ key: String) -> String {
    (Localizer.bundle ?? Bundle.main).localizedString(forKey: key, value: key, table: nil)
}

/// 带参数的版本。
func L(_ key: String, _ args: CVarArg...) -> String {
    String(format: L(key), arguments: args)
}
