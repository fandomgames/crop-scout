package com.farrah.sproutvision.analyze

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class SamException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Pinned concept phrases (Meta playground, Oct 25, basil tray): bare
 * "microgreens" hugs the plants, including small seedlings; "black
 * tray" for the tray ("seed tray" and "tray" both returned zero
 * matches on the full-tray photo). One concept per request, no
 * variety words (docs: extra words give no benefit). Grow-outs use
 * bare "plants".
 */
internal fun samConcept(growOut: Boolean): String = if (growOut) "plants" else "microgreens"

internal const val SAM_TRAY_CONCEPT = "black tray"

internal fun escapeJson(text: String): String = buildString(text.length + 8) {
    for (c in text) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }
}

/**
 * SAM 3.1 Responses-API request body (Meta playground's generated code,
 * Oct 25): model sam-3.1, one user message with the concept noun phrase
 * plus an input_image item referencing the uploaded file ID, streamed,
 * lossless masks.
 */
internal fun buildSamRequestBody(concept: String, fileId: String): String =
    """{"model":"${SamClient.MODEL}","stream":true,""" +
        """"metadata":{"mask_encoding":"${SamClient.MASK_ENCODING}"},""" +
        """"input":[{"type":"message","role":"user","content":""" +
        """[{"type":"input_text","text":"${escapeJson(concept)}"},""" +
        """{"type":"input_image","file_id":"${escapeJson(fileId)}"}]}]}"""

/**
 * File ID from a /v1/files upload response (`{"id": ...}`). Pure so the
 * upload contract stays unit-tested without touching the network.
 */
internal fun parseUploadId(body: String): String {
    val obj = parseJsonObject(body)
        ?.let { JsonValue.Obj(it) }
        ?: throw SamException("SAM file upload returned an unreadable response.")
    return obj.string("id")?.takeIf { it.isNotBlank() }
        ?: throw SamException("SAM file upload returned no file id.")
}

internal sealed interface SseEvent {
    data class Delta(val text: String) : SseEvent
    data class DoneText(val text: String?) : SseEvent
    data class Completed(val outputText: String?) : SseEvent
    data class Failed(val message: String) : SseEvent
    data object Other : SseEvent
}

/**
 * Minimal JSON DOM — just enough to read Responses SSE fields (event
 * type, delta/done text, nested error messages, completed output
 * parts). Hand-rolled (no org.json) so the SSE path stays
 * unit-testable on plain JVM tests.
 */
internal sealed interface JsonValue {
    data class Str(val value: String) : JsonValue
    data class Obj(val entries: Map<String, JsonValue>) : JsonValue
    data class Arr(val items: List<JsonValue>) : JsonValue
    data object Null : JsonValue
    data class Other(val raw: String) : JsonValue // numbers, true, false
}

private const val JSON_MAX_DEPTH = 32

/**
 * Parses one JSON value; null unless the whole input is exactly one
 * well-formed value. Depth-capped so hostile input cannot stack
 * overflow the recursive descent.
 */
