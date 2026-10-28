package com.farrah.sproutvision.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale
import kotlin.math.roundToInt

/** Stepper bounds and steps: pH matches test strips (0.5), EC reads 0.1 mS/cm. */
const val PH_MIN = 4.0
const val PH_MAX = 9.0
const val PH_READING_STEP = 0.5
const val EC_MIN = 0.0
const val EC_MAX = 4.0
const val EC_STEP = 0.1

/** New trays start here; EC stays blank (null) until the grower sets it. */
const val DEFAULT_PH_LOW = 5.8
const val DEFAULT_PH_HIGH = 6.2

/** Stepper start positions (documented, not data — untouched stays null). */
const val PH_READING_SEED = 7.0
const val EC_READING_SEED = 1.0
const val EC_LOW_SEED = 0.0
const val EC_HIGH_SEED = 4.0

/**
 * One manual pH/EC reading for a tray. Either value may be null (EC
 * reader absent, pH strip unreadable) but not both — an empty entry
 * cannot save. Append-only in v1: no edit, no delete.
 */
@Entity(
    tableName = "ph_ec_readings",
    foreignKeys = [
        ForeignKey(
            entity = TrayEntity::class,
            parentColumns = ["id"],
            childColumns = ["trayId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["trayId", "timestampEpochMs"])]
)
data class PhEcReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trayId: Long,
    val timestampEpochMs: Long,
    /** pH 4.0-9.0 in 0.5 steps, null when the entry is EC-only. */
    val ph: Double? = null,
    /** EC mS/cm 0.0-4.0 in 0.1 steps, null when blank (never 0 by default). */
    val ec: Double? = null,
    val note: String = ""
)

/** One decimal step with clamping; first tap from null lands on [seed]. */
fun stepValue(
    value: Double?,
    up: Boolean,
    min: Double,
    max: Double,
    step: Double,
    seed: Double
): Double {
    val base = value ?: return seed
    val stepped = if (up) base + step else base - step
    return ((stepped * 10).roundToInt() / 10.0).coerceIn(min, max)
}

/** pH reading stepper: 0.5 steps from a neutral 7.0 start. */
fun stepReadingPh(value: Double?, up: Boolean): Double =
    stepValue(value, up, PH_MIN, PH_MAX, PH_READING_STEP, PH_READING_SEED)

/** EC reading stepper: 0.1 mS/cm steps from a 1.0 start. */
fun stepReadingEc(value: Double?, up: Boolean): Double =
    stepValue(value, up, EC_MIN, EC_MAX, EC_STEP, EC_READING_SEED)

/** pH target bounds: 0.1 steps (targets hold 5.8/6.2, off the 0.5 strip grid). */
fun stepTargetPhLow(value: Double?, up: Boolean): Double =
    stepValue(value, up, PH_MIN, PH_MAX, EC_STEP, DEFAULT_PH_LOW)

/** pH target bounds: 0.1 steps (targets hold 5.8/6.2, off the 0.5 strip grid). */
fun stepTargetPhHigh(value: Double?, up: Boolean): Double =
    stepValue(value, up, PH_MIN, PH_MAX, EC_STEP, DEFAULT_PH_HIGH)

/** EC target bounds seed from the full span (flags nothing until set). */
fun stepTargetEcLow(value: Double?, up: Boolean): Double =
    stepValue(value, up, EC_MIN, EC_MAX, EC_STEP, EC_LOW_SEED)

/** EC target bounds seed from the full span (flags nothing until set). */
fun stepTargetEcHigh(value: Double?, up: Boolean): Double =
    stepValue(value, up, EC_MIN, EC_MAX, EC_STEP, EC_HIGH_SEED)

/** An entry saves only when at least one of pH or EC is present. */
fun canSavePhEc(ph: Double?, ec: Double?): Boolean = ph != null || ec != null

/**
 * True when a set value falls outside a fully set range. A blank value
 * or a blank range (either bound null) never flags.
 */
fun isOutOfRange(value: Double?, low: Double?, high: Double?): Boolean =
    value != null && low != null && high != null && (value < low || value > high)

/** Per-value range suffix: " (low)", " (high)", or "" when fine or blank. */
fun rangeSuffix(value: Double?, low: Double?, high: Double?): String = when {
    value == null || low == null || high == null -> ""
    value < low -> " (low)"
    value > high -> " (high)"
    else -> ""
}

/** One-decimal display; blank reads "—", never 0. */
fun formatPhEc(value: Double?): String =
    if (value == null) "—" else String.format(Locale.US, "%.1f", value)

/** History order, newest first. Applied in the repository so the contract is unit-tested. */
fun latestFirst(readings: List<PhEcReading>): List<PhEcReading> =
    readings.sortedWith(
        compareByDescending<PhEcReading> { it.timestampEpochMs }.thenByDescending { it.id }
    )

/** The newest reading, or null when the tray has none. */
fun latestReading(readings: List<PhEcReading>): PhEcReading? =
    readings.maxWithOrNull(compareBy({ it.timestampEpochMs }, { it.id }))
