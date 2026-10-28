package com.farrah.sproutvision.analyze

/**
 * One row of the golden-tray validation report (written Mac-side by
 * :validation:validateGolden, read here from assets). All measurements
 * are nullable: null renders as —, never as 0%.
 */
data class ValidationRow(
    val id: String,
    val samCoverage: Int?,
    val byEye: Int?,
    /** samCoverage - byEye when both are present, else null. */
    val difference: Int?
)

/** Golden shot IDs in display order. The report may omit shots; the view fills —. */
val GOLDEN_SHOT_IDS = listOf(
    "shot1-full", "shot2-uneven", "shot3-gap", "shot4-poorlight", "shot5-partial"
)

/**
 * Parses golden-validation.json with the hand-rolled JSON reader (no
 * org.json, so this stays unit-testable on plain JVM tests). Returns
 * one row per [GOLDEN_SHOT_IDS] in order, null-filled for missing
 * shots — or null when the whole document is unreadable. Never throws
 * for malformed input.
 */
fun parseValidationReport(json: String): List<ValidationRow>? {
    val root = try {
        parseJsonObject(json)
    } catch (_: Exception) {
        null
    } ?: return null
    val items = try {
        (root["shots"] as? JsonValue.Arr)?.items.orEmpty()
    } catch (_: Exception) {
        return null
    }
    val byId = mutableMapOf<String, ValidationRow>()
    for (item in items) {
        val entries = try {
            (item as? JsonValue.Obj)?.entries ?: continue
        } catch (_: Exception) {
            continue
        }
        val id = (entries["id"] as? JsonValue.Str)?.value ?: continue
        val sam = entries["samCoverage"].toNullableInt()
        val byEye = entries["byEye"].toNullableInt()
        byId[id] = ValidationRow(
            id = id,
            samCoverage = sam,
            byEye = byEye,
            difference = if (sam != null && byEye != null) sam - byEye else null
        )
    }
    return GOLDEN_SHOT_IDS.map { id ->
        byId[id] ?: ValidationRow(id, null, null, null)
    }
}

private fun JsonValue?.toNullableInt(): Int? = when (this) {
    is JsonValue.Other -> raw.toIntOrNull()
    is JsonValue.Str -> value.toIntOrNull()
    else -> null
}
