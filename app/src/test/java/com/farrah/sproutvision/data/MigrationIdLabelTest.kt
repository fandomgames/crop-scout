package com.farrah.sproutvision.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the ID-label migration: three nullable anchor columns on the
 * herbarium table. Nullable ADD COLUMNs only, so old entries read back
 * null anchors (unpinned, never a fake pose) — no backfill, no drops.
 */
class MigrationIdLabelTest {

    @Test
    fun `id-label migration adds three nullable anchor columns`() {
        val ddl = SproutDatabase.MIGRATION_10_11_DDL
        assertEquals(
            setOf(
                "ALTER TABLE `herbarium` ADD COLUMN `anchorX` REAL",
                "ALTER TABLE `herbarium` ADD COLUMN `anchorY` REAL",
                "ALTER TABLE `herbarium` ADD COLUMN `anchorZ` REAL"
            ),
            ddl.toSet()
        )
        // Additive only: no DROP/DELETE/INSERT/UPDATE statements.
        assertTrue(
            "MIGRATION_10_11 must not drop, delete, or backfill, found: $ddl",
            ddl.none {
                val head = it.substringBefore("(").uppercase()
                head.startsWith("DROP") || head.startsWith("DELETE") ||
                    head.startsWith("INSERT") || head.startsWith("UPDATE")
            }
        )
        assertTrue(
            "anchor columns must be nullable with no default, found: $ddl",
            ddl.none {
                it.contains("NOT NULL", ignoreCase = true) ||
                    it.contains("DEFAULT", ignoreCase = true)
            }
        )
    }
}
