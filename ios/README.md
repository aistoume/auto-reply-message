# Aplomb for iOS

嘴替的 iOS 版。iOS 没有悬浮窗、也不允许读别的 app 的屏幕，所以入口是系统截图：

1. 在聊天里截一张图（电源 + 音量上）
2. 回到 Aplomb —— 最新那张截图一键载入
3. 选一个语气（情绪轮盘）
4. 出稿：潜台词 / 正文 / 建议
5. 复制，回聊天粘贴

> 下一版做键盘扩展，就能像 Android 那样直接填进输入框。

## 免费电池

首次启动匿名领一块电池（20 格），一次拟稿一格。用完可以在设置里填自己的
Anthropic key 继续。续电内购在下一版。

后端见 `../worker`。

## 构建

```sh
cd ios
xcodebuild -project Aplomb.xcodeproj -scheme Aplomb \
  -destination 'platform=iOS Simulator,name=iPhone 16' build
```

iOS 17+，无依赖。源码是文件系统同步组，新增 .swift 自动纳入编译。

## 源码

| 文件 | 作用 |
|---|---|
| `AplombApp.swift` | app 壳 + 拟稿主流程 + 电池条 |
| `BatteryClient.swift` | 免费电池（领取/拟稿/查余量） |
| `ReplyEngine.swift` | prompt 与解析（与 Android 端逐字对齐） |
| `ToneConfig.swift` | 情绪档位（与 Android 同构） |
| `ToneWheelView.swift` | 情绪轮盘 |
| `ReplyCardView.swift` | 回复卡 + 换档重出 |
| `SettingsView.swift` | 电池 / 语言 / 人设 / 档位 / 自带 key |
| `Prefs.swift` | 本机设置 + 自带 key 的直连路径 |
