package com.farrah.sproutvision.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the nutrient migration: three new tables (inventory, schedules,
 * feeding log). CREATEs only — existing trays gain no schedules or
 * entries, so no backfill is possible and none is attempted.
 */
class MigrationNutrientTest {

    @Test
    fun `nutrient migration creates three tables and no backfill`() {
        val ddl = SproutDatabase.MIGRATION_11_12_DDL
        assertEquals(
            setOf(
                "CREATE TABLE IF NOT EXISTS `nutrients` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`brand` TEXT NOT NULL, " +
                    "`npk` TEXT, " +
                    "`form` TEXT NOT NULL, " +
                    "`doseAmount` REAL NOT NULL, " +
                    "`doseUnit` TEXT NOT NULL, " +
                    "`dosePerLiters` REAL NOT NULL)",
                "CREATE TABLE IF NOT EXISTS `feeding_schedules` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`trayId` INTEGER NOT NULL, " +
                    "`nutrientId` INTEGER NOT NULL, " +
                    "`strengthPercent` INTEGER NOT NULL, " +
                    "`startStage` TEXT, " +
                    "FOREIGN KEY(`trayId`) REFERENCES `trays`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`nutrientId`) REFERENCES `nutrients`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
                "CREATE INDEX IF NOT EXISTS `index_feeding_schedules_trayId` " +
                    "ON `feeding_schedules` (`trayId`)",
                "CREATE INDEX IF NOT EXISTS `index_feeding_schedules_nutrientId` " +
                    "ON `feeding_schedules` (`nutrientId`)",
                "CREATE TABLE IF NOT EXISTS `feeding_entries` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`trayId` INTEGER NOT NULL, " +
                    "`nutrientId` INTEGER NOT NULL, " +
                    "`timestampEpochMs` INTEGER NOT NULL, " +
                    "`strengthPercent` INTEGER NOT NULL, " +
                    "`reservoirLiters` REAL NOT NULL, " +
                    "`doseAmount` REAL, " +
                    "`doseUnit` TEXT NOT NULL, " +
                    "`note` TEXT NOT NULL, " +
                    "FOREIGN KEY(`trayId`) REFERENCES `trays`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`nutrientId`) REFERENCES `nutrients`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
                "CREATE INDEX IF NOT EXISTS `index_feeding_entries_trayId_timestampEpochMs` " +
                    "ON `feeding_entries` (`trayId`, `timestampEpochMs`)"
            ),
            ddl.toSet()
        )
        // New tables only: no ALTER on trays, no INSERT/UPDATE backfill,
        // no drops or deletes.
        assertTrue(
            "MIGRATION_11_12 must only create new tables, found: $ddl",
            ddl.all {
                val head = it.substringBefore("(").uppercase()
                head.startsWith("CREATE TABLE") || head.startsWith("CREATE INDEX")
            }
        )
        assertTrue(
            "MIGRATION_11_12 must not touch the trays table, found: $ddl",
            ddl.none { it.contains("ALTER TABLE `trays`", ignoreCase = true) }
        )
    }
}
