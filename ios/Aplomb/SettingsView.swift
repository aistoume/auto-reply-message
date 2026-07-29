import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var battery: BatteryClient
    @State private var myLanguage = Prefs.myLanguage
    @State private var persona = Prefs.persona
    @State private var apiKey = Prefs.apiKey
    @State private var tones = ToneConfig.load()
    @State private var editing: Tone?
    @State private var adding = false

    var body: some View {
        NavigationStack {
            Form {
                Section("免费电池") {
                    BatteryBar()
                        .listRowInsets(EdgeInsets(top: 8, leading: 8, bottom: 8, trailing: 8))
                    Text("一次拟稿用一格电。用完可以填自己的 API key 继续，续电内购在下一版。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Section("我的语言") {
                    TextField("中文 / English / 日本語…", text: $myLanguage)
                        .onChange(of: myLanguage) { _, v in Prefs.myLanguage = v }
                    Text("潜台词和建议永远用这个语言写给你看；回复正文自动跟对方说的语言走。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Section("我是谁") {
                    TextField("例：自由职业设计师，说话简短偏冷，不接受无偿改稿", text: $persona, axis: .vertical)
                        .lineLimit(2...4)
                        .onChange(of: persona) { _, v in Prefs.persona = v }
                    Text("你的身份、说话习惯、底线。会影响所有档位的语气。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Section("情绪档位") {
                    ForEach(tones) { tone in
                        Button { editing = tone } label: {
                            HStack(alignment: .top, spacing: 12) {
                                Text(tone.emoji).font(.title2)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(tone.name).foregroundStyle(.primary)
                                    Text(tone.guidance)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                        .lineLimit(2)
                                }
                            }
                        }
                    }
                    if tones.count < ToneConfig.maxTones {
                        Button { adding = true } label: {
                            Label("添加档位", systemImage: "plus.circle")
                        }
                    }
                    Button(role: .destructive) {
                        ToneConfig.reset()
                        tones = ToneConfig.load()
                    } label: {
                        Text("恢复默认档位")
                    }
                }

                Section("自带 API key（可选）") {
                    SecureField("sk-ant-…", text: $apiKey)
                        .onChange(of: apiKey) { _, v in Prefs.apiKey = v }
                    Text("填了就绕过免费电池，直连你自己的 Anthropic 账户。留空就用电池。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Section {
                    Text("没有服务器保存你的聊天内容。截图只在你点拟稿的那一刻上传做识别，不留存。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("设置")
            .sheet(item: $editing) { tone in
                ToneEditor(tone: tone) { updated in
                    if let updated {
                        tones = tones.map { $0.id == updated.id ? updated : $0 }
                    } else {
                        tones = tones.filter { $0.id != tone.id }
                        if tones.isEmpty { tones = ToneConfig.defaults }
                    }
                    ToneConfig.save(tones)
                }
            }
            .sheet(isPresented: $adding) {
                ToneEditor(tone: nil) { created in
                    if let created {
                        tones.append(created)
                        ToneConfig.save(tones)
                    }
                }
            }
            .task { await battery.refresh() }
        }
    }
}

/// 档位编辑 —— 改 emoji / 名称 / 颜色 / 说话方式。传回 nil 表示删除。
struct ToneEditor: View {
    let tone: Tone?
    let onDone: (Tone?) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var emoji: String
    @State private var name: String
    @State private var guidance: String
    @State private var colorHex: String

    private let palette = ["#3B82F6", "#D97706", "#DC2626", "#10B981", "#A855F7", "#64748B"]

    init(tone: Tone?, onDone: @escaping (Tone?) -> Void) {
        self.tone = tone
        self.onDone = onDone
        _emoji = State(initialValue: tone?.emoji ?? "💬")
        _name = State(initialValue: tone?.name ?? "")
        _guidance = State(initialValue: tone?.guidance ?? "")
        _colorHex = State(initialValue: tone?.colorHex ?? "#3B82F6")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("图标与名称") {
                    TextField("emoji", text: $emoji)
                    TextField("档位名称，如「体面」", text: $name)
                }
                Section("颜色") {
                    HStack(spacing: 12) {
                        ForEach(palette, id: \.self) { hex in
                            Circle()
                                .fill(Color(hex: hex))
                                .frame(width: 32, height: 32)
                                .overlay(
                                    Circle().strokeBorder(.primary, lineWidth: hex == colorHex ? 3 : 0)
                                )
                                .onTapGesture { colorHex = hex }
                        }
                    }
                }
                Section("这一档怎么说话") {
                    TextField("写给 AI 看的说明", text: $guidance, axis: .vertical)
                        .lineLimit(3...8)
                }
                if tone != nil {
                    Section {
                        Button(role: .destructive) {
                            onDone(nil)
                            dismiss()
                        } label: {
                            Text("删除这一档")
                        }
                    }
                }
            }
            .navigationTitle(tone == nil ? "添加档位" : "修改档位")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
                        let trimmedGuidance = guidance.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !trimmedName.isEmpty, !trimmedGuidance.isEmpty else { return }
                        onDone(Tone(
                            id: tone?.id ?? UUID().uuidString,
                            emoji: emoji.isEmpty ? "💬" : emoji,
                            name: trimmedName,
                            colorHex: colorHex,
                            guidance: trimmedGuidance
                        ))
                        dismiss()
                    }
                }
            }
        }
    }
}
