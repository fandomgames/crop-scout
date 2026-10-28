package com.farrah.sproutvision.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the pH/EC migration: one new readings table plus four nullable
 * target columns on trays. Nullable ADD COLUMNs only for existing
 * tables, so old trays read back null ranges (blank, never flagged) —
 * no backfill, no fake readings, no drops.
 */
class MigrationPhEcTest {

    @Test
    fun `ph-ec migration creates the readings table and four nullable columns`() {
        val ddl = SproutDatabase.MIGRATION_9_10_DDL
        assertEquals(
            setOf(
                "CREATE TABLE IF NOT EXISTS `ph_ec_readings` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`trayId` INTEGER NOT NULL, " +
                    "`timestampEpochMs` INTEGER NOT NULL, " +
                    "`ph` REAL, " +
                    "`ec` REAL, " +
                    "`note` TEXT NOT NULL, " +
                    "FOREIGN KEY(`trayId`) REFERENCES `trays`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
                "CREATE INDEX IF NOT EXISTS `index_ph_ec_readings_trayId_timestampEpochMs` " +
                    "ON `ph_ec_readings` (`trayId`, `timestampEpochMs`)",
                "ALTER TABLE `trays` ADD COLUMN `phTargetLow` REAL",
                "ALTER TABLE `trays` ADD COLUMN `phTargetHigh` REAL",
                "ALTER TABLE `trays` ADD COLUMN `ecTargetLow` REAL",
                "ALTER TABLE `trays` ADD COLUMN `ecTargetHigh` REAL"
            ),
            ddl.toSet()
        )
        // Additive only: CREATE/ALTER statements, no DROP/DELETE/INSERT/UPDATE
        // statements (the ON DELETE CASCADE clause inside CREATE TABLE is a
        // foreign-key action, not a destructive statement).
        assertTrue(
            "MIGRATION_9_10 must not drop, delete, or backfill, found: $ddl",
            ddl.none {
                val head = it.substringBefore("(").uppercase()
                head.startsWith("DROP") || head.startsWith("DELETE") ||
                    head.startsWith("INSERT") || head.startsWith("UPDATE")
            }
        )
        assertTrue(
            "tray target columns must be nullable with no default, found: $ddl",
            ddl.filter { it.startsWith("ALTER TABLE") }.none {
                it.contains("NOT NULL", ignoreCase = true) ||
                    it.contains("DEFAULT", ignoreCase = true)
            }
        )
    }
}
