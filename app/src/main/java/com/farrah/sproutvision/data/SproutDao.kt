package com.farrah.sproutvision.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SproutDao {
    @Query("SELECT * FROM trays ORDER BY seededAtEpochMs DESC")
    fun observeTrays(): Flow<List<TrayEntity>>

    @Query("SELECT * FROM trays ORDER BY seededAtEpochMs DESC")
    suspend fun getTraysOnce(): List<TrayEntity>

    @Query("SELECT * FROM trays WHERE id = :id")
    fun observeTray(id: Long): Flow<TrayEntity?>

    @Query("SELECT COUNT(*) FROM trays")
    suspend fun trayCount(): Int

    @Insert
    suspend fun insertTray(tray: TrayEntity): Long

    @Update
    suspend fun updateTray(tray: TrayEntity)

    @Delete
    suspend fun deleteTray(tray: TrayEntity)

    @Query("DELETE FROM trays")
    suspend fun deleteAllTrays()

    @Query("SELECT * FROM stage_entries WHERE trayId = :trayId ORDER BY timestampEpochMs ASC")
    fun observeStages(trayId: Long): Flow<List<StageEntryEntity>>

    @Insert
    suspend fun insertStage(entry: StageEntryEntity)

    @Update
    suspend fun updateStage(entry: StageEntryEntity)

    @Delete
    suspend fun deleteStage(entry: StageEntryEntity)

    @Query("SELECT * FROM stage_entries WHERE trayId = :trayId ORDER BY timestampEpochMs ASC")
    suspend fun getStagesOnce(trayId: Long): List<StageEntryEntity>

    @Query("DELETE FROM stage_entries")
    suspend fun deleteAllStages()

    @Query("SELECT * FROM scans WHERE trayId = :trayId ORDER BY timestampEpochMs DESC")
    fun observeScans(trayId: Long): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans WHERE trayId = :trayId ORDER BY timestampEpochMs DESC")
    suspend fun getScansOnce(trayId: Long): List<ScanEntity>

    @Insert
    suspend fun insertScan(scan: ScanEntity): Long

    @Delete
    suspend fun deleteScan(scan: ScanEntity)

    @Query("DELETE FROM scans WHERE trayId = :trayId")
    suspend fun deleteScansForTray(trayId: Long)

    @Insert
    suspend fun insert(record: ScanRecordEntity): Long

    @Query("SELECT * FROM scan_records WHERE trayId = :trayId ORDER BY timestamp DESC")
    fun getForTray(trayId: Long): Flow<List<ScanRecordEntity>>

    @Query("DELETE FROM scan_records WHERE trayId = :trayId")
    suspend fun deleteScanRecordsForTray(trayId: Long)

    @Query("DELETE FROM scan_records WHERE trayId = :trayId AND timestamp = :timestamp")
    suspend fun deleteScanRecordsAt(trayId: Long, timestamp: Long)

    // ---- Baselines: one "before" snapshot per tray ----

    @Query("SELECT * FROM baselines WHERE trayId = :trayId")
    fun observeBaseline(trayId: Long): Flow<BaselineEntity?>

    @Upsert
    suspend fun upsertBaseline(baseline: BaselineEntity)

    @Query("DELETE FROM baselines WHERE trayId = :trayId")
    suspend fun deleteBaselineForTray(trayId: Long)

    // ---- Variety trials: manual per-variety scorecard data ----

    @Query("SELECT * FROM variety_trials ORDER BY displayName ASC")
    fun observeTrials(): Flow<List<VarietyTrialEntity>>

    @Upsert
    suspend fun upsertTrial(trial: VarietyTrialEntity)

    // ---- Whole-history reads (CSV export, scorecard aggregates) ----

    @Query("SELECT * FROM scans ORDER BY timestampEpochMs ASC")
    fun observeAllScans(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans ORDER BY timestampEpochMs ASC")
    suspend fun getAllScansOnce(): List<ScanEntity>

    // ---- Herbarium: saved wild-plant identifications ----

    @Query("SELECT * FROM herbarium ORDER BY timestampEpochMs DESC")
    fun observeHerbarium(): Flow<List<HerbariumEntity>>

    @Insert
    suspend fun insertHerbarium(entry: HerbariumEntity): Long

    @Delete
    suspend fun deleteHerbarium(entry: HerbariumEntity)
}
