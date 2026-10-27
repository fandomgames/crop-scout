package com.farrah.sproutvision.settings

/**
 * Saved custom seed varieties: every non-default variety on a saved
 * tray is remembered (auto-save) and offered in the Add-tray picker.
 * Stored as one newline-separated string — names come from a
 * single-line field, so newlines cannot occur; parsing still drops
 * blank lines. Oldest entries past [MAX_CUSTOM_VARIETIES] are trimmed.
 */
const val MAX_CUSTOM_VARIETIES = 20

fun parseCustomVarieties(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .takeLast(MAX_CUSTOM_VARIETIES)
}

/** Stores a custom-variety list compactly; see [parseCustomVarieties]. */
fun joinCustomVarieties(values: List<String>): String =
    values.map { it.trim() }
        .filter { it.isNotEmpty() }
        .takeLast(MAX_CUSTOM_VARIETIES)
        .joinToString("\n")

/**
 * Adds [candidate] to the saved list: trims, ignores blanks,
 * built-in defaults (case-insensitive), and duplicates
 * (case-insensitive, keeping the first-seen spelling).
 */
fun mergeCustomVariety(
    existing: List<String>,
    candidate: String,
    defaultNames: Set<String>
): List<String> {
    val name = candidate.trim()
    if (name.isEmpty()) return existing
    if (defaultNames.any { it.equals(name, ignoreCase = true) }) return existing
    if (existing.any { it.equals(name, ignoreCase = true) }) return existing
    return (existing + name).takeLast(MAX_CUSTOM_VARIETIES)
}
