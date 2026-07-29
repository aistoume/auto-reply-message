# Aplomb for iOS

嘴替的 iOS 版。iOS 没有悬浮窗、也不允许读别的 app 的屏幕，所以入口是系统截图：

1. 在聊天里截一张图（电源 + 音量上）
2. 回到 Aplomb —— 最新那张截图一键载入
3. 选一个语气（情绪轮盘）
4. 出稿：潜台词 / 正文 / 建议
5. 复制，回聊天粘贴

### 嘴替键盘（直接填进输入框）

复制粘贴是兜底路径。装上嘴替键盘之后：

1. 在聊天里截图 → 回 Aplomb 选语气拟稿（稿子自动进 App Group）
2. 回到聊天，点输入框，用地球键切到 **Aplomb 键盘**
3. 刚拟的稿子已经列在那里，点一条 → 落进输入框
4. **发送键仍然你自己按**

iOS 的键盘扩展读不了相册、也拿不到用户刚截的图，所以分工是 **app 出稿、
键盘送稿**，中间靠 App Group 交接。这也意味着键盘必须开「完全访问」——
没开时键盘会显示去哪儿开的引导，而不是一片空白。

启用：设置 › 通用 › 键盘 › 键盘 › 添加新键盘 › Aplomb，再进去打开「允许完全访问」。

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
