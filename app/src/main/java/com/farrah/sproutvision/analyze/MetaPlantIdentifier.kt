package com.farrah.sproutvision.analyze

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Wild-plant identification via Muse Spark on the Meta Model API.
 *
 * Transport mirrors [MetaAnalyzer] (same model, endpoint, Bearer auth,
 * json_object response at choices[0].message.content); the prompt and
 * result shape are the shared wild-ID ones ([buildIdentifyPrompt] /
 * [parsePlantIdentification]), so the card renders identically whatever
 * ran. Model/endpoint verification status lives on [MetaAnalyzer] —
 * this file follows it and must not drift.
 *
 * Failures (HTTP error, bad envelope, bad JSON) throw — the caller
 * surfaces the error and never substitutes a fake identification.
 *
 * @param apiKey Meta Model API key, passed in from Settings (never hardcoded).
 */
class MetaPlantIdentifier(private val apiKey: String) : PlantIdentifier {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    override suspend fun identify(
        imageBytes: ByteArray,
        onEvent: (String) -> Unit
    ): PlantIdentification =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "Meta Model API key is missing — add it in Settings." }

            onEvent("Waiting on Meta AI…")
            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val bodyJson = JSONObject()
                .put("model", MetaAnalyzer.DEFAULT_MODEL)
                .put(
                    "messages", JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put(
                                "content", JSONArray()
                                    .put(JSONObject().put("type", "text").put("text", buildIdentifyPrompt()))
                                    .put(
                                        JSONObject().put("type", "image_url").put(
                                            "image_url", JSONObject()
                                                .put("url", "data:image/jpeg;base64,$base64")
                                        )
                                    )
                            )
                    )
                )
                .put("response_format", JSONObject().put("type", "json_object"))
                .toString()

            val request = Request.Builder()
                .url("${MetaAnalyzer.BASE_URL}/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Meta Model API error ${response.code}: ${raw.take(300)}")
                }
                onEvent("Parsing response…")
                parseResponse(raw)
            }
        }

    private fun parseResponse(raw: String): PlantIdentification {
        val text = JSONObject(raw)
            .getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
        return parsePlantIdentification(text).copy(source = IdentifySource.META)
    }
}
