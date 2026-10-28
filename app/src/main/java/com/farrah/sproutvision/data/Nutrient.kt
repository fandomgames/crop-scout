package com.farrah.sproutvision.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale

/**
 * Nutrient inventory + per-tray feeding (v1). The inventory lives in
 * Manage; feeding schedules and the feeding log live on TrayDetail next
 * to the pH/EC section. Feeding never changes verdicts.
 *
 * Deferred to later versions (NOT in v1): scan-deficiency to nutrient
 * suggestions (needs deficiency detection, not built) and scan-to-create
 * tray (separate feature).
 */

/** Strength stepper: 0-200% in 25% steps (half, full, double). */
const val STRENGTH_MIN = 0
const val STRENGTH_MAX = 200
const val STRENGTH_STEP = 25

/** Reservoir stepper: 0-50 L in 0.5 L steps. */
const val RESERVOIR_MIN = 0.0
const val RESERVOIR_MAX = 50.0
const val RESERVOIR_STEP = 0.5

/** One inventory product with its grower-defined dosage rule. */
@Entity(tableName = "nutrients")
data class NutrientProduct(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String,
    /** Optional NPK, e.g. "3-1-2". */
    val npk: String? = null,
    /** Form, e.g. "Liquid" or "Powder". */
    val form: String,
    /** Rule: [doseAmount] [doseUnit] per [dosePerLiters] L at full strength. */
    val doseAmount: Double,
    val doseUnit: String,
    val dosePerLiters: Double
)

/** Grower-defined dosage rule extracted from a product (null when invalid). */
data class DosageRule(val amount: Double, val unit: String, val perLiters: Double)

fun dosageRuleOf(product: NutrientProduct): DosageRule? {
    if (product.doseAmount <= 0 || product.dosePerLiters <= 0) return null
    if (product.doseUnit.isBlank()) return null
    return DosageRule(product.doseAmount, product.doseUnit.trim(), product.dosePerLiters)
}

/**
 * Deterministic dose math — pure arithmetic, never an LLM call. Dose =
 * rule amount × (reservoir ÷ rule liters) × (strength ÷ 100).
 *
 * Null only when no dose can be computed (missing/invalid rule, negative
 * reservoir). Zero reservoir computes 0.0 (an empty reservoir needs
 * nothing); strength clamps to 0-200%. A blank unit is an invalid rule —
 * the dose would print without a unit, so it computes nothing.
 */
fun computeDose(
    rule: DosageRule?,
    reservoirLiters: Double,
    strengthPercent: Int
): Double? {
    if (rule == null) return null
    if (rule.amount <= 0 || rule.perLiters <= 0 || rule.unit.isBlank()) return null
    if (reservoirLiters < 0) return null
    val strength = strengthPercent.coerceIn(STRENGTH_MIN, STRENGTH_MAX) / 100.0
    return rule.amount * (reservoirLiters / rule.perLiters) * strength
}

/** Per-tray feeding schedule: which nutrient, at what strength, from when. */
@Entity(
    tableName = "feeding_schedules",
    foreignKeys = [
        ForeignKey(
            entity = TrayEntity::class,
            parentColumns = ["id"],
            childColumns = ["trayId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = NutrientProduct::class,
            parentColumns = ["id"],
            childColumns = ["nutrientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["trayId"]), Index(value = ["nutrientId"])]
)
data class FeedingSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trayId: Long,
    val nutrientId: Long,
    val strengthPercent: Int,
    /** GrowthStage name, or null for "anytime". */
    val startStage: String? = null
)

/** One logged feeding: dose computed at log time, never edited after. */
@Entity(
    tableName = "feeding_entries",
    foreignKeys = [
        ForeignKey(
            entity = TrayEntity::class,
            parentColumns = ["id"],
            childColumns = ["trayId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = NutrientProduct::class,
            parentColumns = ["id"],
            childColumns = ["nutrientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["trayId", "timestampEpochMs"])]
)
data class FeedingEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trayId: Long,
    val nutrientId: Long,
    val timestampEpochMs: Long,
    val strengthPercent: Int,
    val reservoirLiters: Double,
    /** Computed dose at log time; null when the rule was missing/invalid. */
    val doseAmount: Double?,
    val doseUnit: String,
    /** Optional note on the entity; not composer-fillable in v1. */
    val note: String = ""
)

/** Newest-first ordering for the feeding log. */
fun feedingLatestFirst(entries: List<FeedingEntry>): List<FeedingEntry> =
    entries.sortedByDescending { it.timestampEpochMs }

/** "12.5 ml", or "—" when the dose could not be computed. */
fun formatDose(amount: Double?, unit: String): String =
    if (amount == null) "—"
    else String.format(Locale.US, "%.1f %s", amount, unit)

/** A feeding can log once a nutrient is picked and the reservoir is set. */
fun canLogFeeding(nutrientId: Long?, reservoirLiters: Double): Boolean =
    nutrientId != null && reservoirLiters > 0

fun stepStrength(value: Int, up: Boolean): Int =
    (value + if (up) STRENGTH_STEP else -STRENGTH_STEP)
        .coerceIn(STRENGTH_MIN, STRENGTH_MAX)

fun stepReservoir(value: Double, up: Boolean): Double =
    (value + if (up) RESERVOIR_STEP else -RESERVOIR_STEP)
        .coerceIn(RESERVOIR_MIN, RESERVOIR_MAX)
