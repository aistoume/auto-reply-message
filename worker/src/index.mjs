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

/** 一块新电池的格数。按每次约 $0.009 估，20 格 ≈ $0.18。 */
const BATTERY_BARS = 20;
/** 每个设备的美元硬上限 —— 格数用完前先撞到它就停。 */
const HARD_CAP_USD = 0.35;

const MAX_TOKENS = 700;
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
});

export default {
  async fetch(request, env) {
    if (request.method === 'OPTIONS') return new Response(null, { headers: CORS });
    const { pathname } = new URL(request.url);
    try {
      if (pathname === '/api/aplomb/claim') return await claim(request, env);
      if (pathname === '/api/aplomb/draft') return await draft(request, env);
      if (pathname === '/api/aplomb/battery') return await battery(request, env);
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

// ── 拟稿 ───────────────────────────────────────────────────────────────────

async function draft(request, env) {
  const { acct, token, error } = await loadAccount(request, env);
  if (error) return error;

  if ((acct.bars ?? 0) <= 0 || (acct.spentUsd ?? 0) >= HARD_CAP_USD) {
    return json(
      {
        error: 'battery_empty',
        message: '免费电池用完了。在设置里填自己的 API key 可以继续用。',
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
