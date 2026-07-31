import SwiftUI
import Photos
import PhotosUI

@main
struct AplombApp: App {
    @StateObject private var battery = BatteryClient()
    @StateObject private var pending = PendingDraft.shared

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(battery)
                .environmentObject(pending)
                .task { await battery.refresh() }
                .onOpenURL { QuickInvoke.handle($0) }
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

    /**
     取相册里最新的一张截图（不是最新照片 —— 用户刚拍的猫不该被当成聊天）。

     [maxAge] 是防呆用的：从悬浮球/快捷指令进来时，如果截图没被真正递过来，
     退回相册取到的可能是几天前那张 —— 替错对话拟一稿是这个产品最糟的失败
     方式，所以宁可报错也不猜。手动点「载入最新截图」时不设时限。
     */
    func loadLatestScreenshot(maxAge: TimeInterval? = nil) {
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
        if let maxAge,
           let taken = asset.creationDate,
           Date().timeIntervalSince(taken) > maxAge {
            status = "没拿到当前这屏的截图（相册里最新那张是旧的，不敢拿来拟稿）。"
                + "请检查快捷指令里「拟一稿」的「聊天截图」有没有接上「拍摄屏幕快照」。"
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
    @EnvironmentObject private var pending: PendingDraft
    @StateObject private var model = DraftModel()
    @State private var tones = ToneConfig.load()
    @State private var picking: PhotosPickerItem?

    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    BatteryBar()

                    // ── 截图 ──
                    Group {
                        if let shot = model.shot {
                            // 缩略图而已 —— 只要能认出「是这段对话」就够，
                            // 省下的高度留给真正要读的回复
                            Image(uiImage: shot)
                                .resizable()
                                .scaledToFit()
                                .frame(maxWidth: .infinity, maxHeight: 130)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12).strokeBorder(.quaternary)
                                )
                        } else {
                            RoundedRectangle(cornerRadius: 12)
                                .fill(.quaternary)
                                .frame(height: 96)
                                .overlay(
                                    Text("在聊天里截一张图，回到这里")
                                        .font(.footnote)
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

                    // ── 情绪选择：横排一行,不再占半屏 ──
                    if model.shot != nil {
                        ToneRowView(tones: tones, active: model.activeTone) { tone in
                            Task { await model.run(tone: tone, battery: battery) }
                        }
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
                        .id("reply")
                    }
                }
                .padding()
            }
            // 出稿即滚到回复 —— 拟完还要自己往下拖是最没道理的一步
            .onChange(of: model.draft) { _, d in
                guard d != nil else { return }
                withAnimation { proxy.scrollTo("reply", anchor: .top) }
            }
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
            // 悬浮球 / 轻点背面 / 快捷指令进来的请求：直接跑到出稿，
            // 省掉「打开 app → 找截图 → 选语气」三步
            .onChange(of: pending.nonce) { _, n in
                guard n > 0 else { return }
                tones = ToneConfig.load()
                guard let tone = pending.resolve(from: tones) else { return }
                let handed = pending.image
                if let handed {
                    model.shot = handed
                    model.draft = nil
                    model.status = nil
                } else {
                    // 快捷指令没递图 —— 只认 90 秒内的截图，旧的宁可不拟
                    model.shot = nil
                    model.loadLatestScreenshot(maxAge: 90)
                }
                Task {
                    for _ in 0..<20 where model.shot == nil && model.status == nil {
                        try? await Task.sleep(for: .milliseconds(100))
                    }
                    guard model.shot != nil else { return }
                    await model.run(tone: tone, battery: battery)
                }
            }
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
