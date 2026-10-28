package com.farrah.sproutvision.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nutrient v1: inventory CRUD, per-tray schedules, deterministic dose
 * math, and the newest-first feeding log. The dose is pure arithmetic
 * in [computeDose] — a plain function with no I/O, so no LLM call can
 * hide inside it; the tests below pin its outputs case by case.
 */
class NutrientTest {

    private class FakeDao : SproutDao {
        val products = mutableListOf<NutrientProduct>()
        val schedules = mutableListOf<FeedingSchedule>()
        val feedings = mutableListOf<FeedingEntry>()
        private var nextId = 1L

        override fun observeTrays(): Flow<List<TrayEntity>> = emptyFlow()
        override suspend fun getTraysOnce(): List<TrayEntity> =
            throw UnsupportedOperationException()
        override fun observeTray(id: Long): Flow<TrayEntity?> = emptyFlow()
        override suspend fun trayCount(): Int = throw UnsupportedOperationException()
        override suspend fun insertTray(tray: TrayEntity): Long =
            throw UnsupportedOperationException()
        override suspend fun updateTray(tray: TrayEntity): Unit =
            throw UnsupportedOperationException()
        override suspend fun deleteTray(tray: TrayEntity): Unit =
            throw UnsupportedOperationException()
        override suspend fun deleteAllTrays(): Unit = throw UnsupportedOperationException()
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
        override fun observeNutrients(): Flow<List<NutrientProduct>> = flowOf(products.toList())
        override suspend fun insertNutrient(product: NutrientProduct): Long {
            val id = nextId++
            products += product.copy(id = id)
            return id
        }
        override suspend fun updateNutrient(product: NutrientProduct) {
            products.replaceAll { if (it.id == product.id) product else it }
        }
        override suspend fun deleteNutrient(product: NutrientProduct) {
            products.removeIf { it.id == product.id }
        }
        override fun observeSchedules(trayId: Long): Flow<List<FeedingSchedule>> =
            flowOf(schedules.filter { it.trayId == trayId })
        override suspend fun insertSchedule(schedule: FeedingSchedule): Long {
            val id = nextId++
            schedules += schedule.copy(id = id)
            return id
        }
        override suspend fun deleteSchedule(schedule: FeedingSchedule) {
            schedules.removeIf { it.id == schedule.id }
        }
        override fun observeFeedings(trayId: Long): Flow<List<FeedingEntry>> =
            flowOf(feedings.filter { it.trayId == trayId })
        override suspend fun insertFeeding(entry: FeedingEntry): Long {
            val id = nextId++
            feedings += entry.copy(id = id)
            return id
        }
    }

    private fun floraGro() = NutrientProduct(
        name = "FloraGro",
        brand = "General Hydroponics",
        npk = "2-1-6",
        form = "Liquid",
        doseAmount = 5.0,
        doseUnit = "ml",
        dosePerLiters = 4.0
    )

    @Test
    fun `inventory create read update delete round-trips`() = runBlocking {
        val dao = FakeDao()
        val repository = TrayRepository(dao)

        val id = repository.saveNutrient(floraGro())
        assertEquals(listOf("FloraGro"), dao.products.map { it.name })

        val read = repository.observeNutrients().first()
        assertEquals(1, read.size)

        repository.saveNutrient(floraGro().copy(id = id, npk = "2-1-7"))
        assertEquals("2-1-7", dao.products.first { it.id == id }.npk)

        repository.deleteNutrient(dao.products.first { it.id == id })
        assertTrue(dao.products.isEmpty())
    }

    @Test
    fun `schedule saves with nutrient plus strength plus start condition`() = runBlocking {
        val dao = FakeDao()
        val repository = TrayRepository(dao)
        val nutrientId = repository.saveNutrient(floraGro())

        repository.saveSchedule(9L, nutrientId, 50, GrowthStage.LIGHT.name)

        val saved = dao.schedules.single()
        assertEquals(9L, saved.trayId)
        assertEquals(nutrientId, saved.nutrientId)
        assertEquals(50, saved.strengthPercent)
        assertEquals("LIGHT", saved.startStage)
        assertEquals(1, repository.observeSchedules(9L).first().size)
        assertTrue(repository.observeSchedules(10L).first().isEmpty())
    }

