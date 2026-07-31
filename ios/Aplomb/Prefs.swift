import Foundation

/// 机主的设置 —— 全部只存在本机。
enum Prefs {
    private static let d = UserDefaults.standard

    /// 机主自己的语言：分析和建议永远用它写。空 = 跟随系统。
    static var myLanguage: String {
        get {
            let saved = d.string(forKey: "myLanguage") ?? ""
            if !saved.isEmpty { return saved }
            return Locale.current.language.languageCode?.identifier == "zh" ? "中文" : "English"
        }
        set { d.set(newValue, forKey: "myLanguage") }
    }

    /// 机主的自我设定：身份、口头禅、底线 —— 影响所有档位的语气。
    static var persona: String {
        get { d.string(forKey: "persona") ?? "" }
        set { d.set(newValue, forKey: "persona") }
    }

    /// 自带的 Anthropic key。填了就绕过免费电池，直连自己的账户。
    static var apiKey: String {
        get { d.string(forKey: "apiKey") ?? "" }
        set { d.set(newValue.trimmingCharacters(in: .whitespacesAndNewlines), forKey: "apiKey") }
    }

    /// 上次选的关系；默认「没有特殊关系」。
    static var relationId: String {
        get { d.string(forKey: "relationId") ?? "none" }
        set { d.set(newValue, forKey: "relationId") }
    }

    static var lastToneId: String {
        get { d.string(forKey: "lastTone") ?? "" }
        set { d.set(newValue, forKey: "lastTone") }
    }
}

/// 自带 key 时走的直连路径 —— 和免费电池二选一，出稿格式完全一样。
enum AnthropicClient {
    struct Failure: Error { let message: String }

    static func vision(key: String, imageBase64: String, prompt: String) async throws -> String {
        var req = URLRequest(url: URL(string: "https://api.anthropic.com/v1/messages")!)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "content-type")
        req.setValue(key, forHTTPHeaderField: "x-api-key")
        req.setValue("2023-06-01", forHTTPHeaderField: "anthropic-version")
        req.timeoutInterval = 60
        let body: [String: Any] = [
            "model": "claude-sonnet-5",
            "max_tokens": 700,
            "messages": [[
                "role": "user",
                "content": [
                    ["type": "image",
                     "source": ["type": "base64", "media_type": "image/jpeg", "data": imageBase64]],
                    ["type": "text", "text": prompt],
                ],
            ]],
        ]
        req.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, resp) = try await URLSession.shared.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard code == 200 else {
            let text = String(data: data, encoding: .utf8) ?? ""
            throw Failure(message: "Anthropic \(code): \(text.prefix(160))")
        }
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let content = obj["content"] as? [[String: Any]]
        else { throw Failure(message: "读不懂返回内容") }
        return content
            .filter { $0["type"] as? String == "text" }
            .compactMap { $0["text"] as? String }
            .joined()
    }
}
