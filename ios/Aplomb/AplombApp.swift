import SwiftUI
import Photos
import PhotosUI

@main
struct AplombApp: App {
    @StateObject private var battery = BatteryClient()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(battery)
                .task { await battery.refresh() }
        }
    }
}

struct RootView: View {
    var body: some View {
        TabView {
            DraftView()
                .tabItem { Label("拟稿", systemImage: "text.bubble") }
            SettingsView()
                .tabItem { Label("设置", systemImage: "gearshape") }
        }
        .tint(.orange)
    }
}

// ─────────────────────────────────────────────────────────────────────────
// 主流程：截图 → 选档 → 出稿
//
// iOS 没有悬浮窗，也不允许读别的 app 的屏幕，所以入口是系统截图：
// 用户在聊天里按下电源+音量上，回到 Aplomb，最新那张截图已经等在这里。
// ─────────────────────────────────────────────────────────────────────────

@MainActor
final class DraftModel: ObservableObject {
    @Published var shot: UIImage?
    @Published var draft: ReplyEngine.Draft?
    @Published var activeTone: Tone?
    @Published var status: String?
    @Published var busy = false
    @Published var outOfBattery = false

    /// 取相册里最新的一张截图（不是最新照片 —— 用户刚拍的猫不该被当成聊天）。
    func loadLatestScreenshot() {
        let opts = PHFetchOptions()
        opts.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        opts.fetchLimit = 1
        opts.predicate = NSPredicate(
            format: "(mediaSubtype & %d) != 0", PHAssetMediaSubtype.photoScreenshot.rawValue
        )
        guard let asset = PHAsset.fetchAssets(with: .image, options: opts).firstObject else {
            status = "相册里还没有截图 —— 先在聊天界面截一张"
            return
        }
        let req = PHImageRequestOptions()
        req.isSynchronous = false
        req.deliveryMode = .highQualityFormat
        req.resizeMode = .exact
        PHImageManager.default().requestImage(
            for: asset,
            targetSize: CGSize(width: 1280, height: 1280),
            contentMode: .aspectFit,
            options: req
        ) { [weak self] image, _ in
            guard let image else { return }
            Task { @MainActor in
                self?.shot = image
                self?.draft = nil
                self?.status = nil
            }
        }
    }

    func run(tone: Tone, battery: BatteryClient) async {
        guard let shot, let jpeg = shot.jpegData(compressionQuality: 0.7) else {
            status = "先选一张聊天截图"
            return
        }
        busy = true
        activeTone = tone
        status = "正在按「\(tone.name)」拟稿…"
        draft = nil
        outOfBattery = false
        Prefs.lastToneId = tone.id
        defer { busy = false }

        let prompt = ReplyEngine.prompt(
            tone: tone, myLanguage: Prefs.myLanguage, persona: Prefs.persona
        )
        let b64 = jpeg.base64EncodedString()
        do {
            let raw: String
            if Prefs.apiKey.isEmpty {
                raw = try await battery.draft(imageBase64: b64, prompt: prompt)
            } else {
                raw = try await AnthropicClient.vision(
                    key: Prefs.apiKey, imageBase64: b64, prompt: prompt
                )
            }
            if let parsed = ReplyEngine.parse(raw) {
                draft = parsed
                status = nil
                // 交给键盘：用户切到嘴替键盘时，这一稿已经在那儿等着了
                SharedDrafts.push(.init(
                    toneEmoji: tone.emoji, toneName: tone.name, text: parsed.reply
                ))
            } else {
                status = "没读懂这屏内容，换个角度再试一次"
            }
        } catch let e as BatteryClient.Empty {
            outOfBattery = true
            status = e.message
        } catch let e as BatteryClient.Failure {
            status = e.message
        } catch let e as AnthropicClient.Failure {
            status = e.message
        } catch {
            status = "网络不通，稍后再试"
        }
    }
}

struct DraftView: View {
    @EnvironmentObject private var battery: BatteryClient
    @StateObject private var model = DraftModel()
    @State private var tones = ToneConfig.load()
    @State private var picking: PhotosPickerItem?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    BatteryBar()

                    // ── 截图 ──
                    Group {
                        if let shot = model.shot {
                            Image(uiImage: shot)
                                .resizable()
                                .scaledToFit()
                                .frame(maxHeight: 260)
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                        } else {
                            RoundedRectangle(cornerRadius: 14)
                                .fill(.quaternary)
                                .frame(height: 160)
                                .overlay(
                                    Text("在聊天里截一张图，回到这里")
                                        .foregroundStyle(.secondary)
                                )
                        }
                    }

                    HStack {
                        Button {
                            model.loadLatestScreenshot()
                        } label: {
                            Label("载入最新截图", systemImage: "photo.badge.arrow.down")
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.orange)

                        PhotosPicker(selection: $picking, matching: .images) {
                            Label("相册", systemImage: "photo.on.rectangle")
                        }
                        .buttonStyle(.bordered)
                    }

                    // ── 情绪轮盘 ──
                    if model.shot != nil {
                        Text("挑一个语气")
                            .font(.headline)
                        ToneWheelView(tones: tones, active: model.activeTone) { tone in
                            Task { await model.run(tone: tone, battery: battery) }
                        }
                        .frame(height: 260)
                    }

                    if model.busy { ProgressView().frame(maxWidth: .infinity) }

                    if let status = model.status {
                        Text(status)
                            .font(.callout)
                            .foregroundStyle(model.outOfBattery ? .orange : .secondary)
                    }

                    if let draft = model.draft {
                        ReplyCardView(draft: draft, tone: model.activeTone, tones: tones) { tone in
                            Task { await model.run(tone: tone, battery: battery) }
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("Aplomb")
            .onChange(of: picking) { _, item in
                guard let item else { return }
                Task {
                    if let data = try? await item.loadTransferable(type: Data.self),
                       let img = UIImage(data: data) {
                        model.shot = img
                        model.draft = nil
                        model.status = nil
                    }
                }
            }
            .onAppear { tones = ToneConfig.load() }
        }
    }
}

/// 电池条 —— 免费额度的可视化，用完变成「填自己的 key」的入口。
struct BatteryBar: View {
    @EnvironmentObject private var battery: BatteryClient

    var body: some View {
        if !Prefs.apiKey.isEmpty {
            Label("用的是你自己的 API key", systemImage: "key.fill")
                .font(.footnote)
                .foregroundStyle(.secondary)
        } else if let b = battery.battery {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Image(systemName: b.isEmpty ? "battery.0percent" : "battery.100percent")
                        .foregroundStyle(b.isEmpty ? .red : .green)
                    Text(b.isEmpty ? "免费电池已用完" : "免费电池 \(b.bars)/\(b.barsTotal) 格")
                        .font(.subheadline.weight(.medium))
                    Spacer()
                    if b.drafts > 0 {
                        Text("已拟 \(b.drafts) 稿").font(.caption).foregroundStyle(.secondary)
                    }
                }
                ProgressView(value: b.fraction)
                    .tint(b.isEmpty ? .red : .green)
            }
            .padding(12)
            .background(.quaternary, in: RoundedRectangle(cornerRadius: 12))
        } else if battery.claiming {
            ProgressView("正在领取免费电池…").font(.footnote)
        }
    }
}
