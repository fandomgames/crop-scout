package com.farrah.sproutvision.data

/**
 * Typical grow targets for one microgreen variety: how long it stays under
 * blackout and how tall it should be at harvest. Guide values only —
 * temperature, light, and seeding density shift real trays by days and
 * fractions of an inch.
 */
data class VarietyGuide(
    val variety: String,
    val blackoutMinDays: Int,
    val blackoutMaxDays: Int,
    val harvestMinIn: Double,
    val harvestMaxIn: Double
) {
    val harvestMidIn: Double get() = (harvestMinIn + harvestMaxIn) / 2.0

    fun blackoutLabel(): String = "$blackoutMinDays-$blackoutMaxDays days"

    fun harvestLabel(): String = "${trim(harvestMinIn)}-${trim(harvestMaxIn)} in"

    private fun trim(v: Double): String =
        if (v == v.toInt().toDouble()) v.toInt().toString() else v.toString()
}

object VarietyGuides {
    private val guides = listOf(
        VarietyGuide("Radish", 2, 3, 2.0, 3.0),
        VarietyGuide("Cress", 1, 2, 1.0, 2.0),
        VarietyGuide("Arugula", 2, 3, 2.0, 3.0),
        VarietyGuide("Mustard", 2, 3, 2.0, 3.0),
        VarietyGuide("Cabbage", 2, 3, 2.0, 3.0),
        VarietyGuide("Kohlrabi", 2, 3, 2.0, 3.0),
        VarietyGuide("Broccoli", 2, 3, 2.0, 3.0),
        VarietyGuide("Kale", 2, 3, 2.0, 3.0),
        VarietyGuide("Sunflower", 3, 4, 3.0, 4.0),
        VarietyGuide("Pea Shoots", 2, 3, 3.0, 5.0),
        VarietyGuide("Cilantro", 3, 5, 2.0, 3.0),
        VarietyGuide("Basil", 4, 6, 2.0, 3.0)
    )

    private val fallback = VarietyGuide("Default", 2, 3, 1.0, 3.0)

    fun forVariety(name: String): VarietyGuide =
        guides.firstOrNull { it.variety.equals(name, ignoreCase = true) }
            ?: fallback.copy(variety = name)

    /**
     * Rough expected height (inches) on [dayIndex] of a [daysToHarvest]-day
     * grow: linear from 0 to the harvest midpoint, capped at the harvest
     * maximum. Day 0 (just seeded) is 0 — too early to measure.
     */
    fun expectedHeightInches(
        guide: VarietyGuide,
        dayIndex: Int,
        daysToHarvest: Int
    ): Double {
        if (dayIndex <= 0 || daysToHarvest <= 0) return 0.0
        return (guide.harvestMidIn * dayIndex / daysToHarvest)
            .coerceIn(0.0, guide.harvestMaxIn)
    }
}
