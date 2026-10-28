package com.farrah.sproutvision.data

import com.farrah.sproutvision.analyze.AnalysisResult
import com.farrah.sproutvision.analyze.PlantIdentification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TrayRepository(private val dao: SproutDao) {

    fun observeTrays(): Flow<List<TrayEntity>> = dao.observeTrays()
    fun observeTray(id: Long): Flow<TrayEntity?> = dao.observeTray(id)
    fun observeStages(id: Long): Flow<List<StageEntryEntity>> = dao.observeStages(id)

    suspend fun addTray(
        name: String,
        variety: String,
        seededAt: Long,
        expectedDays: Int?
    ): Long {
        val id = dao.insertTray(
            TrayEntity(
                name = name,
                variety = variety,
                seededAtEpochMs = seededAt,
                expectedDaysToHarvest = expectedDays
            )
        )
        dao.insertStage(
            StageEntryEntity(
                trayId = id,
                stage = GrowthStage.SEEDED,
                timestampEpochMs = seededAt,
                note = "Tray created"
            )
        )
        return id
    }

    suspend fun advanceStage(tray: TrayEntity, note: String = "") {
        val next = tray.currentStage.next() ?: return
        dao.updateTray(tray.copy(currentStage = next))
        dao.insertStage(
            StageEntryEntity(
                trayId = tray.id,
                stage = next,
                timestampEpochMs = System.currentTimeMillis(),
                note = note
            )
        )
    }

    /** Moves the tray forward to a scan-detected stage (never backwards). */
    suspend fun applyDetectedStage(tray: TrayEntity, detected: GrowthStage) {
        if (detected.order > tray.currentStage.order) {
            dao.updateTray(tray.copy(currentStage = detected))
            dao.insertStage(
                StageEntryEntity(
                    trayId = tray.id,
                    stage = detected,
                    timestampEpochMs = System.currentTimeMillis(),
                    note = "Detected by tray scan"
                )
            )
        }
    }

    suspend fun updateNotes(tray: TrayEntity, notes: String) {
        dao.updateTray(tray.copy(notes = notes))
    }

    suspend fun updateExpectedDays(tray: TrayEntity, days: Int?) {
        dao.updateTray(tray.copy(expectedDaysToHarvest = days))
    }

    suspend fun setGrowIntent(tray: TrayEntity, intent: GrowIntent) {
        dao.updateTray(tray.copy(growIntent = intent))
    }

    suspend fun saveAnalysis(
        tray: TrayEntity,
        result: AnalysisResult,
        photoPath: String? = null,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        val now = timestampMs
        dao.insertScan(
            ScanEntity(
                trayId = tray.id,
                timestampEpochMs = now,
                variety = result.variety,
                stage = result.stage,
                healthScore = result.healthScore,
                coveragePercent = result.coveragePercent,
                issues = result.issues.joinToString("; "),
                recommendation = result.recommendation,
                isDemo = result.isDemo,
                modelsUsed = result.modelsUsed,
                samCoverage = result.samCoverage,
                imageHash = result.imageHash
            )
        )
        dao.insert(
            ScanRecordEntity(
                trayId = tray.id,
                timestamp = now,
                healthPct = result.healthScore,
                coveragePct = result.coveragePercent,
                issues = result.issues.joinToString("; "),
                photoPath = photoPath,
                modelsUsed = result.modelsUsed,
                samCoverage = result.samCoverage,
                imageHash = result.imageHash
            )
        )
        dao.updateTray(
            tray.copy(
                lastHealthScore = result.healthScore,
                lastCoveragePercent = result.coveragePercent,
                lastIssues = result.issues.joinToString("; "),
                lastRecommendation = result.recommendation,
                lastAnalyzedAtEpochMs = now,
                lastAnalyzedByDemo = result.isDemo
            )
        )
    }

    suspend fun deleteTray(tray: TrayEntity) = dao.deleteTray(tray)

    /** Clears the stored analysis snapshot and all scan history. */
    suspend fun clearAnalysis(tray: TrayEntity) {
        dao.deleteScansForTray(tray.id)
        dao.deleteScanRecordsForTray(tray.id)
        dao.updateTray(
            tray.copy(
                lastHealthScore = null,
                lastCoveragePercent = null,
                lastIssues = "",
                lastRecommendation = "",
                lastAnalyzedAtEpochMs = null,
                lastAnalyzedByDemo = true
            )
        )
    }

    fun observeScans(trayId: Long): Flow<List<ScanEntity>> = dao.observeScans(trayId)

    fun observeScanRecords(trayId: Long): Flow<List<ScanRecordEntity>> = dao.getForTray(trayId)

    /** pH/EC readings newest-first (ordering lives in latestFirst). */
    fun observePhEcReadings(trayId: Long): Flow<List<PhEcReading>> =
        dao.observePhEcReadings(trayId).map(::latestFirst)

    /** Appends a reading; null when empty (nothing to save). */
    suspend fun savePhEcReading(
        trayId: Long,
        ph: Double?,
        ec: Double?,
        timestampMs: Long = System.currentTimeMillis(),
        note: String = ""
    ): Long? {
        if (!canSavePhEc(ph, ec)) return null
        return dao.insertPhEcReading(
            PhEcReading(
                trayId = trayId,
                timestampEpochMs = timestampMs,
                ph = ph,
                ec = ec,
                note = note
            )
        )
    }

    suspend fun setPhEcTargets(
        trayId: Long,
        low: Double?,
        high: Double?,
        ecLow: Double?,
        ecHigh: Double?
    ) = dao.updatePhEcTargets(trayId, low, high, ecLow, ecHigh)

    fun observeAllScans(): Flow<List<ScanEntity>> = dao.observeAllScans()

    suspend fun getAllScansOnce(): List<ScanEntity> = dao.getAllScansOnce()

    suspend fun getTraysOnce(): List<TrayEntity> = dao.getTraysOnce()

    /**
     * Pins the tray's floating card at a world-space pose. Re-reads the row
     * first so a placement save never clobbers a concurrent scan snapshot.
     */
    suspend fun setTrayAnchor(trayId: Long, x: Float, y: Float, z: Float) {
        val tray = dao.getTraysOnce().find { it.id == trayId } ?: return
        dao.updateTray(tray.copy(anchorX = x, anchorY = y, anchorZ = z))
    }

    suspend fun clearTrayAnchor(trayId: Long) {
        val tray = dao.getTraysOnce().find { it.id == trayId } ?: return
        dao.updateTray(tray.copy(anchorX = null, anchorY = null, anchorZ = null))
    }

    // ---- Baselines ----

    fun observeBaseline(trayId: Long): Flow<BaselineEntity?> = dao.observeBaseline(trayId)

    /** Snapshots the tray's current analysis as its "before" baseline. No-op when never scanned. */
    suspend fun snapshotBaseline(tray: TrayEntity): Boolean {
        val health = tray.lastHealthScore ?: return false
        dao.upsertBaseline(
            BaselineEntity(
                trayId = tray.id,
                timestampEpochMs = tray.lastAnalyzedAtEpochMs ?: System.currentTimeMillis(),
                healthScore = health,
                coveragePercent = tray.lastCoveragePercent ?: 0,
                issues = tray.lastIssues
            )
        )
        return true
    }

    // ---- Variety trials ----

    fun observeTrials(): Flow<List<VarietyTrialEntity>> = dao.observeTrials()

    /** Normalizes the variety name so "Radish" and "radish" share one trial row. */
    suspend fun saveTrial(
        variety: String,
        daysToHarvest: Int?,
        yieldGrams: Int?,
        tasteRating: Int?
    ) {
        val key = varietyKey(variety)
        if (key.isBlank()) return
        dao.upsertTrial(
            VarietyTrialEntity(
                varietyKey = key,
                displayName = variety.trim(),
                daysToHarvest = daysToHarvest,
                yieldGrams = yieldGrams,
                tasteRating = tasteRating?.coerceIn(1, 5)
            )
        )
    }



    /**
     * Deletes one scan. If it was the tray's latest, the snapshot rolls
     * back to the newest remaining scan (cleared if none remain).
     */
    suspend fun deleteScan(tray: TrayEntity, scan: ScanEntity) {
        dao.deleteScan(scan)
        dao.deleteScanRecordsAt(tray.id, scan.timestampEpochMs)
        val snapshotAt = tray.lastAnalyzedAtEpochMs
        if (snapshotAt != null && scan.timestampEpochMs >= snapshotAt) {
            val latest = dao.getScansOnce(tray.id).maxByOrNull { it.timestampEpochMs }
            if (latest == null) {
                clearAnalysis(tray)
            } else {
                dao.updateTray(
                    tray.copy(
                        lastHealthScore = latest.healthScore,
                        lastCoveragePercent = latest.coveragePercent,
                        lastIssues = latest.issues,
                        lastRecommendation = latest.recommendation,
                        lastAnalyzedAtEpochMs = latest.timestampEpochMs,
                        lastAnalyzedByDemo = latest.isDemo
                    )
                )
            }
        }
    }

    /** Creates or updates one stage-log entry (note, date, stage). */
    suspend fun upsertStageEntry(
        tray: TrayEntity,
        existing: StageEntryEntity?,
        stage: GrowthStage,
        timestampEpochMs: Long,
        note: String
    ) {
        if (existing == null) {
            dao.insertStage(
                StageEntryEntity(
                    trayId = tray.id,
                    stage = stage,
                    timestampEpochMs = timestampEpochMs,
                    note = note
                )
            )
        } else {
            dao.updateStage(
                existing.copy(
                    stage = stage,
                    timestampEpochMs = timestampEpochMs,
                    note = note
                )
            )
        }
        if (stage.order > tray.currentStage.order) {
            dao.updateTray(tray.copy(currentStage = stage))
        }
    }

    /**
     * Deletes one stage-log entry. If it was the tray's current stage,
     * the tray rolls back to the latest remaining entry (SEEDED if none).
     */
    suspend fun deleteStageEntry(tray: TrayEntity, entry: StageEntryEntity) {
        dao.deleteStage(entry)
        if (entry.stage == tray.currentStage) {
            val rolledBack = dao.getStagesOnce(tray.id)
                .maxByOrNull { it.stage.order }?.stage ?: GrowthStage.SEEDED
            if (rolledBack != tray.currentStage) {
                dao.updateTray(tray.copy(currentStage = rolledBack))
            }
        }
    }

    suspend fun clearAll() {
        dao.deleteAllStages()
        dao.deleteAllTrays()
    }

    companion object {
        fun varietyKey(variety: String): String = variety.trim().lowercase()
    }

    /**
     * Opt-in sample data ("Load demo trays"): loads the bundled basil + kale
     * trays only into an empty table. Never clears anything — when the user
     * already has trays this is a no-op, so it can't clobber real data.
     */
    suspend fun loadDemoTrays(seed: List<SeedTray>) {
        if (dao.trayCount() > 0) return
        insertSeedTrays(seed)
    }

    // ---- Herbarium: saved wild-plant identifications (explicit Save only) ----

    fun observeHerbarium(): Flow<List<HerbariumEntity>> = dao.observeHerbarium()

    suspend fun saveHerbariumEntry(
        result: PlantIdentification,
        photoPath: String?,
        timestampMs: Long,
        locationNote: String
    ): Long =
        dao.insertHerbarium(herbariumEntryOf(result, photoPath, timestampMs, locationNote))

    suspend fun deleteHerbariumEntry(entry: HerbariumEntity) = dao.deleteHerbarium(entry)

    /**
     * Pins the entry's floating ID label at a world-space pose. Re-reads
     * the row first so a placement save never clobbers a concurrent edit.
     * Touches only the herbarium row — never tray data.
     */
    suspend fun setHerbariumAnchor(entryId: Long, x: Float, y: Float, z: Float) {
        val entry = dao.getHerbariumOnce().find { it.id == entryId } ?: return
        dao.updateHerbarium(entry.copy(anchorX = x, anchorY = y, anchorZ = z))
    }

    suspend fun clearHerbariumAnchor(entryId: Long) {
        val entry = dao.getHerbariumOnce().find { it.id == entryId } ?: return
        dao.updateHerbarium(entry.copy(anchorX = null, anchorY = null, anchorZ = null))
    }

    // ---- Nutrients: inventory, per-tray schedules, feeding log ----

    fun observeNutrients(): Flow<List<NutrientProduct>> = dao.observeNutrients()

    suspend fun saveNutrient(product: NutrientProduct): Long =
        if (product.id == 0L) dao.insertNutrient(product)
        else {
            dao.updateNutrient(product)
            product.id
        }

    suspend fun deleteNutrient(product: NutrientProduct) = dao.deleteNutrient(product)

    fun observeSchedules(trayId: Long): Flow<List<FeedingSchedule>> =
        dao.observeSchedules(trayId)

    suspend fun saveSchedule(
        trayId: Long,
        nutrientId: Long,
        strengthPercent: Int,
        startStage: String?
    ): Long = dao.insertSchedule(
        FeedingSchedule(
            trayId = trayId,
            nutrientId = nutrientId,
            strengthPercent = strengthPercent,
            startStage = startStage
        )
    )

    suspend fun deleteSchedule(schedule: FeedingSchedule) = dao.deleteSchedule(schedule)

    fun observeFeedings(trayId: Long): Flow<List<FeedingEntry>> =
        dao.observeFeedings(trayId).map(::feedingLatestFirst)

    /**
     * Logs a feeding with the dose computed by the pure [computeDose] —
     * never an LLM. Null when nothing loggable (no nutrient picked).
     */
    suspend fun logFeeding(
        trayId: Long,
        product: NutrientProduct?,
        strengthPercent: Int,
        reservoirLiters: Double,
        timestampMs: Long = System.currentTimeMillis()
    ): Long? {
        if (product == null || !canLogFeeding(product.id, reservoirLiters)) return null
        return dao.insertFeeding(
            FeedingEntry(
                trayId = trayId,
                nutrientId = product.id,
                timestampEpochMs = timestampMs,
                strengthPercent = strengthPercent,
                reservoirLiters = reservoirLiters,
                doseAmount = computeDose(
                    dosageRuleOf(product), reservoirLiters, strengthPercent
                ),
                doseUnit = product.doseUnit.ifBlank { "ml" }
            )
        )
    }

    private suspend fun insertSeedTrays(seed: List<SeedTray>) {
        for (tray in seed) {
            val id = dao.insertTray(
                TrayEntity(
                    name = tray.name,
                    variety = tray.variety,
                    seededAtEpochMs = tray.seededAtEpochMs,
                    expectedDaysToHarvest = tray.expectedDaysToHarvest,
                    currentStage = tray.currentStage,
                    notes = tray.notes
                )
            )
            for (s in tray.stages) {
                dao.insertStage(
                    StageEntryEntity(
                        trayId = id,
                        stage = s.stage,
                        timestampEpochMs = s.timestampEpochMs,
                        note = s.note
                    )
                )
            }
        }
    }
}
