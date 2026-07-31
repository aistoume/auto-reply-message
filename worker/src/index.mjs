/**
 * Aplomb 免费电池 —— 服务端 Claude 代理，按「一次拟稿 = 一格电」计费。
 *
 *   POST /api/aplomb/claim    {deviceId}      → {token, battery}
 *   POST /api/aplomb/draft    Bearer <token>  → {draft, battery}
 *   GET  /api/aplomb/battery  Bearer <token>  → {battery}
 *
 * 为什么不沿用 nodx free-tier：
 *  - 那个只收文字，嘴替的输入永远是一张聊天截图；
 *  - 那个要用户注册用户名，试用环节多一步就掉一半人 —— 这里按设备匿名发放；
 *  - 「credits」用户读不懂，「还剩 12 格电」一眼就懂，所以对外单位是电池格数。
 *
 * 计费两层：
 *  1. 对用户：一次成功拟稿扣一格电（失败不扣），格数是唯一对外单位；
 *  2. 对我们：同时按真实 token 用量累计美元，超出硬上限即使还有格也停 ——
 *     防止有人用超长截图把一格电撑成一块钱。
 */

const MODEL = 'claude-sonnet-5';
/** USD / 1M tokens (input / output) —— 读潜台词值得用好模型，所以按 Sonnet 记。 */
const PRICE_IN = 3.0;
const PRICE_OUT = 15.0;

/** 免费试用一块电池的格数。按每次约 $0.009 估，20 格 ≈ $0.18。 */
const BATTERY_BARS = 20;
/** 免费档的美元硬上限 —— 格数用完前先撞到它就停。 */
const HARD_CAP_USD = 0.35;

/**
 * 订阅档位。每档每月发一次电池，续订时重置。
 *
 * 定价按真实成本反推：一次拟稿约 $0.009，苹果抽 15–30%。格数给得比
 * 「刚好回本」宽一些 —— 用户在快没电时会本能省着用，而省着用的产品
 * 留不住人；宁可毛利薄一点，也要让人放心用。
 */
const TIERS = {
  'aplomb.sub.lite': { bars: 100, capUsd: 1.6 },
  'aplomb.sub.plus': { bars: 300, capUsd: 4.5 },
  'aplomb.sub.pro': { bars: 1000, capUsd: 14.0 },
};

// 输出要装下 五个字段 + 正文 + 回译,中文还更吃 token —— 700 会在
// 中英混排的稿子上被截断甚至一个字都吐不出来。
const MAX_TOKENS = 1600;
/** 截图 base64 上限 ≈ 1.5MB 原图，超了就是有人在灌 payload。 */
const MAX_IMAGE_CHARS = 2_000_000;

const CORS = {
  'access-control-allow-origin': '*',
  'access-control-allow-headers': 'content-type, authorization',
  'access-control-allow-methods': 'GET, POST, OPTIONS',
};

const json = (obj, status = 200) =>
  new Response(JSON.stringify(obj), {
    status,
    headers: { 'content-type': 'application/json', 'cache-control': 'no-store', ...CORS },
  });

const round6 = (n) => Math.round(n * 1e6) / 1e6;
const costUsd = (i, o) => (i * PRICE_IN + o * PRICE_OUT) / 1_000_000;

/** 对外只暴露电池，不暴露成本。 */
const batteryOf = (acct) => ({
  bars: Math.max(0, acct.bars ?? 0),
  barsTotal: acct.barsTotal ?? BATTERY_BARS,
  drafts: acct.drafts ?? 0,
  // 订阅态：客户端据此显示「本月还剩 / 下次续期」而不是「免费试用」
  tier: acct.tier ?? null,
  renewsAt: acct.renewsAt ?? null,
});

export default {
  async fetch(request, env) {
    if (request.method === 'OPTIONS') return new Response(null, { headers: CORS });
    const { pathname } = new URL(request.url);
    try {
      if (pathname === '/api/aplomb/claim') return await claim(request, env);
      if (pathname === '/api/aplomb/draft') return await draft(request, env);
      if (pathname === '/api/aplomb/battery') return await battery(request, env);
      if (pathname === '/api/aplomb/subscribe') return await subscribe(request, env);
    } catch (e) {
      return json({ error: String(e?.message ?? e) }, 500);
    }
    return json({ error: 'not found' }, 404);
  },
};

// ── 账户 ───────────────────────────────────────────────────────────────────

