import StoreKit
import SwiftUI

/**
 订阅 —— 每月自动续一块电池。

 分工：StoreKit 负责收钱和记录权益，**发电由服务端说了算**。客户端把
 交易号报给后端，后端拿去问苹果这笔订阅是否有效，有效才发本月的电池。
 只信客户端的话，改个返回值就能白嫖算力。
 */
@MainActor
final class Subscriptions: ObservableObject {

    enum Tier: String, CaseIterable, Identifiable {
        case lite = "aplomb.sub.lite"
        case plus = "aplomb.sub.plus"
        case pro  = "aplomb.sub.pro"

        var id: String { rawValue }

        var title: String {
            switch self {
            case .lite: return L("tier.lite")
            case .plus: return L("tier.plus")
            case .pro:  return L("tier.pro")
            }
        }
        /// 每月发多少格 —— 与服务端 TIERS 保持一致。
        var bars: Int {
            switch self {
            case .lite: return 100
            case .plus: return 300
            case .pro:  return 1000
            }
        }
        var blurb: String {
            switch self {
            case .lite: return L("tier.lite.blurb")
            case .plus: return L("tier.plus.blurb")
            case .pro:  return L("tier.pro.blurb")
            }
        }
    }

    @Published private(set) var products: [Product] = []
    @Published private(set) var activeTier: Tier?
    @Published private(set) var busy = false
    @Published var lastError: String?

    private var updates: Task<Void, Never>?

    init() {
        // 续订、退款、家庭共享都会从这里推过来 —— 必须常驻监听，
        // 否则用户续了费而 app 不知道。
        updates = Task { [weak self] in
            for await result in StoreKit.Transaction.updates {
                await self?.handle(result)
            }
        }
    }

    deinit { updates?.cancel() }

    func load() async {
        do {
            let ids = Tier.allCases.map(\.rawValue)
            products = try await Product.products(for: ids)
                .sorted { $0.price < $1.price }
        } catch {
            lastError = "读取订阅项目失败：\(error.localizedDescription)"
        }
        await refreshEntitlement()
    }

    /// 当前有效的订阅（沙盒和正式环境都走这里）。
    func refreshEntitlement() async {
        var found: Tier?
        for await result in StoreKit.Transaction.currentEntitlements {
            guard case .verified(let t) = result else { continue }
            if let tier = Tier(rawValue: t.productID) { found = tier }
        }
        activeTier = found
    }

    func purchase(_ product: Product, battery: BatteryClient) async {
        busy = true
        lastError = nil
        defer { busy = false }
        do {
            switch try await product.purchase() {
            case .success(let verification):
                await handle(verification, battery: battery)
            case .userCancelled:
                break
            case .pending:
                lastError = "购买待确认（可能需要家长同意），完成后会自动生效。"
            @unknown default:
                break
            }
        } catch {
            lastError = "购买失败：\(error.localizedDescription)"
        }
    }

    func restore(battery: BatteryClient) async {
        busy = true
        defer { busy = false }
        try? await AppStore.sync()
        await refreshEntitlement()
        await syncToServer(battery: battery)
    }

    private func handle(
        _ result: VerificationResult<StoreKit.Transaction>, battery: BatteryClient? = nil
    ) async {
        guard case .verified(let transaction) = result else {
            lastError = "这笔交易没通过校验。"
            return
        }
        await transaction.finish()
        await refreshEntitlement()
        if let battery { await syncToServer(battery: battery) }
    }

    /// 把当前权益报给后端换电池。开 app、买完、恢复购买时都会调。
    func syncToServer(battery: BatteryClient) async {
        var latest: StoreKit.Transaction?
        for await result in StoreKit.Transaction.currentEntitlements {
            guard case .verified(let t) = result else { continue }
            if Tier(rawValue: t.productID) != nil { latest = t }
        }
        guard let t = latest else { return }
        do {
            try await battery.subscribe(
                productId: t.productID, transactionId: String(t.id)
            )
        } catch let e as BatteryClient.Failure {
            lastError = e.message
        } catch {
            lastError = "开通失败，稍后再试。"
        }
    }
}
