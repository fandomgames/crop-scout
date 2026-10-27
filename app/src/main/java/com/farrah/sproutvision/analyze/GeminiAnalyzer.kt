package com.farrah.sproutvision.analyze

import android.util.Base64
import com.farrah.sproutvision.data.GrowIntent
import com.farrah.sproutvision.data.GrowthStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Real vision analysis via the Gemini Developer API (gemini-3.5-flash
 * first, gemini-3.8-flash as fallback).
 * Needs an API key from Google AI Studio (https://aistudio.google.com),
 * entered in the app's Settings screen.
 */
private const val TAG = "GeminiAnalyzer"

/**
 * Ordered model chain. 2026-10-24: gemini-3.8-flash 503s or hangs
 * with no response under load while gemini-3.5-flash answers in
 * ~1.3s — so the proven model goes first until 3.8 recovers.
 * (Trying 3.8 first burns the 35s demo scan budget on every scan
 * while the brown-out lasts; flip back when 3.8 is healthy again.)
 */
internal val GEMINI_MODEL_CHAIN = listOf("gemini-3.5-flash", "gemini-3.8-flash")

/**
 * First-attempt read patience. A healthy model sends response
 * headers in ~1s; past this the attempt is a brown-out hang and
 * the scan should fail over instead of staring at "Waiting…".
 */
internal const val FIRST_ATTEMPT_READ_TIMEOUT_S = 25

/**
 * Failover read patience: the backup model gets the full
 * slow-WiFi budget, since legit 70s completions were observed.
 */
internal const val FAILOVER_READ_TIMEOUT_S = 60

internal fun geminiModelUrl(model: String): String =
    "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

/** HTTP codes worth retrying — and failing over to the next model. */
internal fun isTransientGeminiCode(code: Int): Boolean = code == 429 || code == 503

/**
 * Transient 429/503 that survived retries: an IOException so
 * existing callers are unaffected, typed so the model loop can
 * fail over instead of failing the scan.
 */
class TransientGeminiException(val code: Int, message: String) : IOException(message)

class GeminiAnalyzer(
    private val apiKey: String,
    private val growIntent: GrowIntent = GrowIntent.MICROGREEN
) : PlantAnalyzer {

    // Two patience levels: the first model gets an impatient client
    // so a brown-out hang fails over fast; the failover model gets
    // the full slow-WiFi budget. The feed shows the stall live and
    // Cancel drops the call, so a long cap never means a blind wait.
    private val firstAttemptClient = buildClient(FIRST_ATTEMPT_READ_TIMEOUT_S.toLong())
    private val failoverClient = buildClient(FAILOVER_READ_TIMEOUT_S.toLong())

    private fun buildClient(readTimeoutS: Long) = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(readTimeoutS, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    override suspend fun analyze(
        imageBytes: ByteArray,
        varietyHint: String?,
        onEvent: (String) -> Unit
    ): AnalysisResult =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "Gemini API key is missing — add it in Settings." }

            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val bodyJson = JSONObject()
                .put(
                    "contents", JSONArray().put(
                        JSONObject().put(
                            "parts", JSONArray()
                                .put(JSONObject().put("text", buildPrompt(varietyHint)))
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

            executeChain(bodyJson, onEvent)
        }

    /**
     * Tries each model in chain order: a busy or hanging primary
     * fails over to the fallback instead of failing the scan.
     * One shared budget across all models; anything that is not a
     * transient 429/503 or a socket timeout throws immediately.
     */
    private suspend fun executeChain(
        bodyJson: String,
        onEvent: (String) -> Unit
    ): AnalysisResult {
        val startMs = System.currentTimeMillis()
        var lastError: IOException? = null
        for ((index, model) in GEMINI_MODEL_CHAIN.withIndex()) {
            if (lastError != null && System.currentTimeMillis() - startMs > TOTAL_BUDGET_MS) {
                onEvent("Giving up after ${TOTAL_BUDGET_MS / 1000}s…")
                throw lastError
            }
            val attemptClient = if (index == 0) firstAttemptClient else failoverClient
            try {
                return executeWithRetry(buildRequest(model, bodyJson), onEvent, startMs, attemptClient)
            } catch (e: TransientGeminiException) {
                lastError = e
                if (index == GEMINI_MODEL_CHAIN.lastIndex) throw e
                Log.w(TAG, "$model busy (${e.code}), failing over")
                onEvent("$model is busy — trying ${GEMINI_MODEL_CHAIN[index + 1]}…")
            } catch (e: SocketTimeoutException) {
                lastError = e
                if (index == GEMINI_MODEL_CHAIN.lastIndex) throw e
                Log.w(TAG, "$model timed out, failing over", e)
                onEvent("$model timed out — trying ${GEMINI_MODEL_CHAIN[index + 1]}…")
            }
        }
        throw (lastError ?: IOException("Gemini request failed"))
    }

    /**
     * Request for one model in the chain.
     * Auth transport: x-goog-api-key header, not ?key=. The
     * new AQ. auth keys never answer on the query-param
     * transport (verified 2026-10-24: requests hang with no
     * error); the header works for AQ. and legacy AIza keys.
     */
    private fun buildRequest(model: String, bodyJson: String): Request =
        Request.Builder()
            .url(geminiModelUrl(model))
            .addHeader("x-goog-api-key", apiKey)
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

    /**
     * High-demand 429/503s are transient — retry twice with backoff, within
     * a total budget so a stalled backend can't hang the scan forever.
     * Cancellable: dropping the coroutine cancels the in-flight call.
     * @param startMs chain start, shared across models so the total
     * budget spans the whole fallback chain, not one model.
     * @param client impatient for the first model, patient for failover.
     * @throws TransientGeminiException when retries exhaust on 429/503,
     * so the caller can fail over to the next model.
     */
    private suspend fun executeWithRetry(
        request: Request,
        onEvent: (String) -> Unit,
        startMs: Long,
        client: OkHttpClient
    ): AnalysisResult {
        var lastError: IOException? = null
        val backoffs = listOf(2000L, 5000L)
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
                    return parseResult(raw)
                }
                val message = "Gemini API error ${response.code}: ${extractError(raw)}"
                if (!isTransientGeminiCode(response.code)) throw IOException(message)
                lastError = TransientGeminiException(response.code, message)
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

    private fun buildPrompt(varietyHint: String?): String = """
        You are an expert microgreens grower analyzing a photo of a grow tray.
        ${if (!varietyHint.isNullOrBlank()) "The grower believes this tray is: $varietyHint." else ""}
        ${if (growIntent == GrowIntent.GROW_OUT) "This tray is being grown out to full maturity, NOT harvested as microgreens — do not judge it against a microgreen harvest timeline; assess health for a maturing plant." else ""}
        Identify the variety, the current growth stage (exactly one of: SEEDED, GERMINATION, BLACKOUT, LIGHT, HARVEST),
        estimate canopy coverage percent (0-100), an overall health score (0-100),
        list any visible issues (examples: mold, legginess, uneven germination, damping off, dry spots — empty list if none),
        and give one short actionable recommendation. Also report your
        confidence in this analysis as an integer 0-100 ("confidence").
        If no grow tray is visible in the photo (blank wall, darkness, random
        objects), set "trayVisible" to false and "trayBox" to null.
        Reply with ONLY valid JSON, no markdown fences, in exactly this shape:
        {"variety":"...","stage":"LIGHT","healthScore":85,"coveragePercent":90,"issues":["..."],"recommendation":"...","trayVisible":true,"trayBox":[100,120,900,800],"confidence":85}
        "trayBox" is the tray bounding box as [x0,y0,x1,y1] in 0-1000
        normalized image coordinates (origin top-left).
    """.trimIndent()

    private fun parseResult(raw: String): AnalysisResult {
        val text = JSONObject(raw)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0)
            .getString("text")
        val cleaned = text.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val o = JSONObject(cleaned)
        val issues = mutableListOf<String>()
        val arr = o.optJSONArray("issues")
        if (arr != null) {
            for (i in 0 until arr.length()) issues += arr.optString(i)
        }
        val boxArr = o.optJSONArray("trayBox")
        val box = if (boxArr != null && boxArr.length() == 4) {
            TrayBox(boxArr.optInt(0), boxArr.optInt(1), boxArr.optInt(2), boxArr.optInt(3))
        } else null
        return AnalysisResult(
            variety = o.optString("variety", "Unknown"),
            stage = GrowthStage.fromName(o.optString("stage", "LIGHT")),
            healthScore = o.optInt("healthScore", 0).coerceIn(0, 100),
            issues = issues,
            coveragePercent = o.optInt("coveragePercent", 0).coerceIn(0, 100),
            recommendation = o.optString("recommendation", ""),
            isDemo = false,
            trayVisible = o.optBoolean("trayVisible", true),
            trayBox = box,
            confidence = o.optInt("confidence", 0).coerceIn(0, 100)
        )
    }

    private fun extractError(raw: String): String = try {
        JSONObject(raw).optJSONObject("error")?.optString("message") ?: raw.take(200)
    } catch (_: Exception) {
        raw.take(200)
    }
}
