package com.farrah.sproutvision.data

/**
 * One leaderboard row of the crop trial scorecard. Germination and average
 * health are computed from scan history; days-to-harvest, yield, and taste
 * are manual entries ([VarietyTrialEntity]).
 */
data class VarietyScorecard(
    val varietyKey: String,
    val displayName: String,
    val trayCount: Int,
    val scanCount: Int,
    /**
     * Germination %: the average canopy coverage of each tray's FIRST scan
     * for this variety (early coverage ≈ germination success). Null when the
     * variety has no scans yet.
     */
    val germinationPct: Int?,
    /** Average health across all scans for this variety. Null when no scans. */
    val avgHealth: Int?,
    val daysToHarvest: Int?,
    val yieldGrams: Int?,
    val tasteRating: Int?,
    /**
     * Composite leaderboard score, roughly 0–100:
     * 35% germination + 30% average health + taste×3 (≤15) +
     * min(yield,100)×0.1 (≤10) + max(0, 14−daysToHarvest) (≤10).
     * Missing inputs contribute 0.
     */
    val composite: Double
)

/**
 * Builds the per-variety leaderboard, sorted by composite score descending.
 * Covers every variety seen on any tray OR any manual trial entry, so a
 * hand-logged harvest still shows up before its trays are scanned.
 */
fun buildScorecards(
    trays: List<TrayEntity>,
    scans: List<ScanEntity>,
    trials: List<VarietyTrialEntity>
): List<VarietyScorecard> {
    val traysByVariety = trays.groupBy { TrayRepository.varietyKey(it.variety) }
    val trayById = trays.associateBy { it.id }
    val scansByTray = scans.groupBy { it.trayId }
    val trialsByKey = trials.associateBy { it.varietyKey }

    val keys = (traysByVariety.keys + trialsByKey.keys).filter { it.isNotBlank() }.toSet()
    return keys.map { key ->
        val varietyTrays = traysByVariety[key].orEmpty()
        val varietyScans = varietyTrays.flatMap { scansByTray[it.id].orEmpty() } +
            // Scans whose tray was deleted keep their own variety label.
            scans.filter { it.trayId !in trayById && TrayRepository.varietyKey(it.variety) == key }

        // Germination: each tray's earliest scan coverage, averaged.
        val firstCoverages = varietyTrays.mapNotNull { tray ->
            scansByTray[tray.id]?.minByOrNull { it.timestampEpochMs }?.coveragePercent
        }
        val germination = if (firstCoverages.isEmpty()) null
        else (firstCoverages.average() + 0.5).toInt()

        val avgHealth = if (varietyScans.isEmpty()) null
        else (varietyScans.map { it.healthScore }.average() + 0.5).toInt()

        val trial = trialsByKey[key]
        val displayName = trial?.displayName
            ?: varietyTrays.firstOrNull()?.variety?.trim().orEmpty().ifBlank { key }

        val composite = 0.35 * (germination ?: 0) +
            0.30 * (avgHealth ?: 0) +
            (trial?.tasteRating ?: 0) * 3.0 +
            minOf(trial?.yieldGrams ?: 0, 100) * 0.1 +
            maxOf(0, 14 - (trial?.daysToHarvest ?: 99))

        VarietyScorecard(
            varietyKey = key,
            displayName = displayName,
            trayCount = varietyTrays.size,
            scanCount = varietyScans.size,
            germinationPct = germination,
            avgHealth = avgHealth,
            daysToHarvest = trial?.daysToHarvest,
            yieldGrams = trial?.yieldGrams,
            tasteRating = trial?.tasteRating,
            composite = composite
        )
    }.sortedByDescending { it.composite }
}
