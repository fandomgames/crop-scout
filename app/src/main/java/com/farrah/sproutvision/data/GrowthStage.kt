package com.farrah.sproutvision.data

/** Microgreen grow lifecycle, in order. */
enum class GrowthStage(val displayName: String, val order: Int, val hint: String) {
    SEEDED("Seeded", 0, "Seeds sown on the tray"),
    GERMINATION("Germination", 1, "Seeds sprouting — keep humid"),
    BLACKOUT("Blackout", 2, "Weighted blackout dome on"),
    LIGHT("Light", 3, "Under lights — water from below"),
    HARVEST("Harvest", 4, "Ready to cut");

    fun next(): GrowthStage? = entries.getOrNull(order + 1)

    /**
     * True when advancing from [this] would mark the tray harvested.
     * Harvested trays leave the working HUD, so that step always asks
     * for inline confirmation first.
     */
    fun needsHarvestConfirm(): Boolean = next() == HARVEST

    companion object {
        fun fromName(name: String): GrowthStage =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: SEEDED
    }
}
