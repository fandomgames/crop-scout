package com.farrah.sproutvision.data

import com.farrah.sproutvision.ui.trayCardVisible
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the floating-card remove/unlink: clearing the anchor unpins the
 * card but must never delete the tray, its scan snapshot, or its scan
 * history rows — re-placing restores the card over intact data.
 */
class TrayCardAnchorTest {

    private class FakeDao : SproutDao {
        val trays = mutableListOf<TrayEntity>()
        val scans = mutableListOf<ScanEntity>()
        private var nextId = 1L

        override fun observeTrays(): Flow<List<TrayEntity>> = emptyFlow()
        override suspend fun getTraysOnce(): List<TrayEntity> = trays.toList()
        override fun observeTray(id: Long): Flow<TrayEntity?> = emptyFlow()
        override suspend fun trayCount(): Int = trays.size
        override suspend fun insertTray(tray: TrayEntity): Long {
            val id = nextId++
            trays.add(tray.copy(id = id))
            return id
        }
        override suspend fun updateTray(tray: TrayEntity) {
            trays.replaceAll { if (it.id == tray.id) tray else it }
        }
        override suspend fun deleteTray(tray: TrayEntity) {
            trays.removeAll { it.id == tray.id }
        }
        override suspend fun deleteAllTrays(): Unit =
            throw UnsupportedOperationException()
        override fun observeStages(trayId: Long): Flow<List<StageEntryEntity>> = emptyFlow()
        override suspend fun insertStage(entry: StageEntryEntity): Unit =
            throw UnsupportedOperationException()
        override suspend fun updateStage(entry: StageEntryEntity): Unit =
            throw UnsupportedOperationException()
        override suspend fun deleteStage(entry: StageEntryEntity): Unit =
            throw UnsupportedOperationException()
        override suspend fun getStagesOnce(trayId: Long): List<StageEntryEntity> =
            throw UnsupportedOperationException()
        override suspend fun deleteAllStages(): Unit = throw UnsupportedOperationException()
        override fun observeScans(trayId: Long): Flow<List<ScanEntity>> = emptyFlow()
        override suspend fun insertPhEcReading(reading: PhEcReading): Long =
            throw UnsupportedOperationException()
        override fun observePhEcReadings(trayId: Long): Flow<List<PhEcReading>> = emptyFlow()
        override suspend fun updatePhEcTargets(
            trayId: Long,
            low: Double?,
            high: Double?,
            ecLow: Double?,
            ecHigh: Double?
        ): Unit = throw UnsupportedOperationException()
        override suspend fun getScansOnce(trayId: Long): List<ScanEntity> =
            scans.filter { it.trayId == trayId }
        override suspend fun insertScan(scan: ScanEntity): Long {
            val id = nextId++
            scans.add(scan.copy(id = id))
            return id
        }
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
        override suspend fun getAllScansOnce(): List<ScanEntity> = scans.toList()
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
    }

    private suspend fun seededDao(): Pair<FakeDao, Long> {
        val dao = FakeDao()
        val trayId = dao.insertTray(
            TrayEntity(
                name = "Basil",
                variety = "Genovese Basil",
                seededAtEpochMs = 1L,
                expectedDaysToHarvest = 21,
                notes = "rack A",
                lastHealthScore = 88,
                lastCoveragePercent = 70,
                lastIssues = "leggy",
                anchorX = 1f,
                anchorY = 2f,
                anchorZ = 3f
            )
        )
        dao.insertScan(
            ScanEntity(
                trayId = trayId,
                timestampEpochMs = 2L,
                variety = "Genovese Basil",
                stage = GrowthStage.LIGHT,
                healthScore = 88,
                coveragePercent = 70
            )
        )
        return dao to trayId
    }

    @Test
    fun `remove clears the anchor but tray snapshot and notes persist`() = runBlocking {
        val (dao, trayId) = seededDao()
        val repository = TrayRepository(dao)

        repository.clearTrayAnchor(trayId)

        val tray = dao.getTraysOnce().single { it.id == trayId }
        assertFalse(tray.hasAnchor())
        assertNull(tray.anchorX)
        assertNull(tray.anchorY)
        assertNull(tray.anchorZ)
        // Name, snapshot, and notes survive the unlink.
        assertEquals("Basil", tray.name)
        assertEquals("Genovese Basil", tray.variety)
        assertEquals("rack A", tray.notes)
        assertEquals(88, tray.lastHealthScore)
        assertEquals(70, tray.lastCoveragePercent)
        assertEquals("leggy", tray.lastIssues)
    }

    @Test
    fun `remove keeps scan history rows`() = runBlocking {
        val (dao, trayId) = seededDao()
        val repository = TrayRepository(dao)

        repository.clearTrayAnchor(trayId)

        val scans = dao.getScansOnce(trayId)
        assertEquals(1, scans.size)
        assertEquals(88, scans.single().healthScore)
    }

    @Test
    fun `unlinked card hides, re-place restores it`() = runBlocking {
        val (dao, trayId) = seededDao()
        val repository = TrayRepository(dao)

        repository.clearTrayAnchor(trayId)
        val unlinked = dao.getTraysOnce().single { it.id == trayId }
        assertFalse(trayCardVisible(unlinked.hasAnchor(), placementMode = false, cleanMode = false))

        repository.setTrayAnchor(trayId, 4f, 5f, 6f)
        val replaced = dao.getTraysOnce().single { it.id == trayId }
        assertTrue(replaced.hasAnchor())
        assertTrue(trayCardVisible(replaced.hasAnchor(), placementMode = false, cleanMode = false))
    }
}
