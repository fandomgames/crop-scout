package com.farrah.sproutvision.analyze

import android.util.Base64
import android.util.Log
import com.farrah.sproutvision.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Identifies a wild plant from a photo. Deliberately separate from
 * [PlantAnalyzer] (tray growing analysis): different prompt, different
 * result, and it never touches tray data, scan history, or the scorecard.
 */
interface PlantIdentifier {
    /**
     * [onEvent] receives live progress notes for the analyzing feed, so a
     * stall is visible while it happens.
     */
    suspend fun identify(
        imageBytes: ByteArray,
        onEvent: (String) -> Unit = {}
    ): PlantIdentification
}

/**
 * Scripted identifier used in demo mode (or with no API key): the sample
 * photo stands in for a real capture, so the result is a labeled demo
 * card at low confidence — a guess is never presented as certain.
 */
class DemoPlantIdentifier : PlantIdentifier {
    override suspend fun identify(
        imageBytes: ByteArray,
        onEvent: (String) -> Unit
    ): PlantIdentification {
        onEvent("Running scripted identification…")
        delay(1_200) // simulate network/vision latency
        return PlantIdentification(
            commonName = "Purple Coneflower",
            scientificName = "Echinacea purpurea",
            family = "Asteraceae",
            confidence = IdentifyConfidence.LOW,
            care = PlantCareSheet(
                sun = "full sun",
                water = "Medium; drought-tolerant once established.",
                soil = "Average, well-drained loam.",
                hardinessZones = "3–9",
                bloomTime = "Midsummer to fall",
                matureSize = "2–4 ft tall",
                funFact = "Goldfinches love the seed heads — leave them standing through winter."
            ),
            betterPhotoTip = "Get closer to the flower head and capture the leaf arrangement.",
            isDemo = true,
            source = IdentifySource.DEMO
        )
    }
}

/**
 * Identifier order (hackathon), same as tray scan: Demo (if demoMode) >
 * Meta (if useMetaAi + Meta key present) > Gemini (if Gemini key
 * present) > Demo. A blank Gemini key does NOT force demo when a Meta
 * key is present.
 */
fun selectPlantIdentifier(s: AppSettings): PlantIdentifier = when {
    s.demoMode -> DemoPlantIdentifier()
    s.useMetaAi && s.metaApiKey.isNotBlank() -> MetaPlantIdentifier(s.metaApiKey)
    s.geminiApiKey.isNotBlank() -> GeminiPlantIdentifier(s.geminiApiKey)
    else -> DemoPlantIdentifier()
}

/**
 * Real wild-plant identification via the Gemini Developer API
 * (gemini-3.8-flash). Needs an API key from Google AI Studio
 * (https://aistudio.google.com), entered in the app's Settings screen.
 *
 * Self-contained HTTP plumbing (mirrored from the tray analyzer, not
 * shared) so the tray path stays untouched.
 */
private const val TAG = "GeminiPlantIdentifier"

class GeminiPlantIdentifier(private val apiKey: String) : PlantIdentifier {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    override suspend fun identify(
        imageBytes: ByteArray,
        onEvent: (String) -> Unit
    ): PlantIdentification =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "Gemini API key is missing — add it in Settings." }

            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val bodyJson = JSONObject()
                .put(
                    "contents", JSONArray().put(
                        JSONObject().put(
                            "parts", JSONArray()
                                .put(JSONObject().put("text", buildIdentifyPrompt()))
                                .put(
                                    JSONObject().put(
                                        "inline_data", JSONObject()
                                            .put("mime_type", "image/jpeg")
                                            .put("data", base64)
                                    )
                                )
                        )
                    )
                )
                .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
                .toString()

            // Auth transport: x-goog-api-key header, not ?key=. The
            // new AQ. auth keys never answer on the query-param
            // transport (verified 2026-10-24: requests hang with no
            // error); the header works for AQ. and legacy AIza keys.
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent")
                .addHeader("x-goog-api-key", apiKey)
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            executeWithRetry(request, onEvent)
        }

    /**
     * High-demand 429/503s are transient — retry twice with backoff, within
     * a total budget so a stalled backend can't hang the ID forever.
     * Cancellable: dropping the coroutine cancels the in-flight call.
     */
    private suspend fun executeWithRetry(
        request: Request,
        onEvent: (String) -> Unit
    ): PlantIdentification {
        var lastError: IOException? = null
        val backoffs = listOf(2000L, 5000L)
        val startMs = System.currentTimeMillis()
        for (attempt in 0..backoffs.size) {
            if (attempt > 0 && System.currentTimeMillis() - startMs > TOTAL_BUDGET_MS) {
                onEvent("Giving up after ${TOTAL_BUDGET_MS / 1000}s…")
                throw (lastError ?: IOException("Gemini request failed"))
            }
            onEvent(if (attempt == 0) "Waiting on Gemini…" else "Retrying (attempt ${attempt + 1})…")
            client.newCall(request).await().use { response ->
                val raw = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    onEvent("Parsing response…")
                    return parseResponse(raw)
                }
                val error = IOException(
                    "Gemini API error ${response.code}: ${extractError(raw)}"
                )
                lastError = error
                if (response.code != 429 && response.code != 503) throw error
            }
            if (attempt < backoffs.size) {
                val waitMs = backoffs[attempt]
                val code = lastError?.message
                    ?.substringAfter("error ")?.substringBefore(":")?.ifBlank { "busy" } ?: "busy"
                onEvent("Server $code — retry in ${waitMs / 1000}s…")
                Log.w(TAG, "transient API error, retry ${attempt + 1} in ${waitMs}ms")
                delay(waitMs)
            }
        }
        throw (lastError ?: IOException("Gemini request failed"))
    }

    /** Total retry budget: attempts past this point give up immediately. */
    private companion object {
        const val TOTAL_BUDGET_MS = 120_000L
    }

    /** Awaiting OkHttp call that honors coroutine cancellation. */
    private suspend fun Call.await(): Response =
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { try { cancel() } catch (_: Exception) {} }
            enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    cont.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (!cont.isCompleted) cont.resumeWithException(e)
                }
            })
        }

    private fun parseResponse(raw: String): PlantIdentification {
        val text = JSONObject(raw)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0)
            .getString("text")
        return parsePlantIdentification(text).copy(source = IdentifySource.GEMINI)
    }

    private fun extractError(raw: String): String = try {
        JSONObject(raw).optJSONObject("error")?.optString("message") ?: raw.take(200)
    } catch (_: Exception) {
        raw.take(200)
    }
}
