package com.farrah.sproutvision.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrayEntity::class,
        StageEntryEntity::class,
        ScanEntity::class,
        ScanRecordEntity::class,
        BaselineEntity::class,
        VarietyTrialEntity::class,
        HerbariumEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SproutDatabase : RoomDatabase() {
    abstract fun dao(): SproutDao

    companion object {
        @Volatile
        private var INSTANCE: SproutDatabase? = null

        fun getInstance(context: Context): SproutDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SproutDatabase::class.java,
                    "sprout_vision.db"
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
                ).build().also { INSTANCE = it }
            }

        /**
         * DDL executed by [MIGRATION_6_7]. Internal so the unit test can
         * assert index parity with [HerbariumEntity]: Room aborts startup
         * when a migration creates indices the entity doesn't declare.
         */
        internal val MIGRATION_6_7_DDL = listOf(
            "CREATE TABLE IF NOT EXISTS `herbarium` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`timestampEpochMs` INTEGER NOT NULL, " +
                "`commonName` TEXT NOT NULL, " +
                "`scientificName` TEXT NOT NULL, " +
                "`family` TEXT NOT NULL, " +
                "`confidence` TEXT NOT NULL, " +
                "`photoPath` TEXT, " +
                "`locationNote` TEXT NOT NULL, " +
                "`sun` TEXT NOT NULL, " +
                "`water` TEXT NOT NULL, " +
                "`soil` TEXT NOT NULL, " +
                "`hardinessZones` TEXT NOT NULL, " +
                "`bloomTime` TEXT NOT NULL, " +
                "`matureSize` TEXT NOT NULL, " +
                "`funFact` TEXT NOT NULL, " +
                "`betterPhotoTip` TEXT NOT NULL)"
        )

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_6_7_DDL.forEach(db::execSQL)
            }
        }

        /**
         * DDL executed by [MIGRATION_7_8]. Internal so the unit test can
         * assert it never drops the table. SQLite cannot ALTER a column's
         * nullability, and the classic rebuild (DROP TABLE) would
         * cascade-wipe trays' children (stage_entries, scans,
         * scan_records, baselines): Room runs migrations inside a
         * transaction, where PRAGMA foreign_keys=OFF is a documented
         * no-op — both verified empirically. So: add a nullable copy,
         * copy values, drop the old column, rename. DROP COLUMN needs
         * SQLite 3.35+ (minSdk 32 ships newer). Values are preserved;
         * only the nullability changes.
         */
        internal val MIGRATION_7_8_DDL = listOf(
            "ALTER TABLE `trays` ADD COLUMN `expectedDaysToHarvest_new` INTEGER",
            "UPDATE `trays` SET `expectedDaysToHarvest_new` = `expectedDaysToHarvest`",
            "ALTER TABLE `trays` DROP COLUMN `expectedDaysToHarvest`",
            "ALTER TABLE `trays` RENAME COLUMN `expectedDaysToHarvest_new` " +
                "TO `expectedDaysToHarvest`"
        )

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_7_8_DDL.forEach(db::execSQL)
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `trays` ADD COLUMN `growIntent` TEXT NOT NULL " +
                        "DEFAULT 'MICROGREEN'"
                )
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scans` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`trayId` INTEGER NOT NULL, " +
                        "`timestampEpochMs` INTEGER NOT NULL, " +
                        "`variety` TEXT NOT NULL, " +
                        "`stage` TEXT NOT NULL, " +
                        "`healthScore` INTEGER NOT NULL, " +
                        "`coveragePercent` INTEGER NOT NULL, " +
                        "`issues` TEXT NOT NULL, " +
                        "`recommendation` TEXT NOT NULL, " +
                        "`isDemo` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`trayId`) REFERENCES `trays`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_scans_trayId_timestampEpochMs` " +
                        "ON `scans` (`trayId`, `timestampEpochMs`)"
                )
                // Backfill one history row per tray with a stored analysis.
                db.execSQL(
                    "INSERT INTO scans (trayId, timestampEpochMs, variety, stage, " +
                        "healthScore, coveragePercent, issues, recommendation, isDemo) " +
                        "SELECT id, lastAnalyzedAtEpochMs, variety, currentStage, " +
                        "COALESCE(lastHealthScore, 0), COALESCE(lastCoveragePercent, 0), " +
                        "lastIssues, lastRecommendation, lastAnalyzedByDemo " +
                        "FROM trays WHERE lastAnalyzedAtEpochMs IS NOT NULL"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scan_records` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`trayId` INTEGER NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`healthPct` INTEGER NOT NULL, " +
                        "`coveragePct` INTEGER NOT NULL, " +
                        "`issues` TEXT NOT NULL, " +
                        "`photoPath` TEXT, " +
                        "FOREIGN KEY(`trayId`) REFERENCES `trays`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_scan_records_trayId_timestamp` " +
                        "ON `scan_records` (`trayId`, `timestamp`)"
                )
                // Backfill photo-less records from existing scan history.
                db.execSQL(
                    "INSERT INTO scan_records (trayId, timestamp, healthPct, " +
                        "coveragePct, issues, photoPath) " +
                        "SELECT trayId, timestampEpochMs, healthScore, " +
                        "coveragePercent, issues, NULL FROM scans"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `trays` ADD COLUMN `anchorX` REAL")
                db.execSQL("ALTER TABLE `trays` ADD COLUMN `anchorY` REAL")
                db.execSQL("ALTER TABLE `trays` ADD COLUMN `anchorZ` REAL")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `baselines` (" +
                        "`trayId` INTEGER NOT NULL, " +
                        "`timestampEpochMs` INTEGER NOT NULL, " +
                        "`healthScore` INTEGER NOT NULL, " +
                        "`coveragePercent` INTEGER NOT NULL, " +
                        "`issues` TEXT NOT NULL, " +
                        "PRIMARY KEY(`trayId`), " +
                        "FOREIGN KEY(`trayId`) REFERENCES `trays`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_baselines_trayId` " +
                        "ON `baselines` (`trayId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `variety_trials` (" +
                        "`varietyKey` TEXT NOT NULL, " +
                        "`displayName` TEXT NOT NULL, " +
                        "`daysToHarvest` INTEGER, " +
                        "`yieldGrams` INTEGER, " +
                        "`tasteRating` INTEGER, " +
                        "PRIMARY KEY(`varietyKey`))"
                )
            }
        }
    }
}