internal fun parseJsonValue(text: String): JsonValue? {
    var i = 0
    val n = text.length
    fun skipWs() {
        while (i < n && text[i].isWhitespace()) i++
    }
    fun readString(): String? {
        if (i >= n || text[i] != '"') return null
        i++
        val out = StringBuilder()
        while (i < n) {
            val c = text[i++]
            if (c == '"') return out.toString()
            if (c != '\\') {
                out.append(c)
                continue
            }
            if (i >= n) return null
            when (val e = text[i++]) {
                '"', '\\', '/' -> out.append(e)
                'b' -> out.append((8).toChar())
                'f' -> out.append((12).toChar())
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'u' -> {
                    if (i + 4 > n) return null
                    val code = text.substring(i, i + 4).toIntOrNull(16) ?: return null
                    i += 4
                    out.append(code.toChar())
                }
                else -> return null
            }
        }
        return null
    }
    fun readScalar(): JsonValue? {
        if (text.startsWith("null", i) &&
            (i + 4 >= n || text[i + 4] in ",:]}" || text[i + 4].isWhitespace())
        ) {
            i += 4
            return JsonValue.Null
        }
        val start = i
        while (i < n && text[i] != ',' && text[i] != ']' &&
            text[i] != '}' && text[i] != ':' && !text[i].isWhitespace()
        ) i++
        if (start == i) return null
        return JsonValue.Other(text.substring(start, i))
    }
    // One recursive function (local functions cannot call each
    // other, so the object/array loops live inline here).
    fun readValue(depth: Int): JsonValue? {
        skipWs()
        if (i >= n || depth > JSON_MAX_DEPTH) return null
        when (text[i]) {
            '"' -> return readString()?.let { JsonValue.Str(it) }
            '{' -> {
                i++
                val entries = mutableMapOf<String, JsonValue>()
                skipWs()
                if (i < n && text[i] == '}') {
                    i++
                    return JsonValue.Obj(entries)
                }
                while (true) {
                    skipWs()
                    val key = readString() ?: return null
                    skipWs()
                    if (i >= n || text[i] != ':') return null
                    i++
                    entries[key] = readValue(depth + 1) ?: return null
                    skipWs()
                    if (i >= n) return null
                    if (text[i] == '}') {
                        i++
                        return JsonValue.Obj(entries)
                    }
                    if (text[i] != ',') return null
                    i++
                }
            }
            '[' -> {
                i++
                val items = mutableListOf<JsonValue>()
                skipWs()
                if (i < n && text[i] == ']') {
                    i++
                    return JsonValue.Arr(items)
                }
                while (true) {
                    items += readValue(depth + 1) ?: return null
                    skipWs()
                    if (i >= n) return null
                    if (text[i] == ']') {
                        i++
                        return JsonValue.Arr(items)
                    }
                    if (text[i] != ',') return null
                    i++
                }
            }
            else -> return readScalar()
        }
    }
    skipWs()
    val value = readValue(0) ?: return null
    skipWs()
    return if (i == n) value else null
}

internal fun parseJsonObject(text: String): Map<String, JsonValue>? =
    (parseJsonValue(text) as? JsonValue.Obj)?.entries

private fun JsonValue.Obj.string(vararg path: String): String? {
    var current: JsonValue = this
    for (key in path) {
        current = (current as? JsonValue.Obj)?.entries?.get(key) ?: return null
    }
    return (current as? JsonValue.Str)?.value
}

/**
 * Concatenated output_text from a completed Response object
 * (`output[].content[]`, docs "Obtain the completed response");
 * null when the object carries no output text. Refusal parts are
 * skipped — refusals surface as failures, never as masks.
 */
internal fun harvestResponseObjectText(obj: JsonValue.Obj): String? {
    val output = obj.entries["output"] as? JsonValue.Arr ?: return null
    val text = StringBuilder()
    var found = false
    for (item in output.items) {
        val content =
            (item as? JsonValue.Obj)?.entries?.get("content") as? JsonValue.Arr ?: continue
        for (part in content.items) {
            part as? JsonValue.Obj ?: continue
            val type = part.string("type")
            if (type != null && type != "output_text") continue
            text.append(part.string("text") ?: continue)
            found = true
        }
    }
    return if (found) text.toString() else null
}

/**
 * Output text from a non-streamed (full JSON object) response body,
 * or null when it carries none. A top-level error object throws with
 * its message so the feed names the real failure.
 */
internal fun harvestCompletedBody(body: String): String? {
    val obj = JsonValue.Obj(parseJsonObject(body) ?: return null)
    obj.string("error", "message")?.let { throw SamException("SAM 3.1 request failed: $it") }
    return harvestResponseObjectText(obj)
}

