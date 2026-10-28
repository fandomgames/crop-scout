package com.farrah.sproutvision.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the verdict-trace migration: both scan tables gain exactly the
 * three nullable trace columns. Room validates the migrated schema
 * exactly — a missing or mistyped column aborts startup ("Migration
 * didn't properly handle"). Nullable ADD COLUMNs only, so pre-trace
 * rows read back nulls; no backfill, no drops.
 */
class MigrationTraceTest {

    @Test
    fun `trace migration adds the three columns to both scan tables`() {
        val ddl = SproutDatabase.MIGRATION_8_9_DDL
        assertEquals(
            setOf(
                "ALTER TABLE `scans` ADD COLUMN `modelsUsed` TEXT",
                "ALTER TABLE `scans` ADD COLUMN `samCoverage` INTEGER",
                "ALTER TABLE `scans` ADD COLUMN `imageHash` TEXT",
                "ALTER TABLE `scan_records` ADD COLUMN `modelsUsed` TEXT",
                "ALTER TABLE `scan_records` ADD COLUMN `samCoverage` INTEGER",
                "ALTER TABLE `scan_records` ADD COLUMN `imageHash` TEXT"
            ),
            ddl.toSet()
        )
        assertTrue(
            "MIGRATION_8_9 must be additive only, found: $ddl",
            ddl.none {
                it.contains("DROP", ignoreCase = true) ||
                    it.contains("CREATE", ignoreCase = true)
            }
        )
    }
}
