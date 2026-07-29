package solutions.aicon.zuiti.ai

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal Gemini image generation — mirrors the extension's
 * generateGeminiImage (providers.ts). Blocking; call from Dispatchers.IO.
 */
object GeminiClient {
    private val client = OkHttpClient.Builder().callTimeout(120, TimeUnit.SECONDS).build()
    private val JSON = "application/json".toMediaType()

    /**
     * Vision Q&A on the free tier: image + prompt → text answer
     * (non-streaming generateContent, same endpoint family as image gen).
     */
    fun visionText(
        apiKey: String,
        imageBase64: String,
        prompt: String,
        model: String = "gemini-3.5-flash",
    ): String {
        val payload = JSONObject().put(
            "contents",
            JSONArray().put(
                JSONObject().put("role", "user").put(
                    "parts",
                    JSONArray()
                        .put(JSONObject().put("inlineData",
                            JSONObject().put("mimeType", "image/jpeg").put("data", imageBase64)))
                        .put(JSONObject().put("text", prompt)),
                )
            )
        )
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
            .addHeader("content-type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: error("Gemini: empty response")
            if (!resp.isSuccessful) error("Gemini ${resp.code}: ${txt.take(200)}")
            val parts = JSONObject(txt)
                .optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?: error("Gemini: no content returned")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                // Gemini 3.x interleaves thought parts — answer text only.
                if (part.optBoolean("thought")) continue
                sb.append(part.optString("text"))
            }
            return sb.toString().ifBlank { "(no text)" }
        }
    }

    /** Text-only Q&A — for PROCESS_TEXT / clipboard text actions. */
    fun textOnly(apiKey: String, prompt: String, model: String = "gemini-3.5-flash"): String {
        val payload = JSONObject().put(
            "contents",
            JSONArray().put(
                JSONObject().put("role", "user").put(
                    "parts", JSONArray().put(JSONObject().put("text", prompt))
                )
            )
        )
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
            .addHeader("content-type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: error("Gemini: empty response")
            if (!resp.isSuccessful) error("Gemini ${resp.code}: ${txt.take(200)}")
            val parts = JSONObject(txt)
                .optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?: error("Gemini: no content returned")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                if (part.optBoolean("thought")) continue
                sb.append(part.optString("text"))
            }
            return sb.toString().ifBlank { "(no text)" }
        }
    }

    /** Returns decoded PNG/JPEG bytes, or throws with a readable message. */
    // gemini-2.5-flash-image shuts down 2026-08-17 — Nano Banana 2 is the successor.
    fun generateImage(apiKey: String, prompt: String, model: String = "gemini-3.1-flash-image"): ByteArray {
        val payload = JSONObject().put(
            "contents",
            JSONArray().put(
                JSONObject().put("role", "user").put(
                    "parts", JSONArray().put(JSONObject().put("text", prompt))
                )
            )
        )
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
            .addHeader("content-type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: error("Gemini: empty response")
            if (!resp.isSuccessful) error("Gemini ${resp.code}: ${txt.take(200)}")
            val parts = JSONObject(txt)
                .optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?: error("Gemini: no content returned")
            for (i in 0 until parts.length()) {
                val data = parts.optJSONObject(i)?.optJSONObject("inlineData")?.optString("data")
                if (!data.isNullOrEmpty()) return Base64.decode(data, Base64.DEFAULT)
            }
            // No image part — surface any text the model returned instead.
            for (i in 0 until parts.length()) {
                val t = parts.optJSONObject(i)?.optString("text")
                if (!t.isNullOrEmpty()) error("Gemini returned text only: ${t.take(120)}")
            }
            error("Gemini: no image returned")
        }
    }
}
