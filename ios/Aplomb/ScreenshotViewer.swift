import SwiftUI

/**
 全屏看截图 —— 缩略图只够认出「是这段对话」，要核对细节得能放大。

 双指缩放、拖动平移、双击在 1× 和 2.5× 之间切换。松手时如果缩得比原始
 大小还小，会弹回 1×：否则用户很容易把图缩成一个点，然后不知道怎么找回来。
 */
struct ScreenshotViewer: View {
    let image: UIImage
    @Environment(\.dismiss) private var dismiss

    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero

    private let maxScale: CGFloat = 6

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            Image(uiImage: image)
                .resizable()
                .scaledToFit()
                .scaleEffect(scale)
                .offset(offset)
                .gesture(
                    SimultaneousGesture(
                        MagnificationGesture()
                            .onChanged { value in
                                scale = min(max(lastScale * value, 0.5), maxScale)
                            }
                            .onEnded { _ in
                                if scale < 1 { withAnimation(.spring) { reset() } }
                                lastScale = scale
                            },
                        DragGesture()
                            .onChanged { value in
                                // 没放大时不给拖 —— 否则图会莫名其妙飘走
                                guard scale > 1 else { return }
                                offset = CGSize(
                                    width: lastOffset.width + value.translation.width,
                                    height: lastOffset.height + value.translation.height
                                )
                            }
                            .onEnded { _ in lastOffset = offset }
                    )
                )
                .onTapGesture(count: 2) {
                    withAnimation(.spring) {
                        if scale > 1 { reset() } else { scale = 2.5; lastScale = 2.5 }
                    }
                }

            VStack {
                HStack {
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(12)
                            .background(.black.opacity(0.45), in: Circle())
                    }
                    .padding(.trailing, 16)
                    .padding(.top, 8)
                }
                Spacer()
                Text("双指缩放 · 双击放大 · 拖动查看")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.55))
                    .padding(.bottom, 24)
            }
        }
        .statusBarHidden()
    }

    private func reset() {
        scale = 1; lastScale = 1
        offset = .zero; lastOffset = .zero
    }
}
