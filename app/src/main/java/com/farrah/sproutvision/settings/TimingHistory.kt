package com.farrah.sproutvision.settings

/** Samples kept per analyzer for the typical-duration estimate. */
const val MAX_TIMING_SAMPLES = 5

/**
 * Parses a stored duration list (see [joinMsList]). Tolerates garbage:
 * unparseable or non-positive entries are dropped, oldest beyond
 * [MAX_TIMING_SAMPLES] are trimmed.
 */
fun parseMsList(raw: String?): List<Long> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(",")
        .mapNotNull { it.trim().toLongOrNull() }
        .filter { it > 0 }
        .takeLast(MAX_TIMING_SAMPLES)
}

/** Stores a duration list compactly; see [parseMsList]. */
fun joinMsList(values: List<Long>): String =
    values.filter { it > 0 }.takeLast(MAX_TIMING_SAMPLES).joinToString(",")
