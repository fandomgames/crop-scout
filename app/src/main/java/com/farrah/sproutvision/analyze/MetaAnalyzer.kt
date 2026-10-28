package com.farrah.sproutvision.analyze

import android.util.Base64
import com.farrah.sproutvision.data.GrowthStage
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
 * Muse Spark verdict via the Meta Model API.
 *
 * HACKATHON COPY ONLY — the frozen real v2 source is untouched,
 * and GeminiAnalyzer remains the fallback there and here.
 *
 * Adapted 2026-10-22 for the real Mac v2 base (not the stale Oct 13 base):
 * - PlantAnalyzer now takes an onEvent progress callback — this override
 *   matches the real signature (imageBytes, varietyHint, onEvent).
 * - AnalysisResult now requires trayVisible / trayBox and carries
 *   confidence — Spark's JSON does not currently report a tray box, so
 *   this returns trayVisible = true, trayBox = null, and confidence
 *   from Spark when present, else 0 (unknown). The ViewModel overwrites
 *   trayBox with the SAM-measured union box when segmentation ran.
 *
 * VERIFY AT KICKOFF (Oct 26) against Meta's current docs: model ID,
 * endpoint shape, and auth. Third-party guides list model "muse-spark-1.3"
 * and base URL https://api.meta.ai/v1 — do not ship on a stale ID.
 * The request below follows the OpenAI-compatible Chat Completions shape
 * those guides describe; if Meta's current docs differ, change this file
 * first and re-run the test script in META_MODEL_SETUP.md. Muse Code
 * Session 1 (2026-10-22) confirmed this shape against Meta's docs
 * (response at choices[0].message.content) and that json_object is a
 * documented, valid response format (json_schema is a possible upgrade).
 *
 * @param apiKey Meta Model API key, stored per-user in Settings (never hardcoded).
 * @param segmentationSummary Optional SAM 3.1 measurements, e.g.
 *   "SAM coverage 18%, largest gap ~6% of tray, unevenness 32/100".
 *   When present, Spark reasons from the measurement, not just the photo.
 * @param growPlan Optional grower plan, e.g. "basil grown to full maturity,
 *   NOT harvested as a microgreen". This is what stops a mature basil plant
 *   being graded "harvest overdue". In the real v2 ViewModel this is
 *   derived from the scanned tray's GrowIntent (GROW_OUT).
 */
/**
 * SAM literal-scale calibration for the Spark prompt (golden-tray runs,
 * 2026-10-28): SAM counts literal leaf pixels, and two independent
 * methods agree a full healthy basil tray reads about 15-25% while
 * growers perceive ~70% canopy. Without this note Spark reads 15% as
 * sparse. Pure so the contract stays unit-tested; reword deliberately.
 */
internal fun samScaleNote(): String =
    "SAM counts literal leaf pixels: a full healthy microgreen tray measures " +
        "about 15-25% (golden-tray calibration), so never read a low SAM number " +
        "as sparse on its own. Judge health against this measured scale, and report " +
        "coveragePercent as grower-perceived canopy (0-100), reconciling the photo " +
        "with the measurement."

class MetaAnalyzer(
    private val apiKey: String,
    private val segmentationSummary: String? = null,
    private val growPlan: String? = null
) : PlantAnalyzer {

    companion object {
        const val DEFAULT_MODEL = "muse-spark-1.3"
        const val BASE_URL = "https://api.meta.ai/v1"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    override suspend fun analyze(
        imageBytes: ByteArray,
        varietyHint: String?,
        onEvent: (String) -> Unit
    ): AnalysisResult =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "Meta Model API key is missing — add it in Settings." }

            onEvent("Waiting on Meta AI…")
            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val bodyJson = JSONObject()
                .put("model", DEFAULT_MODEL)
                .put(
                    "messages", JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put(
                                "content", JSONArray()
                                    .put(JSONObject().put("type", "text").put("text", buildPrompt(varietyHint)))
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
                .url("$BASE_URL/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Meta Model API error ${response.code}: ${raw.take(300)}")
                }
                onEvent("Parsing response…")
                parseResult(raw)
            }
        }

    private fun buildPrompt(varietyHint: String?): String = """
        You are an expert microgreens grower analyzing a photo of a grow tray.
        ${if (!varietyHint.isNullOrBlank()) "The grower believes this tray is: $varietyHint." else ""}
        ${if (!growPlan.isNullOrBlank()) "Grow plan (this overrides a generic harvest window): $growPlan." else ""}
        ${if (!segmentationSummary.isNullOrBlank()) "Measured by SAM 3.1 segmentation (trust these numbers over a visual estimate): $segmentationSummary. ${samScaleNote()}" else ""}
        Identify the variety, the current growth stage (exactly one of: SEEDED, GERMINATION, BLACKOUT, LIGHT, HARVEST),
        estimate canopy coverage percent (0-100), an overall health score (0-100),
        list any visible issues (examples: mold, legginess, uneven germination, damping off, dry spots — empty list if none),
        and give one short actionable recommendation.
        Reply with ONLY valid JSON, no markdown fences, in exactly this shape:
        {"variety":"...","stage":"LIGHT","healthScore":85,"coveragePercent":90,"issues":["..."],"recommendation":"..."}
    """.trimIndent()

    private fun parseResult(raw: String): AnalysisResult {
        val text = JSONObject(raw)
            .getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
        val cleaned = text.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val o = JSONObject(cleaned)
        val issues = mutableListOf<String>()
        val arr = o.optJSONArray("issues")
        if (arr != null) {
            for (i in 0 until arr.length()) issues += arr.optString(i)
        }
        return AnalysisResult(
            variety = o.optString("variety", "Unknown"),
            stage = GrowthStage.fromName(o.optString("stage", "LIGHT")),
            healthScore = o.optInt("healthScore", 0).coerceIn(0, 100),
            issues = issues,
            coveragePercent = o.optInt("coveragePercent", 0).coerceIn(0, 100),
            recommendation = o.optString("recommendation", ""),
            isDemo = false,
            trayVisible = true,
            trayBox = null,
            confidence = o.optInt("confidence", 0).coerceIn(0, 100)
        )
    }
}
