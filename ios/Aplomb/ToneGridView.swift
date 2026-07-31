import SwiftUI

/**
 情绪档位 —— 自动换行的多排网格。

 演进过一轮半：最早是圆形轮盘（照搬 Android 悬浮球那版），iOS 没有悬浮窗
 所以改成横排一条；档位扩到 10 档后横排又变成「还得往右滑才看得全」。
 现在铺成网格，跟着页面一起上下滚 —— 选项一眼看全，不用横向找。
 */
struct ToneGridView: View {
    let tones: [Tone]
    let active: Tone?
    let onSelect: (Tone) -> Void

    private let columns = [GridItem(.adaptive(minimum: 78), spacing: 10)]

    var body: some View {
        LazyVGrid(columns: columns, spacing: 10) {
            ForEach(tones) { tone in
                Button { onSelect(tone) } label: {
                    VStack(spacing: 3) {
                        Text(tone.emoji).font(.system(size: 23))
                        Text(tone.name)
                            .font(.system(size: 11, weight: .semibold))
                            .lineLimit(1)
                            .minimumScaleFactor(0.75)
                    }
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 64)
                    .background(tone.color, in: RoundedRectangle(cornerRadius: 14))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .strokeBorder(.white, lineWidth: highlighted(tone) ? 2.5 : 0)
                    )
                    .shadow(color: .black.opacity(0.1), radius: 3, y: 2)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func highlighted(_ tone: Tone) -> Bool {
        if let active { return tone.id == active.id }
        return tone.id == Prefs.lastToneId
    }
}

/**
 对方是谁 —— 同样铺成网格。

 刻意比语气格子矮一号、字号小一号：它是限定条件，不是主动作，不该跟
 下面那片语气抢注意力。
 */
struct RelationGridView: View {
    let relations: [Tone]
    @Binding var selectedId: String

    private let columns = [GridItem(.adaptive(minimum: 88), spacing: 8)]

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("对方是谁")
                .font(.caption)
                .foregroundStyle(.secondary)
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(relations) { r in
                    let on = r.id == selectedId
                    Button {
                        selectedId = r.id
                        Prefs.relationId = r.id
                    } label: {
                        HStack(spacing: 4) {
                            Text(r.emoji).font(.system(size: 13))
                            Text(r.name)
                                .font(.system(size: 13, weight: on ? .semibold : .regular))
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(
                            on ? r.color : Color.secondary.opacity(0.13),
                            in: Capsule()
                        )
                        .foregroundStyle(on ? .white : .primary)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}
