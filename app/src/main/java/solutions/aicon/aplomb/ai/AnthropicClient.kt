package solutions.aicon.aplomb.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal Anthropic Messages call with a vision (image) input — mirrors
 * the browser extension's callAnthropic. Blocking; call from Dispatchers.IO.
 */
object AnthropicClient {
    private val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private val JSON = "application/json".toMediaType()

    /** Shared vision prompts — used by whichever provider is active. */
    const val IDENTIFY_PROMPT =
        "Identify the single product shown in this image. Reply with ONLY a concise shopping search query — brand + product name + key attribute (e.g. \"Seven Minerals aloe vera gel 12oz\"). 3-8 words, no punctuation, no quotes, no explanation. If it is not obviously a buyable product, still return the best short search term for the main object."
    const val DESCRIBE_PROMPT =
        "Look at this image carefully. Write a detailed, vivid image-generation prompt (English, one paragraph, 60\u2013120 words) that captures the subject, composition, style, colours, lighting, mood, and any distinctive details. The prompt should be usable in Midjourney / DALL-E / Gemini image generation. Do NOT prefix with 'a prompt for' — just write the prompt itself."


    /** Prompt comes from the caller (localized via string resources). */
    fun explain(apiKey: String, imageBase64: String, prompt: String, model: String = "claude-haiku-4-5"): String =
        visionCall(apiKey, model, imageBase64, prompt)

    /**
     * Name the main object as a short search/shopping query — same prompt
     * as the extension service worker's shoppingQueryFromImage.
     */
    fun identify(apiKey: String, imageBase64: String, model: String = "claude-haiku-4-5"): String =
        visionCall(apiKey, model, imageBase64, IDENTIFY_PROMPT)
            .trim().removeSurrounding("\"").removeSurrounding("'").replace(Regex("\\s+"), " ").trim()

    /**
     * Write a vivid image-generation prompt from the crop — same prompt and
     * model tier (Sonnet, vision quality matters) as the extension's
     * generatePromptFromImage.
     */
    fun describeForGeneration(apiKey: String, imageBase64: String, model: String = "claude-sonnet-5"): String =
        visionCall(apiKey, model, imageBase64, DESCRIBE_PROMPT).trim()

    /** Text-only call — for PROCESS_TEXT / clipboard text actions. */
    fun textCall(apiKey: String, prompt: String, model: String = "claude-haiku-4-5"): String {
        val payload = JSONObject()
            .put("model", model)
            .put("max_tokens", 600)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: return "(empty response)"
            if (!resp.isSuccessful) return "Anthropic ${resp.code}: ${txt.take(160)}"
            val arr = JSONObject(txt).optJSONArray("content") ?: return "(no content)"
            val sb = StringBuilder()
            for (i in 0 until arr.length()) {
                val b = arr.getJSONObject(i)
                if (b.optString("type") == "text") sb.append(b.optString("text"))
            }
            return sb.toString().ifBlank { "(no text)" }
        }
    }

    private fun visionCall(apiKey: String, model: String, imageBase64: String, prompt: String): String {
        val content = JSONArray()
            .put(JSONObject().put("type", "image").put("source",
                JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", imageBase64)))
            .put(JSONObject().put("type", "text").put("text", prompt))
        val payload = JSONObject()
            .put("model", model)
            .put("max_tokens", 400)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: return "(empty response)"
            if (!resp.isSuccessful) return "Anthropic ${resp.code}: ${txt.take(160)}"
            val arr = JSONObject(txt).optJSONArray("content") ?: return "(no content)"
            val sb = StringBuilder()
            for (i in 0 until arr.length()) {
                val b = arr.getJSONObject(i)
                if (b.optString("type") == "text") sb.append(b.optString("text"))
            }
            return sb.toString().ifBlank { "(no text)" }
        }
    }
}
