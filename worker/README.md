# Aplomb 免费电池后端

一次拟稿 = 一格电。用户只看到电池格数，成本核算留在服务端。

## 端点

| | |
|---|---|
| `POST /api/aplomb/claim` `{deviceId}` | 匿名领一块电池 → `{token, battery}` |
| `POST /api/aplomb/draft` `Bearer` `{imageBase64, prompt}` | 拟稿 → `{text, battery}`；没电返回 402 |
| `GET /api/aplomb/battery` `Bearer` | 查余量 |

## 两层计费

1. **对用户**：一次成功拟稿扣一格，失败不扣。20 格一块。
2. **对我们**：同时按真实 token 用量累加美元，撞到 `HARD_CAP_USD` 即使还有格也停——
   防止超长截图把一格电撑成一块钱。

## 部署

```sh
cd worker
npx wrangler kv namespace create APLOMB      # 把返回的 id 填进 wrangler.toml
npx wrangler secret put ANTHROPIC_API_KEY
npx wrangler deploy
```
