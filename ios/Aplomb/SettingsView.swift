import SwiftUI

/**
 设置页 —— 分成「关于我」和「系统」两块。

 分开的理由：上面那块是**关于用户这个人**的（说什么语言、是什么身份），
 直接决定出稿的样子，用户会回来改；下面那块是**关于这个 app 怎么跑**的
 （电池、密钥、隐私），设一次就不动了。混在一列里，真正影响效果的两项
 会淹没在配置项里。
 */
struct SettingsView: View {
    @EnvironmentObject private var battery: BatteryClient
    @State private var myLanguage = Prefs.myLanguage
    @State private var persona = Prefs.persona
    @State private var apiKey = Prefs.apiKey
    @State private var paywall = false
    @ObservedObject private var lang = AppLanguage.shared

    var body: some View {
        NavigationStack {
            Form {
                // ── 关于我：直接影响出稿 ──
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(L("settings.my_language")).font(.caption).foregroundStyle(.secondary)
                        TextField(L("settings.my_language.ph"), text: $myLanguage)
                            .onChange(of: myLanguage) { _, v in Prefs.myLanguage = v }
                    }
                    Text(L("settings.my_language.help"))
                        .font(.caption).foregroundStyle(.secondary)

                    VStack(alignment: .leading, spacing: 4) {
                        Text(L("settings.persona")).font(.caption).foregroundStyle(.secondary)
                        TextField(L("settings.persona.ph"), text: $persona, axis: .vertical)
                            .lineLimit(2...5)
                            .onChange(of: persona) { _, v in Prefs.persona = v }
                    }
                    Text(L("settings.persona.help"))
                        .font(.caption).foregroundStyle(.secondary)
                } header: {
                    Text(L("settings.me"))
                }

                // ── 系统：设一次就不动 ──
                Section {
                    BatteryBar()
                        .listRowInsets(EdgeInsets(top: 8, leading: 8, bottom: 8, trailing: 8))
                    Text(L("settings.battery.help"))
                        .font(.caption).foregroundStyle(.secondary)
                } header: {
                    Text(L("settings.battery"))
                }

                Section {
                    SecureField("sk-ant-…", text: $apiKey)
                        .onChange(of: apiKey) { _, v in Prefs.apiKey = v }
                    Text(L("settings.key.help"))
                        .font(.caption).foregroundStyle(.secondary)
                } header: {
                    Text(L("settings.key"))
                }

                Section {
                    Picker(L("settings.app_language"), selection: Binding(
                        get: { lang.choice },
                        set: { lang.set($0) }
                    )) {
                        ForEach(AppLanguage.Choice.allCases) { c in
                            Text(c.label).tag(c)
                        }
                    }
                    .pickerStyle(.segmented)
                    Text(L("settings.app_language.help2"))
                        .font(.caption).foregroundStyle(.secondary)
                } header: {
                    Text(L("settings.app_language"))
                }

                Section {
                    Text(L("settings.privacy.help"))
                        .font(.caption).foregroundStyle(.secondary)
                } header: {
                    Text(L("settings.privacy"))
                }
            }
            .navigationTitle(L("settings.title"))
            .task { await battery.refresh() }
        }
    }
}
