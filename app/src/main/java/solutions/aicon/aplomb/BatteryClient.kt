package solutions.aicon.aplomb

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 免费电池客户端 —— 让没有 API key 的人也能直接用。
 *
 * 之前 Android 版是纯 BYOK：装上就要求你先去 Anthropic 开个账号搞把
 * key，绝大多数人在第一屏就走了。现在首次启动匿名换一个令牌，送 20 格，
 * 拟稿走我们的服务器代付。
 *
 * 填了自己的 key 就完全绕开这里，请求从手机直连模型厂商，我们看不到。
 */
object BatteryClient {

    private const val BASE = "https://aicon.solutions/api/aplomb"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        // 一次视觉拟稿要十几秒，默认 10 秒读超时会把成功的请求掐掉。
        .callTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /** 电池状态。[tier] 非空表示订阅中。 */
    class Battery(
        val bars: Int,
        val barsTotal: Int,
        val tier: String?,
        val renewsAt: String?,
    ) {
        val subscribed get() = !tier.isNullOrBlank()

        companion object {
            fun fromJson(o: JSONObject?) = o?.let {
                Battery(
                    bars = it.optInt("bars", 0),
                    barsTotal = it.optInt("barsTotal", 20),
                    tier = it.optString("tier").takeIf { s -> s.isNotBlank() && s != "null" },
                    renewsAt = it.optString("renewsAt").takeIf { s -> s.isNotBlank() && s != "null" },
                )
            }
        }
    }

    /** 拟稿结果 —— 要么有正文，要么有一句给用户看的失败原因。 */
    class DraftResult(val text: String?, val battery: Battery?, val error: String?)

    // ── 令牌 ──────────────────────────────────────────────────────────

    /**
     * 拿令牌。已经有就直接返回，没有就用匿名设备 id 去换。
     * 同一个设备 id 重复 claim 不会重发电池（服务端按 deviceId 去重）。
     */
    fun ensureToken(c: Context): String? {
        Prefs.batteryToken(c).takeIf { it.isNotBlank() }?.let { return it }
        val body = JSONObject().put("deviceId", Prefs.deviceId(c)).toString()
        val o = post("$BASE/claim", body, token = null) ?: return null
        val token = o.optString("token").takeIf { it.isNotBlank() } ?: return null
        Prefs.setBatteryToken(c, token)
        return token
    }

    fun battery(c: Context): Battery? {
        val token = ensureToken(c) ?: return null
        val req = Request.Builder().url("$BASE/battery")
            .header("authorization", "Bearer $token").get().build()
        return Battery.fromJson(exec(req)?.optJSONObject("battery"))
    }

    // ── 拟稿 ──────────────────────────────────────────────────────────

    fun draft(c: Context, imageB64: String, prompt: String): DraftResult {
        val token = ensureToken(c)
            ?: return DraftResult(null, null, c.getString(R.string.battery_offline))
        val body = JSONObject()
            .put("imageBase64", imageB64)
            .put("prompt", prompt)
            .toString()
        val req = Request.Builder().url("$BASE/draft")
            .header("authorization", "Bearer $token")
            .post(body.toRequestBody(JSON)).build()

        val (o, code) = execWithCode(req)
            ?: return DraftResult(null, null, c.getString(R.string.battery_offline))
        val batt = Battery.fromJson(o.optJSONObject("battery"))

        // 服务端把「没出稿」和「没电了」分得很清楚，两种都不扣电，
        // 但给用户的下一步动作完全不同，所以原样把 message 透出来。
        if (code == 402) return DraftResult(null, batt, o.optString("message").ifBlank {
            c.getString(R.string.battery_empty)
        })
        val text = o.optString("text").takeIf { it.isNotBlank() }
            ?: return DraftResult(null, batt, o.optString("message").ifBlank {
                c.getString(R.string.card_failed)
            })
        return DraftResult(text, batt, null)
    }

    // ── 订阅上报 ──────────────────────────────────────────────────────

    /**
     * 把 Play 的购买凭据交给服务器换电池。
     * 校验在服务端做（客户端说自己订阅了不算数）。
     */
    fun reportPlayPurchase(c: Context, productId: String, purchaseToken: String): Battery? {
        val token = ensureToken(c) ?: return null
        val body = JSONObject()
            .put("store", "play")
            .put("productId", productId)
            .put("purchaseToken", purchaseToken)
            .toString()
        val req = Request.Builder().url("$BASE/subscribe")
            .header("authorization", "Bearer $token")
            .post(body.toRequestBody(JSON)).build()
        return Battery.fromJson(exec(req)?.optJSONObject("battery"))
    }

    // ── HTTP ─────────────────────────────────────────────────────────

    private fun post(url: String, body: String, token: String?): JSONObject? {
        val b = Request.Builder().url(url).post(body.toRequestBody(JSON))
        if (token != null) b.header("authorization", "Bearer $token")
        return exec(b.build())
    }

    private fun exec(req: Request): JSONObject? = execWithCode(req)?.first

    /**
     * 返回 (JSON, HTTP 状态码)。
     *
     * 重试只针对**解析不出 JSON** 的响应：Cloudflare 偶尔会把请求路由到同域名
     * 下的静态站，回一段纯文本 404。业务错误（401/402）是明确答复，不重试。
     */
    private fun execWithCode(req: Request, attempts: Int = 3): Pair<JSONObject, Int>? {
        repeat(attempts) { i ->
            val parsed = runCatching {
                http.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    JSONObject(raw) to resp.code
                }
            }.getOrNull()
            if (parsed != null) return parsed
            if (i < attempts - 1) Thread.sleep(400L * (i + 1))
        }
        return null
    }
}
