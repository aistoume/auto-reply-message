import SwiftUI

/**
 对方是谁 —— 挑语气之前先定的一层。

 视觉上刻意比语气条矮一号：它是限定条件，不是主动作。选中才上色，
 未选的保持灰底，避免和下面那排语气抢注意力。
 */
struct RelationRowView: View {
    let relations: [Tone]
    @Binding var selectedId: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("对方是谁")
                .font(.caption)
                .foregroundStyle(.secondary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(relations) { r in
                        let on = r.id == selectedId
                        Button {
                            selectedId = r.id
                            Prefs.relationId = r.id
                        } label: {
                            HStack(spacing: 4) {
                                Text(r.emoji).font(.system(size: 14))
                                Text(r.name).font(.system(size: 13, weight: on ? .semibold : .regular))
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(
                                on ? r.color : Color.secondary.opacity(0.13),
                                in: Capsule()
                            )
                            .foregroundStyle(on ? .white : .primary)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 1)
            }
        }
    }
}
