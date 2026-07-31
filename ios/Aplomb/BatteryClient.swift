import Foundation
import UIKit

/**
 电池 —— 免费额度的对外单位。

 一次成功拟稿 = 一格电。首次启动匿名领一块（20 格），用完引导用户填自己的
 key 或（下一版）内购续电。成本核算全在服务端，客户端只知道还剩几格。
 */
@MainActor
final class BatteryClient: ObservableObject {

    struct Battery: Codable, Equatable {
        var bars: Int
        var barsTotal: Int
        var drafts: Int

        var fraction: Double { barsTotal > 0 ? Double(bars) / Double(barsTotal) : 0 }
        var isEmpty: Bool { bars <= 0 }
    }

    /// 服务端没电时抛这个，UI 据此换成「续电 / 填自己的 key」。
    struct Empty: Error { let message: String }
    struct Failure: Error { let message: String }

    @Published private(set) var battery: Battery?
    @Published private(set) var claiming = false

    private let base = "https://aicon.solutions/api/aplomb"
    private var token: String? {
        get { UserDefaults.standard.string(forKey: "battery.token") }
        set { UserDefaults.standard.set(newValue, forKey: "battery.token") }
    }

    /// 设备标识 —— 同一台机器重开 app 拿回同一块电池。
    private var deviceId: String {
        if let saved = UserDefaults.standard.string(forKey: "battery.device") { return saved }
        let id = UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString
        UserDefaults.standard.set(id, forKey: "battery.device")
        return id
    }

    /// 首启或缺 token 时领电池；已有则只刷新余量。
    func refresh() async {
        if token == nil {
            await claim()
        } else {
            await loadBattery()
        }
    }

    private func claim() async {
        claiming = true
        defer { claiming = false }
        struct Req: Encodable { let deviceId: String }
        struct Res: Decodable { let token: String; let battery: Battery }
        guard let res: Res = try? await post("claim", body: Req(deviceId: deviceId), auth: false)
        else { return }
        token = res.token
        battery = res.battery
    }

    private func loadBattery() async {
        struct Res: Decodable { let battery: Battery }
        guard let t = token,
              var req = request("battery", method: "GET") as URLRequest?
        else { return }
        req.setValue("Bearer \(t)", forHTTPHeaderField: "authorization")
        guard let (data, resp) = try? await send(req) else { return }
        if resp.statusCode == 401 {
            // token 失效（后端重建过）—— 重新领一块
            token = nil
            await claim()
            return
        }
        battery = (try? JSONDecoder().decode(Res.self, from: data))?.battery
    }

    /// 用一格电换一份稿子。
    func draft(imageBase64: String, prompt: String) async throws -> String {
        guard let t = token else { throw Failure(message: "电池还没领到，稍后再试") }
        struct Req: Encodable { let imageBase64: String; let prompt: String }
        struct Res: Decodable { let text: String; let battery: Battery }
        struct Err: Decodable { let error: String?; let message: String?; let battery: Battery? }

        var req = request("draft", method: "POST")
        req.setValue("Bearer \(t)", forHTTPHeaderField: "authorization")
        req.httpBody = try JSONEncoder().encode(Req(imageBase64: imageBase64, prompt: prompt))

        let (data, resp) = try await send(req)
        let code = resp.statusCode
        if code == 402 {
            let err = try? JSONDecoder().decode(Err.self, from: data)
            if let b = err?.battery { battery = b }
            throw Empty(message: err?.message ?? "免费电池用完了")
        }
        guard code == 200, let ok = try? JSONDecoder().decode(Res.self, from: data) else {
            let err = try? JSONDecoder().decode(Err.self, from: data)
            throw Failure(message: err?.error ?? "服务暂时不可用（\(code)）")
        }
        battery = ok.battery
        return ok.text
    }

    // ── plumbing ───────────────────────────────────────────────────────

    /**
     发一次请求，最多重试两次。

     后端和官网静态站挂在同一个域名上，Cloudflare 偶尔会把请求路由到静态站，
     返回一个非 JSON 的 404 页面。概率不高但真实存在，撞上一次用户就看到
     「服务暂时不可用」——重试一下就好了，不该让用户自己再点一遍。
     */
    private func send(_ req: URLRequest, attempts: Int = 3) async throws -> (Data, HTTPURLResponse) {
        var lastError: Error?
        for attempt in 0..<attempts {
            do {
                let (data, resp) = try await URLSession.shared.data(for: req)
                let http = resp as? HTTPURLResponse
                let code = http?.statusCode ?? 0
                // 402/401 是后端自己的答复，别重试；只有「根本没落到 worker
                // 上」这种情况才值得重来 —— 特征是响应不是 JSON。
                let looksJSON = data.first == UInt8(ascii: "{")
                if looksJSON || code == 402 || code == 401 {
                    return (data, http ?? HTTPURLResponse())
                }
                lastError = Failure(message: "服务暂时不可用（\(code)）")
            } catch {
                lastError = error
            }
            if attempt < attempts - 1 {
                try? await Task.sleep(for: .milliseconds(400 * (attempt + 1)))
            }
        }
        throw lastError ?? Failure(message: "网络不通")
    }

    private func request(_ path: String, method: String) -> URLRequest {
        var req = URLRequest(url: URL(string: "\(base)/\(path)")!)
        req.httpMethod = method
        req.setValue("application/json", forHTTPHeaderField: "content-type")
        req.timeoutInterval = 60
        return req
    }

    private func post<B: Encodable, R: Decodable>(_ path: String, body: B, auth: Bool) async throws -> R {
        var req = request(path, method: "POST")
        if auth, let t = token { req.setValue("Bearer \(t)", forHTTPHeaderField: "authorization") }
        req.httpBody = try JSONEncoder().encode(body)
        let (data, _) = try await send(req)
        return try JSONDecoder().decode(R.self, from: data)
    }
}