function newToken() {
  const bytes = crypto.getRandomValues(new Uint8Array(24));
  return [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/**
 * 匿名领取：设备 ID 换 token。同一设备重复调用返回同一个账户，
 * 卸载重装拿的是 iOS 的 identifierForVendor，会变 —— 这一版接受这个漏洞，
 * 真要堵得等接了 IAP 用收据校验。
 */
async function claim(request, env) {
  const body = await request.json().catch(() => ({}));
  const deviceId = String(body.deviceId ?? '').trim();
  if (!/^[A-Za-z0-9-]{8,64}$/.test(deviceId)) {
    return json({ error: 'bad deviceId' }, 400);
  }

  const existingToken = await env.APLOMB.get(`device:${deviceId}`);
  if (existingToken) {
    const raw = await env.APLOMB.get(`token:${existingToken}`);
    if (raw) return json({ token: existingToken, battery: batteryOf(JSON.parse(raw)) });
  }

  const token = newToken();
  const acct = {
    deviceId,
    bars: BATTERY_BARS,
    barsTotal: BATTERY_BARS,
    spentUsd: 0,
    drafts: 0,
    createdAt: new Date().toISOString(),
  };
  await env.APLOMB.put(`device:${deviceId}`, token);
  await env.APLOMB.put(`token:${token}`, JSON.stringify(acct));
  return json({ token, battery: batteryOf(acct) });
}

async function loadAccount(request, env) {
  const auth = request.headers.get('authorization') ?? '';
  const token = auth.startsWith('Bearer ') ? auth.slice(7).trim() : '';
  if (!token) return { error: json({ error: 'missing token' }, 401) };
  const raw = await env.APLOMB.get(`token:${token}`);
  if (!raw) return { error: json({ error: 'unknown token' }, 401) };
  return { acct: JSON.parse(raw), token };
}

async function battery(request, env) {
  const { acct, error } = await loadAccount(request, env);
  if (error) return error;
  return json({ battery: batteryOf(acct) });
}

// ── 订阅 ───────────────────────────────────────────────────────────────────

/**
 * 用 App Store 的签名交易换本月的电池。
 *
 * 验签走苹果官方的 App Store Server API：把 transactionId 报给苹果，
 * 由苹果告诉我们这笔订阅当前是不是有效。**没配好密钥时一律拒绝发电**
 * —— 宁可用户暂时买不了，也不能因为「先信着」被人白嫖算力。
 */
async function subscribe(request, env) {
  const { acct, token, error } = await loadAccount(request, env);
  if (error) return error;

  const body = await request.json().catch(() => ({}));
  const productId = String(body.productId ?? '');
  const transactionId = String(body.transactionId ?? '');
  const tier = TIERS[productId];
  if (!tier || !transactionId) return json({ error: 'bad product' }, 400);

  const verdict = await verifyWithApple(env, transactionId, productId);
  if (!verdict.ok) {
    return json({ error: 'verify_failed', message: verdict.message }, 402);
  }

  const raw = await env.APLOMB.get(`token:${token}`);
  const fresh = raw ? JSON.parse(raw) : acct;
  const period = verdict.expiresAt ?? null;

  // 同一个计费周期内重复上报（每次开 app 都会报）不重复发电，
  // 否则用户只要杀进程重开就能刷满电池。
  if (fresh.tier === productId && fresh.renewsAt === period) {
    return json({ battery: batteryOf(fresh), granted: false });
  }
  fresh.tier = productId;
  fresh.renewsAt = period;
  fresh.bars = tier.bars;
  fresh.barsTotal = tier.bars;
  fresh.spentUsd = 0;          // 新周期，成本账重新算
  fresh.lastGrantAt = new Date().toISOString();
  await env.APLOMB.put(`token:${token}`, JSON.stringify(fresh));
  return json({ battery: batteryOf(fresh), granted: true });
}

/** 用 App Store Server API 查这笔交易当前是否有效。 */
async function verifyWithApple(env, transactionId, productId) {
  const { APPSTORE_ISSUER_ID, APPSTORE_KEY_ID, APPSTORE_PRIVATE_KEY, APPSTORE_BUNDLE_ID } = env;
  if (!APPSTORE_ISSUER_ID || !APPSTORE_KEY_ID || !APPSTORE_PRIVATE_KEY) {
    // 失败关闭：没有验签能力就不发电。
    return { ok: false, message: '服务端还没配好 App Store 校验密钥，暂时无法开通。' };
  }
  const jwt = await appleJwt(env);
  // 生产查不到就查沙盒 —— 开发期的交易只存在于沙盒环境。
  for (const host of ['api.storekit.itunes.apple.com', 'api.storekit-sandbox.itunes.apple.com']) {
    const res = await fetch(
      `https://${host}/inApps/v1/subscriptions/${encodeURIComponent(transactionId)}`,
      { headers: { authorization: `Bearer ${jwt}` } },
    );
    if (res.status === 404) continue;
    if (!res.ok) return { ok: false, message: `App Store 校验失败（${res.status}）` };
    const data = await res.json();
    for (const group of data.data ?? []) {
      for (const item of group.lastTransactions ?? []) {
        // 1 = 有效，5 = 宽限期内（扣款失败但还没到期）
        if (item.status !== 1 && item.status !== 5) continue;
        const payload = decodeJwsPayload(item.signedTransactionInfo);
        if (!payload) continue;
        if (payload.productId !== productId) continue;
        if (APPSTORE_BUNDLE_ID && payload.bundleId !== APPSTORE_BUNDLE_ID) continue;
        return { ok: true, expiresAt: payload.expiresDate ?? null };
      }
    }
  }
  return { ok: false, message: '这笔订阅在 App Store 查不到或已失效。' };
}

/** App Store Server API 要求的 ES256 JWT。 */
async function appleJwt(env) {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: 'ES256', kid: env.APPSTORE_KEY_ID, typ: 'JWT' };
  const claims = {
    iss: env.APPSTORE_ISSUER_ID,
    iat: now,
    exp: now + 600,
    aud: 'appstoreconnect-v1',
    bid: env.APPSTORE_BUNDLE_ID ?? 'solutions.aicon.aplomb',
  };
  const b64u = (obj) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const signingInput = `${b64u(header)}.${b64u(claims)}`;

  const pem = env.APPSTORE_PRIVATE_KEY.replace(/-----[^-]+-----/g, '').replace(/\s+/g, '');
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    'pkcs8', der, { name: 'ECDSA', namedCurve: 'P-256' }, false, ['sign'],
  );
  const sig = await crypto.subtle.sign(
    { name: 'ECDSA', hash: 'SHA-256' }, key, new TextEncoder().encode(signingInput),
  );
  const sigB64 = btoa(String.fromCharCode(...new Uint8Array(sig)))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${signingInput}.${sigB64}`;
}

/** 只取 JWS 的 payload —— 签名本身已由苹果的接口背书。 */
function decodeJwsPayload(jws) {
  try {
    const part = String(jws).split('.')[1];
    const b64 = part.replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(decodeURIComponent(escape(atob(b64))));
  } catch {
    return null;
  }
}

// ── 拟稿 ───────────────────────────────────────────────────────────────────

async function draft(request, env) {
  const { acct, token, error } = await loadAccount(request, env);
  if (error) return error;

  const capUsd = TIERS[acct.tier]?.capUsd ?? HARD_CAP_USD;
  if ((acct.bars ?? 0) <= 0 || (acct.spentUsd ?? 0) >= capUsd) {
    return json(
      {
        error: 'battery_empty',
        message: acct.tier
          ? '这个月的电池用完了。可以升一档，或填自己的 API key 继续。'
          : '免费电池用完了。订阅可以每月自动续电，也可以填自己的 API key 继续用。',
        battery: batteryOf({ ...acct, bars: 0 }),
      },
      402,
    );
  }

  const body = await request.json().catch(() => ({}));
  const image = String(body.imageBase64 ?? '');
  const prompt = String(body.prompt ?? '');
  if (!image || image.length > MAX_IMAGE_CHARS) return json({ error: 'bad image' }, 400);
  if (!prompt.trim()) return json({ error: 'empty prompt' }, 400);

  const upstream = await fetch('https://api.anthropic.com/v1/messages', {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      // trim：密钥若是用管道喂进 wrangler（echo 会补换行）会带尾随空白，
      // Anthropic 直接判 invalid x-api-key，排查起来毫无线索。
      'x-api-key': String(env.ANTHROPIC_API_KEY ?? '').trim(),
      'anthropic-version': '2023-06-01',
    },
    body: JSON.stringify({
      model: MODEL,
      max_tokens: MAX_TOKENS,
      messages: [
        {
          role: 'user',
          content: [
            {
              type: 'image',
              source: { type: 'base64', media_type: 'image/jpeg', data: image },
            },
            { type: 'text', text: prompt },
          ],
        },
      ],
    }),
  });

  if (!upstream.ok) {
    const text = await upstream.text().catch(() => '');
    return json({ error: `upstream ${upstream.status}: ${text.slice(0, 200)}` }, 502);
  }

  const out = await upstream.json();
  const text = (out.content ?? [])
    .filter((b) => b.type === 'text')
    .map((b) => b.text)
    .join('');

  // 空回复不扣电 —— 让用户为一份没拿到的稿子付一格,是最不该有的收费。
  // 多半是撞了 max_tokens,把 stop_reason 带出来方便定位。
  if (!text.trim()) {
    return json(
      {
        error: 'empty_draft',
        message: '模型这次没出稿（可能内容太长）。没有扣电，再试一次。',
        stopReason: out.stop_reason ?? null,
        battery: batteryOf(acct),
      },
      502,
    );
  }

  // 扣费：一次成功拟稿一格电，同时记真实美元。失败路径不会走到这里。
  const spend = costUsd(out.usage?.input_tokens ?? 0, out.usage?.output_tokens ?? 0);
  const raw = await env.APLOMB.get(`token:${token}`);
  const fresh = raw ? JSON.parse(raw) : acct;
  fresh.bars = Math.max(0, (fresh.bars ?? BATTERY_BARS) - 1);
  fresh.spentUsd = round6((fresh.spentUsd ?? 0) + spend);
  fresh.drafts = (fresh.drafts ?? 0) + 1;
  fresh.lastUsedAt = new Date().toISOString();
  await env.APLOMB.put(`token:${token}`, JSON.stringify(fresh));

  return json({ text, battery: batteryOf(fresh) });
}
