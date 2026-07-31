import SwiftUI

/**
 底部三格：语气 · 拟稿 · 设置。

 用自绘的 tab bar 而不是系统 TabView —— 系统的每格一样大，没法把主入口
 单独放大。而这个 app 十次里有九次是来拟稿的，主入口就该比另外两个显眼，
 拇指不用瞄准。
 */
struct RootView: View {
    enum Tab: Int { case tones, draft, settings }
    @State private var tab: Tab = .draft
    /// 订阅语言变化 —— 切了语言这里一刷新，下面所有 L() 都重取
    @ObservedObject private var lang = AppLanguage.shared

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                ToneSettingsView().opacity(tab == .tones ? 1 : 0)
                DraftView().opacity(tab == .draft ? 1 : 0)
                SettingsView().opacity(tab == .settings ? 1 : 0)
            }

            Divider()
            HStack(alignment: .bottom, spacing: 0) {
                item(.tones, "face.smiling", L("tab.tones"))
                item(.draft, "text.bubble.fill", L("tab.draft"))
                item(.settings, "gearshape", L("tab.settings"))
            }
            .padding(.top, 6)
            .background(.bar)
        }
        .id(lang.choice)   // 换语言即重建视图树，避免个别文案没刷新
    }

    private func item(_ t: Tab, _ icon: String, _ title: String) -> some View {
        let on = tab == t
        let main = t == .draft
        return Button {
            withAnimation(.spring(duration: 0.22)) { tab = t }
        } label: {
            VStack(spacing: 3) {
                Image(systemName: icon)
                    .font(.system(size: main ? 26 : 19, weight: main ? .semibold : .regular))
                    .frame(height: 30)
                Text(title)
                    .font(.system(size: main ? 12 : 11, weight: main ? .semibold : .regular))
            }
            .foregroundStyle(on ? Color.orange : Color.secondary)
            .frame(maxWidth: .infinity)
            .padding(.bottom, 2)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
