# Aplomb · Google Play 上架资料

包名 `solutions.aicon.aplomb` · versionCode 1 · versionName 1.0.0
上传包：`dist/aplomb-1.0.0-vc1.aab`（5.0 MB，已用 release key 签名）

---

## 零、只想先装到手机上试 —— 最短路径

内部测试（Internal testing）**不需要**商店信息、截图、宣传图，也不受
「新账号要 12 个测试者跑 14 天」那条规则限制。这条路今天就能走完：

1. Play Console → **创建应用**
   - 应用名称 `Aplomb`
   - 默认语言 `英语（美国）`
   - 应用或游戏 → **应用**
   - 免费或付费 → **免费**（订阅是应用内购买，不影响这里选免费）
   - 勾选两个声明

2. 左栏 **测试 → 内部测试 → 创建新的版本**
   - 上传 `dist/aplomb-1.0.0-vc1.aab`
   - Play 应用签名：**接受默认**（Google 托管正式签名密钥，我们这把是上传密钥，丢了还能重置）
   - 版本名称 `1.0.0 (1)`，版本说明随便写一句

3. 同一页 **测试人员** 标签 → 创建邮件列表 → 把你自己的 Google 账号加进去 → 保存

4. 保存并发布 → 复制页面底部的 **加入链接**，用手机上那个 Google 账号打开 → 接受邀请 → 跳转 Play 商店安装

> 中间 Play 会拦着让你先填「应用内容」那一栏，见第二节，全部答案都给你了。

---

## 一、商店信息（正式上架时才必填，内部测试可跳过）

### 应用名称（30 字符）
```
Aplomb — Say It Right
```

### 简短说明（80 字符）
```
Screenshot the chat, pick a tone, get a reply you can actually send.
```
简中：
```
截一张聊天图，选一个语气，拿到一句能直接发出去的话。
```

### 完整说明（4000 字符）
和 App Store 的 Description 同一份，见 `ios/STORE.md` 第二节。
Play 这边要改两处：

- 「PRIVACY」段落里 App Store 相关表述换成 Google Play
- 结尾补一句 Play 要求的订阅披露：
  ```
  Subscriptions are billed through your Google Play account and renew monthly until cancelled. Manage or cancel any time in Play Store → Subscriptions.
  ```

### 图形素材（正式上架必填）
| 素材 | 规格 | 状态 |
|---|---|---|
| 应用图标 | 512×512 PNG，32 位 | 从 `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` 放大 |
| 功能图片 | 1024×500 PNG/JPG | ⚠️ 还没做 |
| 手机截图 | 至少 2 张，最少边 320px | ⚠️ 还没做，要真机跑一遍才有 |

---

## 二、应用内容（App content）—— 内部测试也拦着必填

左栏 **政策 → 应用内容**，逐项：

| 项目 | 怎么答 |
|---|---|
| **隐私政策** | `https://aicon.solutions/aplomb/privacy` |
| **应用访问权限** | 「所有功能均可使用，无需特殊访问权限」——app 没有账号体系 |
| **广告** | 「不包含广告」 |
| **内容分级** | 填问卷，类别选 **参考工具 / 新闻**；暴力色情赌博全选否；**「用户生成内容 / AI 生成内容」要答是**（app 会产出模型生成的文本） |
| **目标受众** | 13 岁及以上（不要勾任何儿童年龄段） |
| **新闻应用** | 否 |
| **新冠接触者追踪** | 否 |
| **数据安全** | 见下 |
| **政府应用** | 否 |
| **金融功能** | 否 |
| **健康应用** | 否 |

### 数据安全（Data safety）逐项

**收集的数据：**

| 数据类型 | 收集？ | 分享？ | 用途 | 必需？ |
|---|---|---|---|---|
| **照片** | 是 | 是（转给 AI 服务商处理） | 应用功能 | 是 |
| **设备或其他 ID** | 是 | 否 | 应用功能 | 是 |
| **购买记录** | 是 | 否 | 应用功能 | 否 |
| 其余全部 | 否 | — | — | — |

