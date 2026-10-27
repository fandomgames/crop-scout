package com.farrah.sproutvision.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the herbarium migration against reintroducing the Oct 2026 startup
 * crash-loop: MIGRATION_6_7 once created `index_herbarium_timestampEpochMs`
 * while HerbariumEntity declares no indices, so Room aborted every launch
 * with "Migration didn't properly handle: herbarium".
 *
 * Room validates the migrated schema exactly, indices included. If
 * HerbariumEntity ever gains an index, add a NEW migration for it — do not
 * extend this one. (Column/type parity is covered by on-device upgrade
 * runs, not by this pure-JVM check.)
 */
class MigrationHerbariumTest {

    @Test
    fun `herbarium migration creates no indices`() {
        val created = SproutDatabase.MIGRATION_6_7_DDL.filter {
            it.trimStart().startsWith("CREATE INDEX", ignoreCase = true)
        }
        assertTrue(
            "MIGRATION_6_7 must not create indices (HerbariumEntity declares " +
                "none; Room aborts startup on any mismatch), found: $created",
            created.isEmpty()
        )
    }
}
