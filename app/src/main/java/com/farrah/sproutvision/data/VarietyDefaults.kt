package com.farrah.sproutvision.data

/** Typical days from seeding to harvest for common microgreen varieties. */
data class VarietyInfo(val name: String, val daysToHarvest: Int)

object VarietyDefaults {
    val all = listOf(
        VarietyInfo("Radish", 6),
        VarietyInfo("Cress", 7),
        VarietyInfo("Arugula", 8),
        VarietyInfo("Mustard", 8),
        VarietyInfo("Cabbage", 8),
        VarietyInfo("Kohlrabi", 8),
        VarietyInfo("Broccoli", 9),
        VarietyInfo("Kale", 10),
        VarietyInfo("Sunflower", 10),
        VarietyInfo("Pea Shoots", 12),
        VarietyInfo("Cilantro", 14),
        VarietyInfo("Basil", 18)
    )

    fun daysFor(name: String): Int =
        all.firstOrNull { it.name.equals(name, ignoreCase = true) }?.daysToHarvest ?: 10
}
