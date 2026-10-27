package com.farrah.sproutvision.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Manual per-variety trial data for the crop trial scorecard. The variety
 * name is the key (stored lowercase + trimmed so "Radish" and "radish" share
 * one row). Germination % and average health come from scan history instead —
 * see [VarietyScorecard] — so only the hand-measured values live here.
 */
@Entity(tableName = "variety_trials")
data class VarietyTrialEntity(
    @PrimaryKey val varietyKey: String,
    /** Display name as first entered (e.g. "Radish"). */
    val displayName: String,
    /** Observed days from seeding to harvest, entered at harvest. Null until entered. */
    val daysToHarvest: Int? = null,
    /** Harvested yield in grams, entered at harvest. Null until entered. */
    val yieldGrams: Int? = null,
    /** Taste rating 1–5. Null until entered. */
    val tasteRating: Int? = null
)
