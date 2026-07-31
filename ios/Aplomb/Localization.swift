import Foundation

/// 取本地化文案。key 用英文短语，缺条目时回落到 key 本身，不会出现空白 UI。
func L(_ key: String) -> String {
    NSLocalizedString(key, comment: "")
}

/// 带参数的版本。
func L(_ key: String, _ args: CVarArg...) -> String {
    String(format: NSLocalizedString(key, comment: ""), arguments: args)
}
