package com.farrah.sproutvision.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fresh-install contract for opt-in sample data: "Load demo trays" plants
 * the bundled basil + kale only into an empty table, and never touches
 * existing user trays.
 */
class LoadDemoTraysTest {

    private class FakeDao : SproutDao {
        val trays = mutableListOf<TrayEntity>()
        val stages = mutableListOf<StageEntryEntity>()
        private var nextId = 1L

        override fun observeTrays(): Flow<List<TrayEntity>> = emptyFlow()
        override suspend fun getTraysOnce(): List<TrayEntity> = trays.toList()
        override fun observeTray(id: Long): Flow<TrayEntity?> = emptyFlow()
        override suspend fun trayCount(): Int = trays.size
        override suspend fun insertTray(tray: TrayEntity): Long {
            val id = nextId++
            trays += tray.copy(id = id)
            return id
        }
        override suspend fun updateTray(tray: TrayEntity) {
            trays.replaceAll { if (it.id == tray.id) tray else it }
        }
        override suspend fun deleteTray(tray: TrayEntity) {
            trays.removeIf { it.id == tray.id }
        }
        override suspend fun deleteAllTrays() = trays.clear()
        override fun observeStages(trayId: Long): Flow<List<StageEntryEntity>> = emptyFlow()
        override suspend fun insertStage(entry: StageEntryEntity) {
            stages += entry
        }
        override suspend fun updateStage(entry: StageEntryEntity): Unit =
            throw UnsupportedOperationException()
        override suspend fun deleteStage(entry: StageEntryEntity): Unit =
            throw UnsupportedOperationException()
        override suspend fun getStagesOnce(trayId: Long): List<StageEntryEntity> =
            stages.filter { it.trayId == trayId }
        override suspend fun deleteAllStages() = stages.clear()
        override fun observeScans(trayId: Long): Flow<List<ScanEntity>> = emptyFlow()
        override suspend fun getScansOnce(trayId: Long): List<ScanEntity> =
            throw UnsupportedOperationException()
        override suspend fun insertScan(scan: ScanEntity): Long =
            throw UnsupportedOperationException()
        override suspend fun deleteScan(scan: ScanEntity): Unit =
            throw UnsupportedOperationException()
        override suspend fun deleteScansForTray(trayId: Long): Unit =
            throw UnsupportedOperationException()
        override suspend fun insert(record: ScanRecordEntity): Long =
            throw UnsupportedOperationException()
        override fun getForTray(trayId: Long): Flow<List<ScanRecordEntity>> = emptyFlow()
        override suspend fun deleteScanRecordsForTray(trayId: Long): Unit =
            throw UnsupportedOperationException()
        override suspend fun deleteScanRecordsAt(trayId: Long, timestamp: Long): Unit =
            throw UnsupportedOperationException()
        override fun observeBaseline(trayId: Long): Flow<BaselineEntity?> = emptyFlow()
        override suspend fun upsertBaseline(baseline: BaselineEntity): Unit =
            throw UnsupportedOperationException()
        override suspend fun deleteBaselineForTray(trayId: Long): Unit =
            throw UnsupportedOperationException()
        override fun observeTrials(): Flow<List<VarietyTrialEntity>> = emptyFlow()
        override suspend fun upsertTrial(trial: VarietyTrialEntity): Unit =
            throw UnsupportedOperationException()
        override fun observeAllScans(): Flow<List<ScanEntity>> = emptyFlow()
        override suspend fun getAllScansOnce(): List<ScanEntity> =
            throw UnsupportedOperationException()
        override fun observeHerbarium(): Flow<List<HerbariumEntity>> = emptyFlow()
        override suspend fun insertHerbarium(entry: HerbariumEntity): Long =
            throw UnsupportedOperationException()
        override suspend fun deleteHerbarium(entry: HerbariumEntity): Unit =
            throw UnsupportedOperationException()
        override suspend fun getHerbariumOnce(): List<HerbariumEntity> =
            throw UnsupportedOperationException()
        override suspend fun updateHerbarium(entry: HerbariumEntity): Unit =
            throw UnsupportedOperationException()
        override fun observeNutrients(): Flow<List<NutrientProduct>> = emptyFlow()
        override suspend fun insertNutrient(product: NutrientProduct): Long =
            throw UnsupportedOperationException()
        override suspend fun updateNutrient(product: NutrientProduct): Unit =
            throw UnsupportedOperationException()
        override suspend fun deleteNutrient(product: NutrientProduct): Unit =
            throw UnsupportedOperationException()
        override fun observeSchedules(trayId: Long): Flow<List<FeedingSchedule>> = emptyFlow()
        override suspend fun insertSchedule(schedule: FeedingSchedule): Long =
            throw UnsupportedOperationException()
        override suspend fun deleteSchedule(schedule: FeedingSchedule): Unit =
            throw UnsupportedOperationException()
        override fun observeFeedings(trayId: Long): Flow<List<FeedingEntry>> = emptyFlow()
        override suspend fun insertFeeding(entry: FeedingEntry): Long =
            throw UnsupportedOperationException()
        override suspend fun insertPhEcReading(reading: PhEcReading): Long =
            throw UnsupportedOperationException()
        override fun observePhEcReadings(trayId: Long): Flow<List<PhEcReading>> = emptyFlow()
        override suspend fun updatePhEcTargets(
            trayId: Long,
            phLow: Double?,
            phHigh: Double?,
            ecLow: Double?,
            ecHigh: Double?
        ): Unit = throw UnsupportedOperationException()
    }

    @Test
    fun `empty table receives the demo trays`() = runBlocking {
        val dao = FakeDao()
        val repository = TrayRepository(dao)

        repository.loadDemoTrays(DefaultTrays.seed())

        assertEquals(listOf("Basil", "Kale"), dao.trays.map { it.name })
        assertEquals(2, dao.trays.size)
        // Each demo tray carries its SEEDED history entry.
        assertEquals(2, dao.stages.size)
        assertTrue(dao.stages.all { it.stage == GrowthStage.SEEDED })
    }

    @Test
    fun `existing trays are never clobbered`() = runBlocking {
        val dao = FakeDao()
        val repository = TrayRepository(dao)
        dao.insertTray(
            TrayEntity(
                name = "Radish",
                variety = "Radish",
                seededAtEpochMs = 1791651600000L,
                expectedDaysToHarvest = 7
            )
        )

        repository.loadDemoTrays(DefaultTrays.seed())

        assertEquals(listOf("Radish"), dao.trays.map { it.name })
        assertTrue(dao.stages.isEmpty())
    }
}
