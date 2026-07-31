import SwiftUI

/**
 语气页 —— 「对方是谁」和「用什么语气」的配置都在这里。

 从设置里单拆出来是因为它俩是**内容**不是**配置**：用户会反复回来调
 措辞、加自己的场景（前任、房东、甲方对接人），而 API key、隐私那些
 设一次就再也不动。混在一起会让真正常用的东西沉到列表底部。
 */
struct ToneSettingsView: View {
    @State private var relations = RelationConfig.load()
    @State private var tones = ToneConfig.load()
    @State private var editing: Tone?
    @State private var editingIsRelation = false
    @State private var adding = false
    @State private var addingIsRelation = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    ForEach(relations) { r in
                        Button {
                            editingIsRelation = true
                            editing = r
                        } label: { row(r, fallback: L("tones.none_guidance")) }
                    }
                    if RelationConfig.store.canAdd() {
                        Button {
                            addingIsRelation = true
                            adding = true
                        } label: {
                            Label(L("tones.add_relation"), systemImage: "plus.circle")
                        }
                    }
                    Button(role: .destructive) {
                        RelationConfig.store.reset()
                        relations = RelationConfig.load()
                    } label: { Text(L("tones.reset_relation")) }
                } header: {
                    Text(L("tones.relations"))
                } footer: {
                    Text(L("tones.relations.help"))
                }

                Section {
                    ForEach(tones) { t in
                        Button {
                            editingIsRelation = false
                            editing = t
                        } label: { row(t, fallback: "") }
                    }
                    if ToneConfig.store.canAdd() {
                        Button {
                            addingIsRelation = false
                            adding = true
                        } label: {
                            Label(L("tones.add_tone"), systemImage: "plus.circle")
                        }
                    }
                    Button(role: .destructive) {
                        ToneConfig.store.reset()
                        tones = ToneConfig.load()
                    } label: { Text(L("tones.reset_tone")) }
                } header: {
                    Text(L("tones.tones"))
                } footer: {
                    Text(L("tones.tones.help"))
                }
            }
            .navigationTitle(L("tones.title"))
            .sheet(item: $editing) { item in
                ToneEditor(tone: item, isRelation: editingIsRelation) { updated in
                    let store = editingIsRelation ? RelationConfig.store : ToneConfig.store
                    if let updated { store.upsert(updated) } else { store.remove(item.id) }
                    reload()
                }
            }
            .sheet(isPresented: $adding) {
                ToneEditor(tone: nil, isRelation: addingIsRelation) { created in
                    if let created {
                        (addingIsRelation ? RelationConfig.store : ToneConfig.store).upsert(created)
                        reload()
                    }
                }
            }
        }
    }

    private func reload() {
        relations = RelationConfig.load()
        tones = ToneConfig.load()
    }

    private func row(_ t: Tone, fallback: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Text(t.emoji).font(.title3)
            VStack(alignment: .leading, spacing: 2) {
                Text(t.name).foregroundStyle(.primary)
                Text(t.guidance.isEmpty ? fallback : t.guidance)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
        }
    }
}

/// 编辑一格 —— 语气和关系共用。传回 nil 表示删除。
struct ToneEditor: View {
    let tone: Tone?
    let isRelation: Bool
    let onDone: (Tone?) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var emoji: String
    @State private var name: String
    @State private var guidance: String
    @State private var colorHex: String

    private let palette = [
        "#F97316", "#EAB308", "#22C55E", "#06B6D4", "#F472B6",
        "#3B82F6", "#8B5CF6", "#D97706", "#DC2626", "#64748B",
    ]

    init(tone: Tone?, isRelation: Bool, onDone: @escaping (Tone?) -> Void) {
        self.tone = tone
        self.isRelation = isRelation
        self.onDone = onDone
        _emoji = State(initialValue: tone?.emoji ?? "💬")
        _name = State(initialValue: tone?.name ?? "")
        _guidance = State(initialValue: tone?.guidance ?? "")
        _colorHex = State(initialValue: tone?.colorHex ?? "#3B82F6")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section(L("tone.section_icon")) {
                    TextField(L("tone.emoji_ph"), text: $emoji)
                    TextField(L("tone.name_ph"), text: $name)
                }
                Section(L("tone.section_color")) {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 40))], spacing: 10) {
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
                    .padding(.vertical, 4)
                }
                Section(L("tone.section_guidance")) {
                    TextField(L("tone.guidance_ph"), text: $guidance, axis: .vertical)
                        .lineLimit(3...10)
                }
                if tone != nil {
                    Section {
                        Button(role: .destructive) {
                            onDone(nil)
                            dismiss()
                        } label: { Text(L("tone.delete")) }
                    }
                }
            }
            .navigationTitle(tone == nil ? L("tone.add") : L("tone.edit"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L("common.cancel")) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("common.save")) {
                        let n = name.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !n.isEmpty else { return }
                        onDone(Tone(
                            id: tone?.id ?? "custom-\(UUID().uuidString.prefix(8))",
                            emoji: emoji.isEmpty ? "💬" : emoji,
                            name: n,
                            colorHex: colorHex,
                            // 关系可以只有名字没设定（比如「默认」）；语气必须有
                            guidance: guidance.trimmingCharacters(in: .whitespacesAndNewlines)
                        ))
                        dismiss()
                    }
                }
            }
        }
    }
}
