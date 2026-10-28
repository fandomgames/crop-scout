package com.farrah.sproutvision.data

import com.farrah.sproutvision.analyze.IdentifyConfidence
import com.farrah.sproutvision.analyze.PlantCareSheet
import com.farrah.sproutvision.analyze.PlantIdentification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Anchored ID label rules: the anchor round-trips through the herbarium
 * row (persist across sessions), labels render at saved anchors, tap
 * toggles expand, unpinned entries carry no fake anchor, and label
 * content shows names + confidence with a best-guess flag on LOW.
 *
 * Walk-around stability (the label staying put as the grower circles the
 * rack) cannot be proven by unit tests — verify in-headset at the rack.
 */
class IdLabelTest {

    private class FakeDao : SproutDao {
        val entries = mutableListOf<HerbariumEntity>()
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
        override suspend fun insertHerbarium(entry: HerbariumEntity): Long {
            val id = nextId++
            entries += entry.copy(id = id)
            return id
        }
        override suspend fun deleteHerbarium(entry: HerbariumEntity) {
            entries.removeIf { it.id == entry.id }
        }
        override suspend fun getHerbariumOnce(): List<HerbariumEntity> = entries.toList()
        override suspend fun updateHerbarium(entry: HerbariumEntity) {
            entries.replaceAll { if (it.id == entry.id) entry else it }
        }
    }

    private fun basil(confidence: IdentifyConfidence = IdentifyConfidence.HIGH) =
        PlantIdentification(
            commonName = "Basil",
            scientificName = "Ocimum basilicum",
            family = "Lamiaceae",
            confidence = confidence,
            care = PlantCareSheet(),
            betterPhotoTip = "Get closer."
        )

    @Test
    fun `pin anchor persists and re-reads across sessions`() = runBlocking {
        val dao = FakeDao()
        val repository = TrayRepository(dao)
        val entryId = repository.saveHerbariumEntry(basil(), null, 1L, "Rack A")

        repository.setHerbariumAnchor(entryId, 1f, 2f, 3f)

        // A fresh repository over the same store (a later session)
        // re-reads the saved anchor.
        val later = TrayRepository(dao)
        val reread = dao.getHerbariumOnce().first { it.id == entryId }
        assertTrue(reread.hasAnchor())
        assertEquals(1f, reread.anchorX)
        assertEquals(2f, reread.anchorY)
        assertEquals(3f, reread.anchorZ)

        later.clearHerbariumAnchor(entryId)
        assertFalse(dao.getHerbariumOnce().first { it.id == entryId }.hasAnchor())
    }

    @Test
    fun `anchored labels render, ghosts only while placing, clean hides all`() {
        assertTrue(idLabelVisible(hasAnchor = true, placingThis = false, cleanMode = false))
        assertTrue(idLabelVisible(hasAnchor = true, placingThis = true, cleanMode = false))
        assertTrue(idLabelVisible(hasAnchor = false, placingThis = true, cleanMode = false))
        assertFalse(idLabelVisible(hasAnchor = false, placingThis = false, cleanMode = false))
        assertFalse(idLabelVisible(hasAnchor = true, placingThis = false, cleanMode = true))
        assertFalse(idLabelVisible(hasAnchor = false, placingThis = true, cleanMode = true))
    }

    @Test
    fun `tap expands the label, second tap collapses`() {
        assertEquals(7L, nextExpandedLabel(null, 7L))
        assertNull(nextExpandedLabel(7L, 7L))
        assertEquals(9L, nextExpandedLabel(7L, 9L))
    }

    @Test
    fun `unpinned entries carry no fake anchor`() {
        val fresh = herbariumEntryOf(basil(), null, 1L, "Rack A")
        assertFalse(fresh.hasAnchor())
        assertNull(fresh.anchorX)
        assertNull(fresh.anchorY)
        assertNull(fresh.anchorZ)
    }

    @Test
    fun `label shows common plus short scientific plus confidence`() {
        val content = idLabelContentOf(herbariumEntryOf(basil(), null, 1L, ""))
        assertEquals("Basil", content.commonName)
        assertEquals("O. basilicum", content.scientificShort)
        assertEquals("High confidence", content.confidenceText)
        assertFalse(content.isBestGuess)
    }

    @Test
    fun `low confidence flags best guess, unknown reopens as low`() {
        val low = idLabelContentOf(
            herbariumEntryOf(basil(IdentifyConfidence.LOW), null, 1L, "")
        )
        assertEquals("Best guess — low confidence", low.confidenceText)
        assertTrue(low.isBestGuess)

        val unknown = idLabelContentOf(
            herbariumEntryOf(basil(), null, 1L, "").copy(confidence = "bogus")
        )
        assertTrue(unknown.isBestGuess)
    }

    @Test
    fun `short scientific form abbreviates the genus`() {
        assertEquals("O. basilicum", shortScientificName("Ocimum basilicum"))
        assertEquals("Basil", shortScientificName("Basil"))
        assertEquals("", shortScientificName("  "))
    }
}
