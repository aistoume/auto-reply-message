import StoreKit
import SwiftUI

/**
 订阅页 —— 三档，每月自动续电池。

 刻意把「自带 API key 也能用」写在页面上：藏着这条会让人觉得被逼着付费，
 而愿意自己配 key 的用户本来也不会买订阅，留住他们比多骗一单更划算。
 */
struct PaywallView: View {
    @EnvironmentObject private var battery: BatteryClient
    @StateObject private var store = Subscriptions()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("每月自动续电")
                            .font(.title2.weight(.bold))
                        Text("一次拟稿用一格电。选一档，每个月自动回满。")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }

                    if let active = store.activeTier {
                        Label("当前：\(active.title)（每月 \(active.bars) 格）",
                              systemImage: "checkmark.seal.fill")
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(.green)
                    }

                    if store.products.isEmpty {
                        ProgressView("读取中…").frame(maxWidth: .infinity, minHeight: 120)
                    } else {
                        ForEach(store.products, id: \.id) { product in
                            tierCard(product)
                        }
                    }

                    if let err = store.lastError {
                        Text(err).font(.footnote).foregroundStyle(.orange)
                    }

                    Button("恢复购买") {
                        Task { await store.restore(battery: battery) }
                    }
                    .font(.footnote)
                    .frame(maxWidth: .infinity)

                    VStack(alignment: .leading, spacing: 6) {
                        Text("订阅按月自动续期，可随时在系统「订阅」里取消。")
                        Text("不想订阅也行：在设置里填自己的 Anthropic API key，走你自己的账户，功能完全一样。")
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }
                .padding()
            }
            .navigationTitle("续电")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
            .task {
                await store.load()
                await store.syncToServer(battery: battery)
            }
        }
    }

    private func tierCard(_ product: Product) -> some View {
        let tier = Subscriptions.Tier(rawValue: product.id)
        let isActive = store.activeTier == tier
        return Button {
            Task { await store.purchase(product, battery: battery) }
        } label: {
            HStack(alignment: .center, spacing: 14) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(tier?.title ?? product.displayName)
                        .font(.headline)
                    Text("每月 \(tier?.bars ?? 0) 格电")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Text(tier?.blurb ?? "")
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text(product.displayPrice)
                        .font(.title3.weight(.semibold))
                    Text("/ 月").font(.caption2).foregroundStyle(.secondary)
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(isActive ? Color.green.opacity(0.12) : Color.secondary.opacity(0.09))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(isActive ? .green : .clear, lineWidth: 2)
            )
        }
        .buttonStyle(.plain)
        .disabled(store.busy || isActive)
    }
}
