package com.farrah.sproutvision.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The bundled sample trays (basil + kale), built in code. Opt-in only via
 * Settings → Load demo trays — never planted automatically.
 */
object DefaultTrays {
    private val ZONE_CHICAGO: ZoneId = ZoneId.of("America/Chicago")

    /** Epoch millis for noon America/Chicago on the given date. */
    fun noonChicagoMillis(date: LocalDate): Long =
        date.atTime(LocalTime.NOON).atZone(ZONE_CHICAGO).toInstant().toEpochMilli()

    fun seed(): List<SeedTray> {
        val basilSeeded = noonChicagoMillis(LocalDate.parse("2026-09-28"))
        val kaleSeeded = noonChicagoMillis(LocalDate.parse("2026-10-10"))
        return listOf(
            SeedTray(
                name = "Basil",
                variety = "Basil",
                seededAtEpochMs = basilSeeded,
                expectedDaysToHarvest = 18,
                notes = "Patchy germination; the seedlings that came up are healthy.",
                currentStage = GrowthStage.LIGHT,
                stages = listOf(
                    SeedStage(GrowthStage.SEEDED, basilSeeded, "Seeded")
                )
            ),
            SeedTray(
                name = "Kale",
                variety = "Kale",
                seededAtEpochMs = kaleSeeded,
                expectedDaysToHarvest = 10,
                notes = "Under blackout as of Oct 12.",
                currentStage = GrowthStage.BLACKOUT,
                stages = listOf(
                    SeedStage(GrowthStage.SEEDED, kaleSeeded, "Seeded")
                )
            )
        )
    }
}
