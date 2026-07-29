import UIKit

/**
 嘴替键盘 —— 不打字，只送稿。

 用户在聊天里切到这个键盘，主 app 刚出的稿子已经列在这里，点一条就落进
 输入框。发送键仍然由用户自己按 —— 这条边界从 Android 版起就没变过。

 为什么没有键位：这个键盘只在「要发一条难回的消息」那几秒被切过来，之后
 用户就切回自己的输入法。做一套全键盘既没必要，也会让人误以为要长期替换
 掉他惯用的键盘。
 */
final class KeyboardViewController: UIInputViewController {

    private var drafts: [SharedDrafts.Item] = []
    private let scroll = UIScrollView()
    private let stack = UIStackView()
    private let hint = UILabel()
    private var nextKeyboardButton: UIButton!

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.09, green: 0.09, blue: 0.11, alpha: 1)
        buildChrome()
        buildList()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        reload()
    }

    // ── 顶栏：品牌 + 切回原键盘 ─────────────────────────────────────

    private func buildChrome() {
        let bar = UIStackView()
        bar.axis = .horizontal
        bar.alignment = .center
        bar.spacing = 8
        bar.isLayoutMarginsRelativeArrangement = true
        bar.layoutMargins = UIEdgeInsets(top: 10, left: 16, bottom: 6, right: 16)
        bar.translatesAutoresizingMaskIntoConstraints = false

        let title = UILabel()
        title.text = "Aplomb"
        title.textColor = UIColor(red: 0.96, green: 0.62, blue: 0.04, alpha: 1)
        title.font = .systemFont(ofSize: 14, weight: .semibold)

        let spacer = UIView()
        spacer.setContentHuggingPriority(.defaultLow, for: .horizontal)

        nextKeyboardButton = UIButton(type: .system)
        nextKeyboardButton.setTitle("⌨ 切回", for: .normal)
        nextKeyboardButton.titleLabel?.font = .systemFont(ofSize: 14)
        nextKeyboardButton.setTitleColor(.lightGray, for: .normal)
        nextKeyboardButton.addTarget(
            self,
            action: #selector(handleInputModeList(from:with:)),
            for: .allTouchEvents
        )

        bar.addArrangedSubview(title)
        bar.addArrangedSubview(spacer)
        bar.addArrangedSubview(nextKeyboardButton)
        view.addSubview(bar)

        NSLayoutConstraint.activate([
            bar.topAnchor.constraint(equalTo: view.topAnchor),
            bar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            bar.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            bar.heightAnchor.constraint(equalToConstant: 40),
        ])
    }

    private func buildList() {
        stack.axis = .vertical
        stack.spacing = 8
        stack.translatesAutoresizingMaskIntoConstraints = false

        scroll.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(stack)
        view.addSubview(scroll)

        hint.numberOfLines = 0
        hint.textAlignment = .center
        hint.textColor = .lightGray
        hint.font = .systemFont(ofSize: 13)
        hint.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(hint)

        NSLayoutConstraint.activate([
            scroll.topAnchor.constraint(equalTo: view.topAnchor, constant: 44),
            scroll.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 12),
            scroll.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -12),
            scroll.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -10),

            stack.topAnchor.constraint(equalTo: scroll.topAnchor),
            stack.leadingAnchor.constraint(equalTo: scroll.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: scroll.trailingAnchor),
            stack.bottomAnchor.constraint(equalTo: scroll.bottomAnchor),
            stack.widthAnchor.constraint(equalTo: scroll.widthAnchor),

            hint.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            hint.centerYAnchor.constraint(equalTo: view.centerYAnchor, constant: 10),
            hint.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            hint.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),

            view.heightAnchor.constraint(equalToConstant: 260),
        ])
    }

    // ── 数据 ───────────────────────────────────────────────────────

    private func reload() {
        stack.arrangedSubviews.forEach { $0.removeFromSuperview() }

        guard let loaded = SharedDrafts.load() else {
            // 读不到共享容器 = 用户没开「完全访问」。说清楚要去哪儿开，
            // 否则一片空白会被当成 app 坏了。
            hint.text = "打开「完全访问」后，Aplomb 拟好的稿子才能送到这里。\n设置 › 通用 › 键盘 › 键盘 › Aplomb › 允许完全访问"
            hint.isHidden = false
            scroll.isHidden = true
            return
        }
        drafts = loaded
        if drafts.isEmpty {
            hint.text = "还没有稿子。\n先在 Aplomb 里选一张聊天截图、挑个语气拟一稿。"
            hint.isHidden = false
            scroll.isHidden = true
            return
        }
        hint.isHidden = true
        scroll.isHidden = false
        for item in drafts { stack.addArrangedSubview(card(for: item)) }
    }

    private func card(for item: SharedDrafts.Item) -> UIView {
        let box = UIControl()
        box.backgroundColor = UIColor(white: 1, alpha: 0.09)
        box.layer.cornerRadius = 12
        box.translatesAutoresizingMaskIntoConstraints = false

        let tone = UILabel()
        tone.text = "\(item.toneEmoji) \(item.toneName)"
        tone.font = .systemFont(ofSize: 11, weight: .medium)
        tone.textColor = UIColor(red: 0.96, green: 0.62, blue: 0.04, alpha: 1)

        let body = UILabel()
        body.text = item.text
        body.numberOfLines = 3
        body.font = .systemFont(ofSize: 15)
        body.textColor = .white

        let col = UIStackView(arrangedSubviews: [tone, body])
        col.axis = .vertical
        col.spacing = 4
        col.isUserInteractionEnabled = false
        col.translatesAutoresizingMaskIntoConstraints = false
        box.addSubview(col)

        NSLayoutConstraint.activate([
            col.topAnchor.constraint(equalTo: box.topAnchor, constant: 10),
            col.leadingAnchor.constraint(equalTo: box.leadingAnchor, constant: 12),
            col.trailingAnchor.constraint(equalTo: box.trailingAnchor, constant: -12),
            col.bottomAnchor.constraint(equalTo: box.bottomAnchor, constant: -10),
        ])

        box.addAction(UIAction { [weak self] _ in
            self?.insert(item.text)
        }, for: .touchUpInside)
        return box
    }

    private func insert(_ text: String) {
        // 先清掉输入框里已有的内容，反复点不会叠加
        let proxy = textDocumentProxy
        if let before = proxy.documentContextBeforeInput, !before.isEmpty {
            for _ in 0..<before.count { proxy.deleteBackward() }
        }
        proxy.insertText(text)
        UIDevice.current.playInputClick()
    }

    override func textDidChange(_ textInput: UITextInput?) {
        // 深色键盘配深色底就够了，这里不跟随外观切换
    }
}
