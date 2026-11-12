package com.brianrobinson.fonds.ai

import android.util.Base64
import com.brianrobinson.fonds.data.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional cloud engine (Anthropic Messages API) for pages on-device models can't read well,
 * mostly handwriting. Only used when you add an API key, and only for the items routed to it.
 */
class ClaudeEngine {

    data class Model(val id: String, val label: String, val note: String)

    class ClaudeException(message: String) : Exception(message)

    suspend fun generate(
        apiKey: String,
        model: String,
        prompt: String,
        imagePaths: List<String> = emptyList(),
        maxTokens: Int = 1500,
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw ClaudeException("Add a Claude API key in Settings first.")
        val content = JSONArray()
        imagePaths.forEach { path ->
            val bytes = ImageStore.jpegBytes(path, maxEdge = 1568, quality = 85) ?: return@forEach
            content.put(
                JSONObject()
                    .put("type", "image")
                    .put(
                        "source",
                        JSONObject()
                            .put("type", "base64")
                            .put("media_type", "image/jpeg")
                            .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)),
                    ),
            )
        }
        content.put(JSONObject().put("type", "text").put("text", prompt))

        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))

        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", apiKey.trim())
            setRequestProperty("anthropic-version", "2023-06-01")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }
                    .getOrDefault(text.take(300))
                throw ClaudeException(friendly(code, msg))
            }
            val json = JSONObject(text)
            val parts = json.optJSONArray("content") ?: JSONArray()
            buildString {
                for (i in 0 until parts.length()) {
                    val p = parts.getJSONObject(i)
                    if (p.optString("type") == "text") append(p.optString("text"))
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun test(apiKey: String, model: String): String =
        generate(apiKey, model, "Reply with the single word: ready", maxTokens = 10).trim()

    private fun friendly(code: Int, msg: String): String = when (code) {
        401 -> "Claude rejected the API key. Check it in Settings."
        403 -> "This API key isn't allowed to use that model."
        404 -> "Model not found. Pick another model in Settings. ($msg)"
        413 -> "The scans were too large to send in one request."
        429 -> "Claude rate limit reached. Wait a minute and retry."
        in 500..599 -> "Claude is having trouble right now. Retry shortly."
        else -> "Claude error $code: $msg"
    }

    companion object {
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"

        val MODELS = listOf(
            Model("claude-haiku-5-5", "Haiku 5.5", "Fastest and cheapest; fine for clear typescript"),
            Model("claude-sonnet-5-5", "Sonnet 5.5", "Best balance; strong on handwriting"),
            Model("claude-opus-5-5", "Opus 5.5", "Most careful; for the hardest hands"),
        )

        const val MAX_IMAGES = 20
    }
}