    @Test
    fun `dose math scales with reservoir and strength`() {
        val rule = DosageRule(5.0, "ml", 4.0)
        // Full strength, full rule volume: the rule amount.
        assertEquals(5.0, computeDose(rule, 4.0, 100)!!, 1e-9)
        // Half strength halves it.
        assertEquals(2.5, computeDose(rule, 4.0, 50)!!, 1e-9)
        // Double reservoir doubles it.
        assertEquals(10.0, computeDose(rule, 8.0, 100)!!, 1e-9)
        // Fractional case: 2.5 ml / 1 L at 50% in 3 L -> 3.75.
        assertEquals(3.75, computeDose(DosageRule(2.5, "g", 1.0), 3.0, 50)!!, 1e-9)
    }

    @Test
    fun `dose math edges - zero reservoir, missing rule, strength clamps`() {
        val rule = DosageRule(5.0, "ml", 4.0)
        // Zero reservoir needs nothing — a real 0.0, not a failure.
        assertEquals(0.0, computeDose(rule, 0.0, 100)!!, 1e-9)
        // 0% strength feeds nothing.
        assertEquals(0.0, computeDose(rule, 4.0, 0)!!, 1e-9)
        // Missing rule: nothing computable.
        assertNull(computeDose(null, 4.0, 100))
        // Broken rules: nothing computable.
        assertNull(computeDose(DosageRule(0.0, "ml", 4.0), 4.0, 100))
        assertNull(computeDose(DosageRule(5.0, "ml", 0.0), 4.0, 100))
        assertNull(computeDose(DosageRule(5.0, "", 4.0), 4.0, 100))
        // Negative reservoir is invalid input.
        assertNull(computeDose(rule, -1.0, 100))
        // Strength clamps to 0-200: negatives feed nothing, over-max caps.
        assertEquals(0.0, computeDose(rule, 4.0, -50)!!, 1e-9)
        assertEquals(10.0, computeDose(rule, 4.0, 200)!!, 1e-9)
        assertEquals(10.0, computeDose(rule, 4.0, 500)!!, 1e-9)
        // Deterministic: same inputs, same outputs, every time.
        assertEquals(computeDose(rule, 3.0, 75), computeDose(rule, 3.0, 75))
    }

    @Test
    fun `invalid products yield no rule`() {
        assertNull(dosageRuleOf(floraGro().copy(doseAmount = 0.0)))
        assertNull(dosageRuleOf(floraGro().copy(dosePerLiters = -2.0)))
        assertNull(dosageRuleOf(floraGro().copy(doseUnit = "  ")))
    }

    @Test
    fun `feeding log saves with computed dose, newest first`() = runBlocking {
        val dao = FakeDao()
        val repository = TrayRepository(dao)
        val product = floraGro().copy(id = repository.saveNutrient(floraGro()))

        repository.logFeeding(9L, product, 100, 4.0, timestampMs = 1000L)
        repository.logFeeding(9L, product, 50, 4.0, timestampMs = 2000L)
        // No nutrient picked: nothing logs.
        assertNull(repository.logFeeding(9L, null, 100, 4.0, timestampMs = 3000L))
        // Empty reservoir: nothing logs.
        assertNull(repository.logFeeding(9L, product, 100, 0.0, timestampMs = 3000L))

        val log = repository.observeFeedings(9L).first()
        assertEquals(listOf(2000L, 1000L), log.map { it.timestampEpochMs })
        assertEquals(2.5, log.first().doseAmount!!, 1e-9)
        assertEquals("ml", log.first().doseUnit)
        assertTrue(repository.observeFeedings(10L).first().isEmpty())
    }

    @Test
    fun `dose formats with unit, missing dose shows dash`() {
        assertEquals("12.5 ml", formatDose(12.5, "ml"))
        assertEquals("—", formatDose(null, "ml"))
    }

    @Test
    fun `steppers move in 25 percent and half liter steps`() {
        assertEquals(125, stepStrength(100, true))
        assertEquals(75, stepStrength(100, false))
        assertEquals(200, stepStrength(200, true))
        assertEquals(0, stepStrength(0, false))
        assertEquals(1.5, stepReservoir(1.0, true), 1e-9)
        assertEquals(0.5, stepReservoir(1.0, false), 1e-9)
        assertEquals(50.0, stepReservoir(50.0, true), 1e-9)
        assertEquals(0.0, stepReservoir(0.0, false), 1e-9)
    }
}
