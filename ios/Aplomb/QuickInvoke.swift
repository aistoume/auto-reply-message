import AppIntents
import SwiftUI
import UIKit

/**
 从聊天界面把嘴替叫起来。

 iOS 不允许第三方画悬浮窗，所以「悬浮球」只能借系统的：辅助触控的圆点、
 轻点背面、或控制中心，它们都能触发一条快捷指令。这个文件提供那条快捷
 指令要调的两个入口：

   • App Intent「拟一稿」—— 可选传入截图 + 语气，出现在快捷指令 / Siri 里
   • URL scheme `aplomb://draft?tone=<id|name>` —— 给手搓 URL 的路子留的门

 两条都汇到 [PendingDraft]，拟稿页看到它就自动跑：省掉「打开 app → 找截图
 → 选语气」这三步，点一下球就直接出稿。
 */

/// 待办的一次拟稿请求 —— 由外部入口写入，拟稿页消费。
@MainActor
final class PendingDraft: ObservableObject {
    static let shared = PendingDraft()

    /// 语气 id 或名称；空表示用上次那档。
    @Published var toneKey: String?
    /// 快捷指令直接递进来的截图；nil 表示去相册取最新那张。
    @Published var image: UIImage?
    /// 自增，保证同一档位连点两次也会各跑一遍。
    @Published var nonce = 0

    func request(toneKey: String?, image: UIImage? = nil) {
        self.toneKey = toneKey
        self.image = image
        nonce += 1
    }

    /**
     把 toneKey 解析成档位。**解析不到就返回 nil，绝不擅自兜底**。

     兜底成「上次那档」看着贴心，实际是拿用户的电池去赌他想要哪个语气 ——
     截完图直接开跑，人还没来得及看一眼对话。所以：快捷指令里明确指定了
     语气才自动跑，没指定就只备好截图，等用户自己挑。
     */
    func resolve(from tones: [Tone]) -> Tone? {
        guard let key = toneKey?.lowercased(), !key.isEmpty else { return nil }
        return tones.first { $0.id.lowercased() == key || $0.name.lowercased() == key }
    }
}

// ── 快捷指令：拟一稿 ────────────────────────────────────────────────

/// 语气下拉 —— 档位是用户自定义的，所以选项得动态从设置里读。
struct ToneOptions: DynamicOptionsProvider {
    func results() async throws -> [String] {
        ToneConfig.load().map(\.name)
    }
}

struct DraftIntent: AppIntent {
    static var title: LocalizedStringResource = "拟一稿"
    static var description = IntentDescription(
        "读一张聊天截图，按选定语气替你写好回复。不传截图就用相册里最新那张。"
    )
    /// 出稿要给用户看，所以必须把 app 带到前台。
    static var openAppWhenRun = true

    @Parameter(title: "语气", optionsProvider: ToneOptions())
    var tone: String?

    @Parameter(title: "聊天截图")
    var screenshot: IntentFile?

    static var parameterSummary: some ParameterSummary {
        Summary("按 \(\.$tone) 拟一稿，截图用 \(\.$screenshot)")
    }

    @MainActor
    func perform() async throws -> some IntentResult {
        var image: UIImage?
        if let file = screenshot {
            image = UIImage(data: file.data)
        }
        PendingDraft.shared.request(toneKey: tone, image: image)
        return .result()
    }
}

struct AplombShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: DraftIntent(),
            phrases: ["用 \(.applicationName) 拟一稿", "\(.applicationName) 帮我回"],
            shortTitle: "拟一稿",
            systemImageName: "text.bubble"
        )
    }
}

// ── URL scheme ─────────────────────────────────────────────────────

enum QuickInvoke {
    /// `aplomb://draft?tone=needle` / `aplomb://draft?tone=绵里藏针`
    @MainActor
    static func handle(_ url: URL) {
        guard url.scheme?.lowercased() == "aplomb" else { return }
        let host = url.host ?? url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard host == "draft" else { return }
        let tone = URLComponents(url: url, resolvingAgainstBaseURL: false)?
            .queryItems?.first { $0.name == "tone" }?.value
        PendingDraft.shared.request(toneKey: tone)
    }
}
