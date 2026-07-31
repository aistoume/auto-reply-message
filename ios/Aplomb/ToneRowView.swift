import SwiftUI

/**
 情绪选择条 —— 横排一列。

 早先这里是个圆形轮盘（照搬 Android 那版）。但轮盘的价值在于「围着悬浮球
 展开、拇指原地就能够到」，而 iOS 没有悬浮窗，轮盘摆进 app 里只是白占掉
 半屏高度，把真正要看的回复挤到屏幕外。所以这里改成横排：占一行，剩下的
 高度全留给回复。
 */
struct ToneRowView: View {
    let tones: [Tone]
    let active: Tone?
    let onSelect: (Tone) -> Void

    private let side: CGFloat = 74

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(tones) { tone in
                    Button { onSelect(tone) } label: {
                        VStack(spacing: 3) {
                            Text(tone.emoji).font(.system(size: 24))
                            Text(tone.name)
                                .font(.system(size: 11, weight: .semibold))
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                        }
                        .foregroundStyle(.white)
                        .frame(width: side, height: 66)
                        .background(tone.color, in: RoundedRectangle(cornerRadius: 16))
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .strokeBorder(.white, lineWidth: highlighted(tone) ? 2.5 : 0)
                        )
                        .shadow(color: .black.opacity(0.12), radius: 3, y: 2)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.vertical, 2)
        }
    }

    private func highlighted(_ tone: Tone) -> Bool {
        if let active { return tone.id == active.id }
        return tone.id == Prefs.lastToneId
    }
}
