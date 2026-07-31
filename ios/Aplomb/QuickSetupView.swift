import SwiftUI

/**
 「怎么一键唤起」的引导页。

 这一页存在的理由：iOS 不让第三方 app 自己画悬浮球，也不让 app 代替用户
 去改辅助功能设置 —— 唤起方式只能由用户自己在系统设置里配一次。而这几步
 藏得很深（设置 › 辅助功能 › 触控 › 辅助触控 › 自定操作 › 轻点两下），
 不写出来没人找得到，找不到的人就只会「打开 app → 翻相册 → 选语气」，
 那是三步，慢到不会有人在真的要回消息的时候用。

 所以这页不是锦上添花，是这个产品能不能被用起来的开关。
 */
struct QuickSetupView: View {
    @Environment(\.dismiss) private var dismiss
    /// 预建好的快捷指令 iCloud 分享链接。有它就能跳过手搭那一步。
    /// nil 表示还没做，界面自动退回手动路径，不会显示一个点不动的按钮。
    static let sharedShortcutURL: URL? = nil

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    Text(L("setup.lead"))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    if let url = Self.sharedShortcutURL {
                        Link(destination: url) {
                            Label(L("setup.get_shortcut"), systemImage: "square.and.arrow.down")
                                .font(.headline)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(Color.orange, in: RoundedRectangle(cornerRadius: 14))
                                .foregroundStyle(.white)
                        }
                        step(1, L("setup.a1.title"), L("setup.a1.body"))
                    } else {
                        section(L("setup.part1"))
                        step(1, L("setup.b1.title"), L("setup.b1.body"))
                        step(2, L("setup.b2.title"), L("setup.b2.body"))
                        step(3, L("setup.b3.title"), L("setup.b3.body"))
                    }

                    section(L("setup.part2"))
                    step(4, L("setup.c1.title"), L("setup.c1.body"))
                    step(5, L("setup.c2.title"), L("setup.c2.body"))

                    Divider().padding(.vertical, 4)

                    VStack(alignment: .leading, spacing: 8) {
                        Text(L("setup.after.title")).font(.headline)
                        Text(L("setup.after.body"))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }

                    // 截图那一步是最容易漏的：漏了就会拿相册里的旧图去拟稿，
                    // 出来的东西驴唇不对马嘴,而用户根本不知道错在哪。
                    Label(L("setup.warn"), systemImage: "exclamationmark.triangle.fill")
                        .font(.footnote)
                        .foregroundStyle(.orange)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.orange.opacity(0.1), in: RoundedRectangle(cornerRadius: 10))
                }
                .padding()
            }
            .navigationTitle(L("setup.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("common.close")) { dismiss() }
                }
            }
        }
    }

    private func section(_ title: String) -> some View {
        Text(title)
            .font(.footnote.weight(.semibold))
            .foregroundStyle(.secondary)
            .textCase(.uppercase)
            .padding(.top, 6)
    }

    private func step(_ n: Int, _ title: String, _ body: String) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Text("\(n)")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: 26, height: 26)
                .background(Color.orange, in: Circle())
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.subheadline.weight(.semibold))
                Text(body).font(.footnote).foregroundStyle(.secondary)
            }
        }
    }
}