/**
 * Classifies one SSE data payload. The event type comes from the
 * embedded "type" field when present, else from the SSE `event:` line
 * Meta's stream sends (its data payloads carry no type). Channels
 * mirror Meta's own stream consumer: deltas append; done events
 * carry the full text for streams that never sent deltas; the
 * completed event's response object carries the output array.
 */
internal fun parseSseData(dataJson: String, eventType: String? = null): SseEvent {
    val obj = parseJsonObject(dataJson)?.let { JsonValue.Obj(it) }
    val embedded = obj?.string("type")
    return when (val type = embedded ?: eventType) {
        "response.output_text.delta" -> {
            val delta = obj?.string("delta")
            if (delta != null) SseEvent.Delta(delta) else SseEvent.Other
        }
        "response.output_text.done" ->
            SseEvent.DoneText(obj?.string("text"))
        "response.content_part.done" -> {
            val part = obj?.entries?.get("part") as? JsonValue.Obj
            when (part?.string("type")) {
                "output_text" -> SseEvent.DoneText(part.string("text"))
                "refusal" -> SseEvent.Failed(
                    part.string("refusal") ?: "The response was refused."
                )
                else -> SseEvent.Other
            }
        }
        "response.completed", "response.incomplete" -> {
            val container =
                (obj?.entries?.get("response") as? JsonValue.Obj) ?: obj
            SseEvent.Completed(container?.let(::harvestResponseObjectText))
        }
        "response.failed" -> SseEvent.Failed(
            obj?.string("response", "error", "message")
                ?: obj?.string("message")
                ?: "The response failed."
        )
        "response.refusal.delta", "response.refusal.done" ->
            SseEvent.Failed(
                obj?.string("refusal") ?: obj?.string("delta")
                    ?: "The response was refused."
            )
        "error" -> SseEvent.Failed(
            obj?.string("error", "message") ?: obj?.string("message")
                ?: "The Responses stream reported an error."
        )
        else -> SseEvent.Other
    }
}

/**
 * Assembles streamed output text from raw SSE lines. `event:` lines
 * set the type for the `data:` payloads that follow (cleared on
 * blank lines, per SSE framing); an embedded "type" wins when
 * present. Deltas append; a done event supplies the whole text only
 * when no deltas arrived (never appended twice); a completed event
 * likewise contributes its output array only as a last resort;
 * completed/[DONE] stop the stream; a failure event throws. Unknown
 * lines and corrupt events are ignored (a hole still fails record
 * parsing loudly, which falls back to Spark-only).
 */
internal fun collectStreamText(sseLines: List<String>): String {
    val text = StringBuilder()
    var sawDelta = false
    var doneUsed = false
    var pendingEvent: String? = null
    for (raw in sseLines) {
        val line = raw.trim()
        if (line.isEmpty()) {
            pendingEvent = null
            continue
        }
        if (line.startsWith(":")) continue
        if (line.startsWith("event:")) {
            pendingEvent = line.substringAfter("event:").trim().ifEmpty { null }
            continue
        }
        if (!line.startsWith("data:")) continue
        val data = line.substringAfter("data:").trim()
        if (data == "[DONE]") break
        when (val event = parseSseData(data, pendingEvent)) {
            is SseEvent.Delta -> {
                text.append(event.text)
                sawDelta = true
            }
            is SseEvent.DoneText ->
                if (!sawDelta && !doneUsed && event.text != null) {
                    text.append(event.text)
                    doneUsed = true
                }
            is SseEvent.Completed -> {
                if (!sawDelta && !doneUsed && event.outputText != null) {
                    text.append(event.outputText)
                }
                break
            }
            is SseEvent.Failed -> throw SamException("SAM 3.1 request failed: ${event.message}")
            is SseEvent.Other -> Unit
        }
    }
    return text.toString()
}

