package com.farrah.sproutvision.analyze

/**
 * Self-reported confidence of a wild-plant identification. Anything
 * unrecognized reads as [LOW] — a guess is never presented as certain.
 */
enum class IdentifyConfidence {
    HIGH,
    MEDIUM,
    LOW;

    companion object {
        fun fromName(raw: String?): IdentifyConfidence = when (raw?.trim()?.lowercase()) {
            "high" -> HIGH
            "medium" -> MEDIUM
            else -> LOW
        }
    }
}

/** A starting-point care sheet from Gemini's plant knowledge — not gospel. */
data class PlantCareSheet(
    val sun: String = "",
    val water: String = "",
    val soil: String = "",
    val hardinessZones: String = "",
    val bloomTime: String = "",
    val matureSize: String = "",
    val funFact: String = ""
)

/** Which identifier produced a result. Null = unknown (e.g. herbarium reopens predate it). */
enum class IdentifySource {
    META,
    GEMINI,
    DEMO
}

/** Result of identifying one wild-plant photo. Never touches tray data. */
data class PlantIdentification(
    val commonName: String,
    val scientificName: String,
    val family: String,
    val confidence: IdentifyConfidence,
    val care: PlantCareSheet,
    val betterPhotoTip: String,
    val isDemo: Boolean = false,
    val source: IdentifySource? = null
)

/**
 * Confidence badge copy. Low confidence reads as a best guess and always
 * pairs with the better-photo tip — never presented as certain.
 */
fun identifyBadgeText(confidence: IdentifyConfidence): String = when (confidence) {
    IdentifyConfidence.HIGH -> "High confidence"
    IdentifyConfidence.MEDIUM -> "Likely — medium confidence"
    IdentifyConfidence.LOW -> "Best guess — low confidence"
}

/** Fallback photo tip when the model omits one — low confidence must always say what would help. */
private const val DEFAULT_PHOTO_TIP =
    "Get closer to the flower head and capture the leaf arrangement."

fun buildIdentifyPrompt(): String = """
    You are an expert botanist identifying a wild plant from a photo.
    Identify the plant and reply with ONLY valid JSON, no markdown fences, in exactly this shape:
    {"commonName":"...","scientificName":"...","family":"...","confidence":"high","betterPhotoTip":"...","careSheet":{"sun":"...","water":"...","soil":"...","hardinessZones":"...","bloomTime":"...","matureSize":"...","funFact":"..."}}
    Rules:
    - "scientificName" is genus + species (e.g. "Echinacea purpurea").
    - "confidence" is exactly one of: high, medium, low. Be honest: a blurry
      photo, a distant plant, or missing flowers/leaves means low.
    - "sun" is exactly one of: full sun, partial sun, shade.
    - "hardinessZones" is the USDA range (e.g. "3–9").
    - "funFact" is one interesting fact about this plant.
    - "betterPhotoTip" says what photo would nail the ID (e.g. "get closer
      to the flower head" or "capture the leaf arrangement") — required,
      especially when confidence is low.
    - If no plant is visible, set "commonName" to "Unknown plant",
      "confidence" to "low", and explain in "betterPhotoTip".
""".trimIndent()

/**
 * Parses Gemini's identification JSON into a [PlantIdentification]. Pure
 * Kotlin (no Android JSON) so it stays unit-testable; missing fields fall
 * back to honest defaults rather than failing the whole card.
 *
 * @throws IllegalArgumentException when [json] is not a JSON object at all.
 */
fun parsePlantIdentification(json: String): PlantIdentification {
    val cleaned = json.trim()
        .removePrefix("```json").removePrefix("```")
        .removeSuffix("```").trim()
    require(cleaned.startsWith("{") && cleaned.endsWith("}")) {
        "Not a JSON object: ${cleaned.take(80)}"
    }
    val careObj = objectField(cleaned, "careSheet")
    return PlantIdentification(
        commonName = stringField(cleaned, "commonName").ifBlank { "Unknown plant" },
        scientificName = stringField(cleaned, "scientificName"),
        family = stringField(cleaned, "family"),
        confidence = IdentifyConfidence.fromName(stringField(cleaned, "confidence")),
        care = PlantCareSheet(
            sun = stringField(careObj, "sun"),
            water = stringField(careObj, "water"),
            soil = stringField(careObj, "soil"),
            hardinessZones = stringField(careObj, "hardinessZones"),
            bloomTime = stringField(careObj, "bloomTime"),
            matureSize = stringField(careObj, "matureSize"),
            funFact = stringField(careObj, "funFact")
        ),
        betterPhotoTip = stringField(cleaned, "betterPhotoTip").ifBlank { DEFAULT_PHOTO_TIP }
    )
}

/** Value of a top-level "key": "string" pair, or "" when absent. */
private fun stringField(obj: String, key: String): String {
    val pattern = Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
    val raw = pattern.find(obj)?.groupValues?.getOrNull(1) ?: return ""
    return unescapeJson(raw)
}

/** Substring of a nested "key": {...} object, or "" when absent. */
private fun objectField(obj: String, key: String): String {
    val start = Regex("\"$key\"\\s*:\\s*\\{").find(obj) ?: return ""
    var depth = 0
    for (i in start.range.last until obj.length) {
        when (obj[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return obj.substring(start.range.last, i + 1)
            }
        }
    }
    return ""
}

private fun unescapeJson(raw: String): String {
    val out = StringBuilder(raw.length)
    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        if (c == '\\' && i + 1 < raw.length) {
            when (raw[i + 1]) {
                '"' -> out.append('"')
                '\\' -> out.append('\\')
                '/' -> out.append('/')
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'r' -> out.append('\r')
                'u' -> {
                    val hex = raw.substring((i + 2).coerceAtMost(raw.length), (i + 6).coerceAtMost(raw.length))
                    out.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                    i += 4
                }
                else -> out.append(raw[i + 1])
            }
            i += 2
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}
