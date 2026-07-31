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
 * 用商店的购买凭据换本月的电池。苹果和 Google 都走这一个入口。
 *
 * 验签一律交给商店官方接口：苹果报 transactionId 给 App Store Server API，
 * Google 报 purchaseToken 给 Play Developer API，由商店告诉我们这笔订阅当前
 * 是不是有效。**没配好密钥时一律拒绝发电** —— 宁可用户暂时买不了，也不能
 * 因为「先信着」被人白嫖算力。
 *
 * 两边商品 id 是同一套（aplomb.sub.*），所以只有验签这一步分平台，档位表和
 * 发电逻辑完全共用。
 */
async function subscribe(request, env) {
  const { acct, token, error } = await loadAccount(request, env);
  if (error) return error;

  const body = await request.json().catch(() => ({}));
  const productId = String(body.productId ?? '');
  const store = String(body.store ?? 'appstore');
  // 苹果给 transactionId，Google 给 purchaseToken
  const receipt = String(body.transactionId ?? body.purchaseToken ?? '');
  const tier = TIERS[productId];
  if (!tier || !receipt) return json({ error: 'bad product' }, 400);

  // 测试通道：拿得到 DEV_GRANT_KEY（wrangler secret）才放行。
  // 存在的理由是 App Store Connect 那套建起来之前也得能验完整链路；
  // 用密钥而不是「debug 开关」把门，是因为开关一旦忘记关就是无限白嫖。
  const devKey = String(body.devKey ?? '');
  const isDev = env.DEV_GRANT_KEY && devKey && devKey === env.DEV_GRANT_KEY;

  let verdict = { ok: true, expiresAt: null };
  if (!isDev) {
    verdict = store === 'play'
      ? await verifyWithGoogle(env, receipt, productId)
      : await verifyWithApple(env, receipt, productId);
    if (!verdict.ok) {
      return json({ error: 'verify_failed', message: verdict.message }, 402);
    }
  } else {
    // 测试授权按 30 天算一个周期，好观察到期与续期行为
    verdict = { ok: true, expiresAt: Date.now() + 30 * 86400_000 };
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

// ── Google Play 校验 ──────────────────────────────────────────────────────

/**
 * 用 Play Developer API 查这笔订阅当前是否有效。
 *
 * 和苹果的差别：Google 不接受直接用 service account 的 JWT 调业务接口，
 * 得先拿 JWT 去 oauth2 换一个 access token，再拿 token 调 API。多一跳。
 *
 * 需要的 secret：
 *   GOOGLE_SA_EMAIL       service account 邮箱
 *   GOOGLE_SA_PRIVATE_KEY 那把 PEM 私钥（JSON 凭据里的 private_key 字段）
 *   ANDROID_PACKAGE       solutions.aicon.aplomb
 */
async function verifyWithGoogle(env, purchaseToken, productId) {
  const { GOOGLE_SA_EMAIL, GOOGLE_SA_PRIVATE_KEY, ANDROID_PACKAGE } = env;
  if (!GOOGLE_SA_EMAIL || !GOOGLE_SA_PRIVATE_KEY) {
    // 失败关闭，和苹果那条路一致。
    return { ok: false, message: '服务端还没配好 Google Play 校验密钥，暂时无法开通。' };
  }
  const pkg = ANDROID_PACKAGE || 'solutions.aicon.aplomb';

  const accessToken = await googleAccessToken(env);
  if (!accessToken) return { ok: false, message: 'Google 授权失败，稍后再试。' };

  const res = await fetch(
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(pkg)}` +
      `/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`,
    { headers: { authorization: `Bearer ${accessToken}` } },
  );
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    return { ok: false, message: `Google Play 校验失败（${res.status}）${text.slice(0, 120)}` };
  }
  const data = await res.json();

  // SUBSCRIPTION_STATE_ACTIVE 正常，_IN_GRACE_PERIOD 是扣款失败但还没断服务。
  const state = data.subscriptionState;
  if (state !== 'SUBSCRIPTION_STATE_ACTIVE' && state !== 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD') {
    return { ok: false, message: '这笔订阅在 Google Play 查不到或已失效。' };
  }
  // 一个 token 下可能挂多个 line item（升降档时），认我们要的那个商品。
  const line = (data.lineItems ?? []).find((l) => l.productId === productId);
  if (!line) return { ok: false, message: '这笔订阅和所选套餐不匹配。' };

  const expiresAt = line.expiryTime ? Date.parse(line.expiryTime) : null;
  return { ok: true, expiresAt: Number.isFinite(expiresAt) ? expiresAt : null };
}

/** service account JWT → OAuth2 access token（RS256，和苹果的 ES256 不是一套）。 */
async function googleAccessToken(env) {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: 'RS256', typ: 'JWT' };
  const claims = {
    iss: env.GOOGLE_SA_EMAIL,
    scope: 'https://www.googleapis.com/auth/androidpublisher',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  };
  const b64u = (obj) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const signingInput = `${b64u(header)}.${b64u(claims)}`;

  // JSON 凭据里的 private_key 带字面量 \n，直接喂给 atob 会炸。
  const pem = String(env.GOOGLE_SA_PRIVATE_KEY)
    .replace(/\\n/g, '\n')
    .replace(/-----[^-]+-----/g, '')
    .replace(/\s+/g, '');
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle
    .importKey('pkcs8', der, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign'])
    .catch(() => null);
  if (!key) return null;

  const sig = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(signingInput),
  );
  const sigB64 = btoa(String.fromCharCode(...new Uint8Array(sig)))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const assertion = `${signingInput}.${sigB64}`;

  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion,
    }),
  });
  if (!res.ok) return null;
  const out = await res.json().catch(() => ({}));
  return out.access_token ?? null;
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
