package com.farrah.sproutvision.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards MIGRATION_7_8 (expectedDaysToHarvest becomes nullable). SQLite
 * cannot ALTER a column's nullability, and the classic rebuild (DROP
 * TABLE) is unsafe here: trays is the FK parent of stage_entries,
 * scans, scan_records, and baselines, and Room runs migrations inside
 * a transaction where PRAGMA foreign_keys=OFF is a documented no-op —
 * verified empirically that DROP TABLE then cascade-wipes the children.
 * So the migration uses ADD COLUMN + copy + DROP/RENAME COLUMN (needs
 * SQLite 3.35+, fine on minSdk 32), which never drops the table.
 */
class MigrationExpectedDaysTest {

    @Test
    fun `migration never drops the trays table`() {
        val drops = SproutDatabase.MIGRATION_7_8_DDL.filter {
            it.trimStart().startsWith("DROP TABLE", ignoreCase = true)
        }
        assertTrue(
            "MIGRATION_7_8 must not DROP TABLE trays (would cascade-wipe " +
                "child rows), found: $drops",
            drops.isEmpty()
        )
    }

    @Test
    fun `migration copies values into a nullable column`() {
        val ddl = SproutDatabase.MIGRATION_7_8_DDL
        assertEquals(4, ddl.size)
        assertTrue(ddl[0].contains("ADD COLUMN", ignoreCase = true))
        assertTrue(ddl[1].contains("UPDATE", ignoreCase = true))
        assertTrue(ddl[2].contains("DROP COLUMN", ignoreCase = true))
        assertTrue(ddl[3].contains("RENAME COLUMN", ignoreCase = true))
    }
}
