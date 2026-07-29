import SwiftUI

/**
 情绪轮盘 —— Android 那个扇形轮盘的 iOS 版。

 iOS 没有悬浮球，所以轮盘不围着球展开，而是在拟稿页里铺成一个圆：
 中心是提示，四周是各档位。上次用过的那格带一圈描边。
 */
struct ToneWheelView: View {
    let tones: [Tone]
    let active: Tone?
    let onSelect: (Tone) -> Void

    private let radius: CGFloat = 92
    private let buttonSize: CGFloat = 66

    var body: some View {
        GeometryReader { geo in
            let centre = CGPoint(x: geo.size.width / 2, y: geo.size.height / 2)
            ZStack {
                // 虚线圆 —— 视觉上把各档位串成一个轮盘
                Circle()
                    .strokeBorder(style: StrokeStyle(lineWidth: 1.5, dash: [4, 4]))
                    .foregroundStyle(.quaternary)
                    .frame(width: radius * 2, height: radius * 2)
                    .position(centre)

                ForEach(Array(tones.enumerated()), id: \.element.id) { index, tone in
                    let angle = angleFor(index: index, count: tones.count)
                    let p = CGPoint(
                        x: centre.x + cos(angle) * radius,
                        y: centre.y + sin(angle) * radius
                    )
                    Button { onSelect(tone) } label: {
                        VStack(spacing: 3) {
                            Text(tone.emoji).font(.system(size: 26))
                            Text(tone.name)
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(.white)
                                .lineLimit(1)
                        }
                        .frame(width: buttonSize, height: buttonSize)
                        .background(tone.color, in: Circle())
                        .overlay(
                            Circle().strokeBorder(
                                .white,
                                lineWidth: isHighlighted(tone) ? 3 : 1.5
                            )
                        )
                        .shadow(color: .black.opacity(0.18), radius: 5, y: 3)
                    }
                    .buttonStyle(.plain)
                    .position(p)
                }
            }
        }
    }

    /// 从正上方开始顺时针均分，格数少时也不会挤在一边。
    private func angleFor(index: Int, count: Int) -> CGFloat {
        let step = (2 * CGFloat.pi) / CGFloat(max(count, 1))
        return -CGFloat.pi / 2 + step * CGFloat(index)
    }

    private func isHighlighted(_ tone: Tone) -> Bool {
        if let active { return tone.id == active.id }
        return tone.id == Prefs.lastToneId
    }
}
