package com.actuallyrizzn.voicekb

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Thin Venice HTTP client for this app. Matches the public API used by
 * the Venice AI HTTP API (`GET /models`, `POST /chat/completions`), same surface as the `venice-ai-sdk` package.
 *
 * `GET /models` works without an API key and returns `model_spec.pricing` (usd / diem) for ranking.
 */
object VeniceApi {

    const val DEFAULT_BASE_URL = "https://api.venice.ai/api/v1"

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun listTextModels(baseUrl: String = DEFAULT_BASE_URL): List<VeniceTextModel> {
        val root = baseUrl.trimEnd('/')
        val req = Request.Builder()
            .url("$root/models")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Venice models HTTP ${resp.code}: ${body.take(200)}")
            }
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: JSONArray()
            val out = ArrayList<VeniceTextModel>()
            for (i in 0 until data.length()) {
                val o = data.optJSONObject(i) ?: continue
                if (o.optString("type", "") != "text") continue
                val id = o.optString("id", "")
                if (id.isEmpty()) continue
                val spec = o.optJSONObject("model_spec")
                val name = o.optString("name", "").ifEmpty { null }
                val desc = o.optString("description", "").ifEmpty { null }
                    ?: spec?.optString("modelSource", "")?.ifEmpty { null }
                val score = pricingScoreUsd(spec)
                out.add(VeniceTextModel(id = id, displayName = name, description = desc, pricingScore = score))
            }
            out.sortBy { it.pricingScore }
            return out
        }
    }

    private fun pricingScoreUsd(spec: JSONObject?): Double {
        if (spec == null) return Double.MAX_VALUE
        val pricing = spec.optJSONObject("pricing") ?: return Double.MAX_VALUE
        val input = pricing.optJSONObject("input")?.optDouble("usd", Double.NaN) ?: Double.NaN
        val output = pricing.optJSONObject("output")?.optDouble("usd", Double.NaN) ?: Double.NaN
        if (input.isNaN() && output.isNaN()) return Double.MAX_VALUE
        return (if (input.isNaN()) 0.0 else input) + (if (output.isNaN()) 0.0 else output)
    }

    fun chatCompletion(
        baseUrl: String = DEFAULT_BASE_URL,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double = 0.2,
        maxCompletionTokens: Int = 512,
    ): String {
        val root = baseUrl.trimEnd('/')
        val bodyJson = JSONObject().apply {
            put("model", model)
            put("temperature", temperature)
            put("max_completion_tokens", maxCompletionTokens)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", userPrompt))
                },
            )
            put(
                "venice_parameters",
                JSONObject().apply {
                    put("strip_thinking_response", true)
                    put("disable_thinking", true)
                },
            )
        }
        val req = Request.Builder()
            .url("$root/chat/completions")
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .post(bodyJson.toString().toRequestBody(jsonMedia))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Venice chat HTTP ${resp.code}: ${body.take(300)}")
            }
            val json = JSONObject(body)
            val choices = json.optJSONArray("choices") ?: throw IllegalStateException("No choices in response")
            val first = choices.optJSONObject(0) ?: throw IllegalStateException("Empty choices")
            val message = first.optJSONObject("message") ?: throw IllegalStateException("No message")
            val content = message.opt("content")
            return when (content) {
                is String -> content.trim()
                else -> content?.toString()?.trim().orEmpty()
            }
        }
    }
}