/**
 * SAM 3.1 over the Meta Model API: [uploadPhoto] posts the tray photo
 * to /v1/files, and [segmentRecords] sends one concept noun phrase
 * with the returned file ID, returning the parsed instance records
 * ([SamMaskCodec]). Compositing and measurement live in
 * [MetaSamSegmenter], which uploads once and makes one call per
 * concept (tray, plants).
 *
 * Every failure mode (upload error, HTTP error, empty stream,
 * unparsable records, zero usable masks) throws — the caller falls
 * back to a verdict without measurement and flags it. A missing
 * measurement is never replaced with an invented coverage number.
 */
class SamClient(private val apiKey: String) {

    companion object {
        const val MODEL = "sam-3.1"
        const val MASK_ENCODING = "lossless"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /**
     * Uploads the tray photo (/v1/files, purpose=user_data) and returns
     * the file ID for [segmentRecords]. One upload per scan — both
     * concept requests reuse the ID.
     */
    suspend fun uploadPhoto(imageBytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "Meta Model API key is missing — add it in Settings." }
            require(imageBytes.isNotEmpty()) { "Cannot upload an empty photo." }
            val form = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("purpose", "user_data")
                .addFormDataPart(
                    "file", "upload.jpg",
                    imageBytes.toRequestBody("image/jpeg".toMediaType())
                )
                .build()
            val request = Request.Builder()
                .url("${MetaAnalyzer.BASE_URL}/files")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(form)
                .build()
            client.newCall(request).execute().use { response ->
                val raw = try {
                    response.body?.string().orEmpty()
                } catch (_: Exception) {
                    ""
                }
                if (!response.isSuccessful) {
                    throw IOException("SAM file upload error ${response.code}: ${raw.take(300)}")
                }
                parseUploadId(raw)
            }
        }

    suspend fun segmentRecords(
        fileId: String,
        concept: String,
        onEvent: (String) -> Unit
    ): List<SamRecord> =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "Meta Model API key is missing — add it in Settings." }
            onEvent("Waiting on SAM 3.1…")
            val request = Request.Builder()
                .url("${MetaAnalyzer.BASE_URL}/responses")
                .addHeader("Authorization", "Bearer $apiKey")
                // Required for the SSE stream (docs' curl/python examples
                // all send it): without this the response is not the
                // documented event stream and parses to nothing.
                .addHeader("Accept", "text/event-stream")
                .post(
                    buildSamRequestBody(concept, fileId)
                        .toRequestBody("application/json".toMediaType())
                )
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val raw = try {
                        response.body?.string().orEmpty()
                    } catch (_: Exception) {
                        ""
                    }
                    throw IOException("SAM API error ${response.code}: ${raw.take(300)}")
                }
                val body = response.body ?: throw IOException("SAM API returned an empty body.")
                val lines = mutableListOf<String>()
                body.source().use { source ->
                    while (!source.exhausted()) {
                        source.readUtf8Line()?.let { lines.add(it) }
                    }
                }
                // Streamed responses arrive as SSE (`event:`/`data:`
                // lines); a non-streamed one is a single JSON object
                // (docs: "Obtain the completed response"). The mask
                // records live in the same output_text either way.
                val rawBody = lines.joinToString("\n")
                val firstLine = lines.firstOrNull { it.isNotBlank() }
                    ?.trimStart() ?: ""
                val text = if (firstLine.startsWith("{")) {
                    harvestCompletedBody(rawBody)
                } else {
                    collectStreamText(lines).ifBlank { null }
                }
                if (text.isNullOrBlank()) {
                    // Include what the server actually sent (response
                    // content only, never the key) so a shape mismatch
                    // diagnoses itself instead of failing silently.
                    val hint = lines.joinToString(" ").take(200)
                    throw SamException("SAM 3.1 returned no output text. Body: $hint")
                }
                val records = text.lines().flatMap(::parseSegmentationLine)
                if (records.isEmpty()) {
                    throw SamException("SAM 3.1 returned no usable masks for '$concept'.")
                }
                records
            }
        }
}
