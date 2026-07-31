import SwiftUI

/**
 回复卡 —— 潜台词在上、正文在中、建议在下，底部一条情绪带随时换档重出。

 iOS 不能替用户往别的 app 的输入框里写字（那是键盘扩展的活，下一版做），
 所以这一版的落点是「复制」：点一下，回到聊天长按粘贴。
 */
struct ReplyCardView: View {
    let draft: ReplyEngine.Draft
    let tone: Tone?
    let tones: [Tone]
    let onTone: (Tone) -> Void

    @State private var copied = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // 头：档位 + 对方语言
            HStack {
                if let tone {
                    Text("\(tone.emoji) \(tone.name)")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(tone.color)
                }
                Spacer()
                if !draft.theirLanguage.isEmpty {
                    Label(draft.theirLanguage, systemImage: "globe")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            if !draft.subtext.isEmpty {
                Text("潜台词：\(draft.subtext)")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            if !draft.risk.isEmpty, draft.risk != "无" {
                Label(draft.risk, systemImage: "exclamationmark.triangle")
                    .font(.caption)
                    .foregroundStyle(.orange)
            }

            // 正文 —— 卡片主体
            Text(draft.reply)
                .font(.body)
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(.quaternary, in: RoundedRectangle(cornerRadius: 12))

            // 回译:正文若不是机主的语言,发出去前得知道自己在说什么
            if !draft.replyGloss.isEmpty {
                Text(draft.replyGloss)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 2)
            }

            if !draft.note.isEmpty {
                Text("建议：\(draft.note)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Button {
                UIPasteboard.general.string = draft.reply
                copied = true
                Task {
                    try? await Task.sleep(for: .seconds(2))
                    copied = false
                }
            } label: {
                Label(copied ? "已复制，回聊天里粘贴" : "复制正文", systemImage: copied ? "checkmark" : "doc.on.doc")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(copied ? .green : .orange)

            // 换档重出 —— 不用回轮盘重截图
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(tones) { t in
                        Button { onTone(t) } label: {
                            Text("\(t.emoji) \(t.name)")
                                .font(.caption.weight(.medium))
                                .padding(.horizontal, 14)
                                .padding(.vertical, 7)
                                .background(
                                    t.id == tone?.id ? t.color : Color.secondary.opacity(0.15),
                                    in: Capsule()
                                )
                                .foregroundStyle(t.id == tone?.id ? .white : .primary)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(14)
        .background(.background.secondary, in: RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16).strokeBorder(.quaternary)
        )
    }
}
