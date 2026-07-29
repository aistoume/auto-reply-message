package solutions.aicon.aplomb.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible chat-completions vision call — mirrors the browser
 * extension's callOpenAI. One client serves both api.openai.com (GPT)
 * and openrouter.ai (free models) via `baseUrl`. Blocking; call from
 * Dispatchers.IO.
 */
object OpenAIClient {
    private val client = OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).build()
    private val JSON = "application/json".toMediaType()

    const val OPENAI_BASE = "https://api.openai.com/v1"
    const val OPENROUTER_BASE = "https://openrouter.ai/api/v1"

    /** Text-only chat call — for PROCESS_TEXT / clipboard text actions. */
    fun textOnly(
        apiKey: String,
        prompt: String,
        model: String,
        baseUrl: String = OPENAI_BASE,
    ): String {
        val payload = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", prompt)),
            )
        if (baseUrl.contains("api.openai.com")) payload.put("max_completion_tokens", 4096)
        else payload.put("max_tokens", 800)
        val req = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("authorization", "Bearer $apiKey")
            .addHeader("content-type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: return "(empty response)"
            if (!resp.isSuccessful) return "OpenAI-compat ${resp.code}: ${txt.take(200)}"
            val choice = JSONObject(txt).optJSONArray("choices")?.optJSONObject(0)
                ?: return "(no choices)"
            return choice.optJSONObject("message")?.optString("content")
                ?.ifBlank { null } ?: "(no text)"
        }
    }

    fun visionText(
        apiKey: String,
        imageBase64: String,
        prompt: String,
        model: String,
        baseUrl: String = OPENAI_BASE,
    ): String {
        val content = JSONArray()
            .put(
                JSONObject().put("type", "image_url").put(
                    "image_url",
                    JSONObject().put("url", "data:image/jpeg;base64,$imageBase64"),
                )
            )
            .put(JSONObject().put("type", "text").put("text", prompt))
        val payload = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", content)),
            )
        // GPT-5.x on api.openai.com rejects max_tokens; the replacement
        // max_completion_tokens also covers hidden reasoning tokens, so it
        // needs headroom. OpenRouter still speaks classic max_tokens.
        if (baseUrl.contains("api.openai.com")) payload.put("max_completion_tokens", 4096)
        else payload.put("max_tokens", 800)
        val req = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("authorization", "Bearer $apiKey")
            .addHeader("content-type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: return "(empty response)"
            if (!resp.isSuccessful) return "OpenAI-compat ${resp.code}: ${txt.take(200)}"
            val choice = JSONObject(txt).optJSONArray("choices")?.optJSONObject(0)
                ?: return "(no choices)"
            return choice.optJSONObject("message")?.optString("content")
                ?.ifBlank { null } ?: "(no text)"
        }
    }
}
