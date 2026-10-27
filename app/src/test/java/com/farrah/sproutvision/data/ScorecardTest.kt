package com.farrah.sproutvision.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the crop trial scorecard aggregates: germination from each tray's
 * first scan, case-insensitive variety merging, the composite leaderboard
 * order, and trial-only varieties with no trays yet.
 */
class ScorecardTest {

    private fun tray(id: Long, variety: String) = TrayEntity(
        id = id,
        name = "Tray $id",
        variety = variety,
        seededAtEpochMs = 0L,
        expectedDaysToHarvest = 10
    )

    private fun scan(trayId: Long, at: Long, health: Int, coverage: Int) = ScanEntity(
        trayId = trayId,
        timestampEpochMs = at,
        variety = "Radish",
        stage = GrowthStage.LIGHT,
        healthScore = health,
        coveragePercent = coverage
    )

    @Test
    fun `germination averages each tray's first scan and varieties merge case-insensitively`() {
        val trays = listOf(tray(1, "Radish"), tray(2, "radish "))
        val scans = listOf(
            scan(1, 1_000L, 70, 80),
            scan(1, 2_000L, 80, 90),
            scan(2, 1_500L, 50, 60)
        )

        val cards = buildScorecards(trays, scans, emptyList())

        assertEquals(1, cards.size)
        val radish = cards.single()
        assertEquals("radish", radish.varietyKey)
        assertEquals(2, radish.trayCount)
        assertEquals(3, radish.scanCount)
        assertEquals(70, radish.germinationPct) // (80 + 60) / 2, later scans ignored
        assertEquals(67, radish.avgHealth) // (70 + 80 + 50) / 3 rounded
    }

    @Test
    fun `composite folds in trial data and sorts the leaderboard`() {
        val trays = listOf(tray(1, "Radish"), tray(3, "Basil"))
        val scans = listOf(scan(1, 1_000L, 70, 80))
        val trials = listOf(
            VarietyTrialEntity(
                varietyKey = "radish",
                displayName = "Radish",
                daysToHarvest = 8,
                yieldGrams = 120,
                tasteRating = 4
            )
        )

        val cards = buildScorecards(trays, scans, trials)

        assertEquals(2, cards.size)
        assertEquals("radish", cards[0].varietyKey)
        assertEquals("basil", cards[1].varietyKey)
        // 0.35*80 + 0.30*70 + 4*3 + min(120,100)*0.1 + max(0,14-8)
        assertEquals(77.0, cards[0].composite, 0.001)
        assertEquals(0.0, cards[1].composite, 0.0)
        assertNull(cards[1].germinationPct)
        assertNull(cards[1].avgHealth)
    }

    @Test
    fun `trial-only varieties appear before their trays are scanned`() {
        val trials = listOf(
            VarietyTrialEntity(
                varietyKey = "kale",
                displayName = "Kale",
                daysToHarvest = null,
                yieldGrams = null,
                tasteRating = 5
            )
        )

        val cards = buildScorecards(emptyList(), emptyList(), trials)

        assertEquals(1, cards.size)
        assertEquals("Kale", cards[0].displayName)
        assertEquals(0, cards[0].trayCount)
        assertEquals(15.0, cards[0].composite, 0.0) // taste 5 * 3, everything else missing
    }
}