**其他问题：**
- 数据传输是否加密？**是**（全程 HTTPS）
- 用户能否请求删除数据？**是**（卸载即清本机；服务端记录发邮件删）
- 数据是否用于追踪？**否**

> 截图虽然不留存，但它是核心功能且会离开设备，必须申报。
> 「设备 ID」是 app 自己生成的匿名 UUID，只用来记电池额度，不是广告 ID。

---

## 三、订阅商品

左栏 **创收 → 商品 → 订阅 → 创建订阅**。三个，id 必须和 iOS 一字不差：

| 商品 ID | 名称 | 基础方案 ID | 周期 | 价格 |
|---|---|---|---|---|
| `aplomb.sub.lite` | Light / 轻度 | `lite-monthly` | 1 个月 | $2.99 |
| `aplomb.sub.plus` | Regular / 常用 | `plus-monthly` | 1 个月 | $6.99 |
| `aplomb.sub.pro` | Heavy / 重度 | `pro-monthly` | 1 个月 | $19.99 |

每个订阅要做两件事才会「激活」：
1. 填**商品说明**（名称 + 说明，中英都填）
2. 建一个**基础方案（base plan）** 并点**激活** —— 只建订阅不建基础方案的话，
   `queryProductDetailsAsync` 返回空列表，app 里订阅页会一直显示「不可用」

说明文案：
- Light：`100 drafts a month. For the occasional message you can't just answer.` / `每月 100 次拟稿。给偶尔那几条不好回的消息。`
- Regular：`300 drafts a month. A few every day.` / `每月 300 次拟稿。每天都有几条。`
- Heavy：`1000 drafts a month. All day, every workday.` / `每月 1000 次拟稿。整个工作日都在用。`

---

## 四、用测试账号试订阅（不真扣钱）

**这一步和内部测试是两回事**，要单独配：

1. Play Console **左上角切到开发者账号层级**（不是应用层级）→ **设置 → 许可测试**
2. 把要用来测试的 Google 账号邮箱加进「许可测试人员」
3. 许可响应保持 `RESPOND_NORMALLY`

配好之后，这些账号在 app 里买订阅会走完整流程但**不实际扣款**，
而且续期被压缩（月订阅 5 分钟续一次），方便观察续期行为。

> ⚠️ **服务端还没配 Google 密钥之前，测试购买会在最后一步失败。**
> Play 那边购买会成功，但 app 把凭据交给我们服务器时，worker 查不到校验密钥
> 会拒绝发电池（fail-closed 设计），你会看到「服务端还没配好 Google Play
> 校验密钥」。这不是 bug，是第五节还没做。

---

## 五、服务端要配的三个密钥

Play Console → **设置 → API 访问权限** → 关联 Google Cloud 项目 → 创建服务账号
→ 授予「查看财务数据、订单和取消调查回复」权限 → 下载 JSON 凭据。

然后：
```bash
cd /Users/youbinmo/Develop/zuiti/worker
npx wrangler secret put GOOGLE_SA_EMAIL        # JSON 里的 client_email
npx wrangler secret put GOOGLE_SA_PRIVATE_KEY  # JSON 里的 private_key 全文
npx wrangler secret put ANDROID_PACKAGE        # solutions.aicon.aplomb
```

> 服务账号刚授权后 Google 那边要等几小时到一天才生效，别急着判定配错了。

---

## 六、签名密钥 —— 备份

```
/Users/youbinmo/Develop/zuiti/aplomb-release.keystore
/Users/youbinmo/Develop/zuiti/keystore.properties   ← 密码在这里
```

两个文件都是 gitignored 的，**不在版本库里**。丢了的话：

- 开了 Play 应用签名（默认开）→ 还能申请重置上传密钥，不致命
- 没开 → 这个包名永远发不了更新，只能换包名重新上架

复制一份到密码管理器或加密盘里。
