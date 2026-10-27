package com.farrah.sproutvision.data

/** What the grower intends for a tray: quick microgreens or a full grow-out. */
enum class GrowIntent(val displayName: String) {
    MICROGREEN("Microgreens"),
    GROW_OUT("Grow out");

    companion object {
        fun fromName(name: String): GrowIntent =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MICROGREEN
    }
}
