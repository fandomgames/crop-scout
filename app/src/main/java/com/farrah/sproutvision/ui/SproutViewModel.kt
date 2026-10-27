package com.farrah.sproutvision.ui

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.farrah.sproutvision.analyze.AnalysisResult
import com.farrah.sproutvision.analyze.DemoAnalyzer
import com.farrah.sproutvision.analyze.DemoPlantIdentifier
import com.farrah.sproutvision.analyze.DemoSegmenter
import com.farrah.sproutvision.analyze.GeminiAnalyzer
import com.farrah.sproutvision.analyze.MetaAnalyzer
import com.farrah.sproutvision.analyze.MetaPlantIdentifier
import com.farrah.sproutvision.analyze.MetaSamSegmenter
import com.farrah.sproutvision.analyze.Segmentation
import com.farrah.sproutvision.analyze.SegmentationException
import com.farrah.sproutvision.analyze.Segmenter
import com.farrah.sproutvision.analyze.overlayBitmap
import com.farrah.sproutvision.analyze.samFailureFlag
import com.farrah.sproutvision.analyze.PlantAnalyzer
import com.farrah.sproutvision.analyze.PlantIdentification
import com.farrah.sproutvision.analyze.selectPlantIdentifier
import com.farrah.sproutvision.data.BaselineEntity
import com.farrah.sproutvision.data.DefaultTrays
import com.farrah.sproutvision.data.GrowIntent
import com.farrah.sproutvision.data.GrowthStage
import com.farrah.sproutvision.data.HerbariumEntity
import com.farrah.sproutvision.data.ScanEntity
import com.farrah.sproutvision.data.ScanPhotoStore
import com.farrah.sproutvision.data.SproutDatabase
import com.farrah.sproutvision.data.StageEntryEntity
import com.farrah.sproutvision.data.TrayEntity
import com.farrah.sproutvision.data.TrayRepository
import com.farrah.sproutvision.data.WildPhotoStore
import com.farrah.sproutvision.data.VarietyDefaults
import com.farrah.sproutvision.data.VarietyScorecard
import com.farrah.sproutvision.data.VarietyTrialEntity
import com.farrah.sproutvision.data.buildScorecards
import com.farrah.sproutvision.reminders.ReminderScheduler
import com.farrah.sproutvision.settings.AppSettings
import com.farrah.sproutvision.settings.MAX_TIMING_SAMPLES
import com.farrah.sproutvision.settings.SettingsStore
import com.farrah.sproutvision.util.ScanCsvRow
import com.farrah.sproutvision.util.ScanExportResult
import com.farrah.sproutvision.util.ScanHistoryExport
import com.farrah.sproutvision.util.dayOn
import com.farrah.sproutvision.util.formatDateTime
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** One timestamped line of the live analyzing feed (elapsed since analyze start). */
data class AnalyzeEvent(val elapsedMs: Long, val text: String)

class SproutViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val repository = TrayRepository(SproutDatabase.getInstance(context).dao())
    private val settingsStore = SettingsStore(context)

    val trays: StateFlow<List<TrayEntity>> = repository.observeTrays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val allScans: StateFlow<List<ScanEntity>> = repository.observeAllScans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trials: StateFlow<List<VarietyTrialEntity>> = repository.observeTrials()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saved wild-plant identifications (herbarium grid). Written on Save taps only. */
    val herbarium: StateFlow<List<HerbariumEntity>> = repository.observeHerbarium()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Crop trial scorecard leaderboard, sorted by composite score. */
    val scorecards: StateFlow<List<VarietyScorecard>> = combine(
        trays, allScans, trials, ::buildScorecards
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var pendingAnalysis by mutableStateOf<AnalysisResult?>(null)
        private set
    var pendingPhoto by mutableStateOf<ByteArray?>(null)
        private set
    /** Where the pending capture's JPEG was saved (null when no photo bytes). */
    var pendingPhotoPath by mutableStateOf<String?>(null)
        private set
    /** Green tint over the counted plant pixels, drawn on the verdict photo. Null when unmeasured. */
    var pendingSamOverlay by mutableStateOf<Bitmap?>(null)
        private set
    /** Capture timestamp shared by the saved JPEG name and the scan record. */
    var pendingScanTimestamp by mutableStateOf(System.currentTimeMillis())
        private set
    var analyzing by mutableStateOf(false)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set
    /** What the current analysis is doing (shown under the spinner). */
    var analyzeStage by mutableStateOf("")
        private set
    /** Elapsed-realtime base for the analyzing timer. */
    var analyzeStartedAtMs by mutableStateOf(0L)
        private set
    /**
     * Rolling typical duration for the analyzer in use (demo/Gemini tracked
     * separately), shown next to the timer. Null until one scan of that kind
     * has completed this session.
     */
    var typicalAnalyzeMs by mutableStateOf<Long?>(null)
        private set
    private val recentDemoMs = ArrayDeque<Long>()
    private val recentGeminiMs = ArrayDeque<Long>()
    /** Whether the deques were seeded from DataStore (once per process). */
    private var seededTimings = false
    /** Last successful result per tray — feeds pre-capture framing coaching. */
    private val lastResults = mutableMapOf<Long, AnalysisResult>()

    fun lastResultFor(trayId: Long): AnalysisResult? = lastResults[trayId]

    // ---- Grow HUD state (shared by the HUD panel and the Manage panel) ----
    /** Which screen the compact HUD panel is showing. */
    var hudScreen by mutableStateOf<HudScreen>(HudScreen.Home)
    /** Whether the large tray-management panel is visible. */
    var manageVisible by mutableStateOf(false)
    /** Tray selected in the HUD (null = first tray). Survives panel switches. */
    var hudTrayId by mutableStateOf<Long?>(null)
    /**
     * Which HUD panel is currently visible (set by the activity's
     * visibility collector): the visible panel owns the camera, the
     * hidden panel's scanner stays inert so it can't disconnect the
     * live session. Snapshot state (not a flow) so both panels read
     * the same fresh value.
     */
    var verticalHudActive by mutableStateOf(false)
    /**
     * Clean mode: both panels hidden for B-roll, restored by tapping the
     * invisible tripwire panel (or back). Panel entities are never moved,
     * so they return to their exact prior positions.
     */
    var cleanMode by mutableStateOf(false)
    /**
     * Hides the 9:16 frame guide while staying in vertical mode (compact
     * HUD keeps showing). Session-only, and reset whenever the mode exits
     * so a fresh entry always shows the guide.
     */
    var hideViewfinder by mutableStateOf(false)
    /** Panel lock: disables the HUD's head-follow so panels stay world-locked. */
    var panelLocked by mutableStateOf(false)
    /** Card placement mode: ghosts appear, cards are grabbable, drops save. */
    var placementMode by mutableStateOf(false)
    /** Tray whose floating card is expanded (null = all compact). */
    var expandedCardTrayId by mutableStateOf<Long?>(null)
        private set
    /**
     * Ghost tap-to-drop request (tray id), consumed by the scene tick, which
     * reads the ghost's world pose and saves it. Single-shot by design.
     */
    var dropCardTrayId by mutableStateOf<Long?>(null)

    fun observeTray(id: Long) = repository.observeTray(id)
    fun observeStages(id: Long) = repository.observeStages(id)

    /**
     * One-shot Manage navigation request (consumed by ManageNav): lets the
     * HUD deep-link somewhere (e.g. empty state → Add tray) without the
     * panels sharing route state.
     */
    var manageOpenRequest by mutableStateOf<ManageScreen?>(null)

    /** Opt-in sample data: loads demo trays only when the table is empty. */
    fun loadDemoTrays() {
        viewModelScope.launch {
            repository.loadDemoTrays(DefaultTrays.seed())
        }
    }

    fun addTray(name: String, variety: String, seededAt: Long) {
        viewModelScope.launch {
            repository.addTray(name, variety, seededAt, null)
            settingsStore.addCustomVariety(
                variety,
                VarietyDefaults.all.map { it.name }.toSet()
            )
        }
    }

    fun updateExpectedDays(tray: TrayEntity, days: Int?) {
        viewModelScope.launch { repository.updateExpectedDays(tray, days) }
    }

    fun advanceStage(tray: TrayEntity) {
        viewModelScope.launch { repository.advanceStage(tray) }
    }

    fun saveNotes(tray: TrayEntity, notes: String) {
        viewModelScope.launch { repository.updateNotes(tray, notes) }
    }

    fun setGrowIntent(tray: TrayEntity, intent: GrowIntent) {
        viewModelScope.launch { repository.setGrowIntent(tray, intent) }
    }

    fun deleteTray(tray: TrayEntity) {
        viewModelScope.launch { repository.deleteTray(tray) }
    }

    fun clearAnalysis(tray: TrayEntity) {
        viewModelScope.launch { repository.clearAnalysis(tray) }
    }

    fun observeScans(trayId: Long) = repository.observeScans(trayId)

    fun observeScanRecords(trayId: Long) = repository.observeScanRecords(trayId)

    fun deleteScan(tray: TrayEntity, scan: ScanEntity) {
        viewModelScope.launch { repository.deleteScan(tray, scan) }
    }

    fun saveStageEntry(
        tray: TrayEntity,
        existing: StageEntryEntity?,
        stage: GrowthStage,
        timestampMs: Long,
        note: String
    ) {
        viewModelScope.launch {
            repository.upsertStageEntry(tray, existing, stage, timestampMs, note)
        }
    }

    fun deleteStageEntry(tray: TrayEntity, entry: StageEntryEntity) {
        viewModelScope.launch { repository.deleteStageEntry(tray, entry) }
    }

    fun clearAllData() {
        viewModelScope.launch { repository.clearAll() }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch { settingsStore.setApiKey(key.trim()) }
    }

    fun setMetaApiKey(key: String) {
        viewModelScope.launch { settingsStore.setMetaApiKey(key.trim()) }
    }

    fun setUseMetaAi(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setUseMetaAi(enabled) }
    }

    fun setUseSamMeasurement(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setUseSamMeasurement(enabled) }
    }

    fun setDemoMode(enabled: Boolean) {
        // Demo mode only switches the analyzer — it never plants trays.
        // Sample data is opt-in via Settings → Load demo trays.
        viewModelScope.launch { settingsStore.setDemoMode(enabled) }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setDarkMode(enabled) }
    }

    fun setHighlightTray(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setHighlightTray(enabled) }
    }

    fun setVoiceReadout(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setVoiceReadout(enabled) }
    }

    fun setScanChime(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setScanChime(enabled) }
    }

    fun setVerticalVideo(enabled: Boolean) {
        if (!enabled) hideViewfinder = false
        viewModelScope.launch { settingsStore.setVerticalVideo(enabled) }
    }

    /**
     * Flips vertical video from the persisted value — not the composing
     * snapshot — so the header button genuinely switches ON/OFF on every
     * tap even if the panel's settings snapshot is stale. Serialized so a
     * rapid double-tap flips twice (lands back where it started) instead
     * of racing two reads and sticking.
     */
    fun toggleVerticalVideo() {
        viewModelScope.launch {
            verticalToggleMutex.withLock {
                val current = settingsStore.settings.first().verticalVideo
                val next = !current
                if (!next) hideViewfinder = false
                settingsStore.setVerticalVideo(next)
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setNotificationsEnabled(enabled)
            ReminderScheduler.setDailyRemindersEnabled(context, enabled)
        }
    }

    /** Live analyzing feed: timestamped progress, visible while it happens. */
    val analyzeEvents = mutableStateListOf<AnalyzeEvent>()
    /** Live viewfinder coaching ("Too close…"), null when framing is fine. */
    var framingAdviceNow by mutableStateOf<String?>(null)
    private var analyzeJob: Job? = null
    /** Serializes 9:16 toggle flips so rapid taps can't race. */
    private val verticalToggleMutex = Mutex()

    /** Cancels an in-flight analysis (a no-op when idle). */
    fun cancelAnalyze() {
        analyzeJob?.cancel()
        analyzeJob = null
    }

    /**
     * Downscales a capture JPEG for upload (max 640 on the long edge,
     * JPEG quality 85). Returns the original bytes when already small
     * or when anything fails — never blocks a scan.
     */
    private fun downscaleForUpload(imageBytes: ByteArray): ByteArray {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0 || maxOf(w, h) <= 640) return imageBytes
            var sample = 1
            while (maxOf(w, h) / (sample * 2) >= 640) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(
                imageBytes, 0, imageBytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return imageBytes
            val out = ByteArrayOutputStream()
            if (!decoded.compress(Bitmap.CompressFormat.JPEG, 85, out)) return imageBytes
            val scaled = out.toByteArray()
            return if (scaled.isNotEmpty()) scaled else imageBytes
        } catch (_: Exception) {
            return imageBytes
        }
    }

    /** Runs a tray photo through the active analyzer (demo, Meta Crop Scout, or Gemini). */
    fun analyze(tray: TrayEntity, imageBytes: ByteArray, onDone: () -> Unit) {
        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch {
            analyzing = true
            lastError = null
            analyzeStage = "Saving capture…"
            analyzeStartedAtMs = SystemClock.elapsedRealtime()
            analyzeEvents.clear()
            framingAdviceNow = null
            fun event(text: String) {
                val elapsed = SystemClock.elapsedRealtime() - analyzeStartedAtMs
                analyzeEvents += AnalyzeEvent(elapsed, text)
                Log.i("SproutAnalysis", "+${elapsed}ms $text")
            }
            pendingPhoto = imageBytes
            pendingSamOverlay = null
            val captureAt = System.currentTimeMillis()
            pendingScanTimestamp = captureAt
            pendingPhotoPath = if (imageBytes.isNotEmpty()) {
                try {
                    ScanPhotoStore.save(context, tray.id, captureAt, imageBytes)
                } catch (e: Exception) {
                    Log.w("SproutAnalysis", "Photo save failed: ${e.message}")
                    null
                }
            } else {
                null
            }
            event(
                if (imageBytes.isNotEmpty()) "Photo saved (${formatKb(imageBytes.size)})"
                else "Empty capture"
            )
            try {
                val s = settingsStore.settings.first()
                if (!seededTimings) {
                    recentDemoMs.addAll(s.recentDemoMs)
                    recentGeminiMs.addAll(s.recentGeminiMs)
                    seededTimings = true
                }
                // Analyzer order (hackathon): Demo (if demoMode) >
                // Meta Spark (if useMetaAi + Meta key present) >
                // Gemini (if Gemini key present) > Demo.
                // NOTE: unlike the old v2 logic, a blank Gemini key does
                // NOT force demo when a Meta key is present — Meta is
                // checked before the Gemini fallback.
                val useMeta = s.useMetaAi && s.metaApiKey.isNotBlank()
                val demo = s.demoMode || (!useMeta && s.geminiApiKey.isBlank())
                typicalAnalyzeMs = (if (demo) recentDemoMs else recentGeminiMs)
                    .average().takeUnless { it.isNaN() }?.toLong()
                event(
                    when {
                        demo -> "Analyzer: scripted demo"
                        useMeta -> "Analyzer: Meta Crop Scout"
                        else -> "Analyzer: Gemini"
                    }
                )
                // Full-res photo stays in records; the upload is downscaled
                // (640×480 is plenty for plant health and uploads 4-8×
                // faster on slow WiFi).
                val uploadBytes = if (demo || imageBytes.isEmpty()) {
                    imageBytes
                } else {
                    downscaleForUpload(imageBytes).also {
                        if (it.size != imageBytes.size) {
                            event("Photo ${formatKb(imageBytes.size)} → ${formatKb(it.size)}")
                        }
                    }
                }
                // Crop Scout pipeline: SAM measures, Spark judges. The SAM
                // gate is the kill-switch: Meta toggle + SAM toggle both
                // on, or no segmenter is even constructed — zero SAM
                // network calls when off, and SAM is inert when Meta is
                // off (the Gemini path below is untouched).
                val growOut = tray.growIntent == GrowIntent.GROW_OUT
                val segmenter: Segmenter? = when {
                    !s.useMetaAi || !s.useSamMeasurement -> null
                    s.demoMode -> DemoSegmenter()
                    s.metaApiKey.isNotBlank() -> MetaSamSegmenter(s.metaApiKey, growOut)
                    else -> DemoSegmenter()
                }
                var seg: Segmentation? = null
                var samError: Throwable? = null
                if (segmenter != null) {
                    analyzeStage = "Segmenting…"
                    try {
                        seg = segmenter.segment(uploadBytes, ::event)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        samError = e
                        event(samFailureFlag(e))
                    }
                } else if (s.useMetaAi) {
                    samError = SegmentationException("turned off in Settings")
                    event(samFailureFlag(samError))
                }
                val growPlan = if (growOut) {
                    "This tray is being grown out to full maturity, " +
                        "NOT harvested as microgreens — do not judge it " +
                        "against a microgreen harvest timeline; assess " +
                        "health for a maturing plant."
                } else null
                val analyzer: PlantAnalyzer = when {
                    s.demoMode -> DemoAnalyzer()
                    useMeta -> MetaAnalyzer(s.metaApiKey, seg?.result?.summary, growPlan)
                    s.geminiApiKey.isNotBlank() -> GeminiAnalyzer(s.geminiApiKey, tray.growIntent)
                    else -> DemoAnalyzer()
                }
                analyzeStage = when {
                    demo -> "Running scripted demo analysis…"
                    useMeta -> "Analyzing with Meta AI · ${formatKb(uploadBytes.size)}"
                    else -> "Analyzing with Gemini · ${formatKb(uploadBytes.size)}"
                }
                var result = analyzer.analyze(uploadBytes, tray.variety, ::event)
                if (seg != null) {
                    result = result.copy(
                        samCoverage = seg.result.coveragePercent,
                        samSource = seg.source,
                        trayBox = seg.trayBox ?: result.trayBox,
                        // Scripted demo coverage gives way to the measured number.
                        coveragePercent = if (result.isDemo) seg.result.coveragePercent
                        else result.coveragePercent
                    )
                    pendingSamOverlay = try {
                        overlayBitmap(seg.plantGrid, seg.width, seg.height)
                    } catch (_: Exception) {
                        null
                    }
                    val overlay = pendingSamOverlay
                    if (overlay != null) event("Overlay ready (${overlay.width}×${overlay.height})")
                } else if (samError != null && s.useMetaAi) {
                    result = result.copy(issues = result.issues + samFailureFlag(samError))
                }
                pendingAnalysis = result
                lastResults[tray.id] = result
                val tookMs = SystemClock.elapsedRealtime() - analyzeStartedAtMs
                event("Done in ${tookMs / 1000}s")
                recordAnalyzeDuration(result.isDemo, tookMs)
            } catch (e: CancellationException) {
                event("Cancelled")
                throw e
            } catch (e: Exception) {
                Log.e("SproutAnalysis", "Analysis failed: ${e.message}")
                lastError = e.message ?: "Analysis failed"
                event("Failed: ${lastError?.take(120)}")
            } finally {
                analyzing = false
                analyzeStage = ""
                // A cancelled run stays put — only a finished run advances.
                if (isActive) onDone()
            }
        }
    }

    fun applyDetectedStage(tray: TrayEntity) {
        val result = pendingAnalysis ?: return
        viewModelScope.launch {
            repository.applyDetectedStage(tray, result.stage)
            repository.saveAnalysis(tray, result, pendingPhotoPath, pendingScanTimestamp)
            clearPendingScan()
        }
    }

    fun saveAnalysis(tray: TrayEntity) {
        val result = pendingAnalysis ?: return
        viewModelScope.launch {
            repository.saveAnalysis(tray, result, pendingPhotoPath, pendingScanTimestamp)
            clearPendingScan()
        }
    }

    fun discardAnalysis() {
        clearPendingScan()
        lastError = null
    }

    // ---- Wild plant identification (fully separate from tray scanning) ----
    // Own state, own analyzer, own photo store. Identifying never touches
    // tray data, scan history, or the scorecard — and nothing is written
    // until an explicit Save tap files the card in the herbarium.
    var identifying by mutableStateOf(false)
        private set
    var pendingIdentification by mutableStateOf<PlantIdentification?>(null)
        private set
    var identifyPhoto by mutableStateOf<ByteArray?>(null)
        private set
    /** Where the pending ID capture's JPEG was saved (null when no photo bytes). */
    var identifyPhotoPath by mutableStateOf<String?>(null)
        private set
    /** Capture timestamp shared by the saved JPEG name and the herbarium entry. */
    var identifyTimestampMs by mutableStateOf(System.currentTimeMillis())
        private set
    var identifyError by mutableStateOf<String?>(null)
        private set
    /** What the current identification is doing (shown under the spinner). */
    var identifyStage by mutableStateOf("")
        private set
    /** Elapsed-realtime base for the identifying timer. */
    var identifyStartedAtMs by mutableStateOf(0L)
        private set
    /** Live identifying feed: timestamped progress, visible while it happens. */
    val identifyEvents = mutableStateListOf<AnalyzeEvent>()
    private var identifyJob: Job? = null

    /** Cancels an in-flight identification (a no-op when idle). */
    fun cancelIdentify() {
        identifyJob?.cancel()
        identifyJob = null
    }

    /** Runs a wild-plant photo through the active identifier (demo, Meta, or Gemini). */
    fun identifyPlant(imageBytes: ByteArray, onDone: () -> Unit) {
        identifyJob?.cancel()
        identifyJob = viewModelScope.launch {
            identifying = true
            identifyError = null
            identifyStage = "Saving capture…"
            identifyStartedAtMs = SystemClock.elapsedRealtime()
            identifyEvents.clear()
            fun event(text: String) {
                val elapsed = SystemClock.elapsedRealtime() - identifyStartedAtMs
                identifyEvents += AnalyzeEvent(elapsed, text)
                Log.i("SproutIdentify", "+${elapsed}ms $text")
            }
            identifyPhoto = imageBytes
            val captureAt = System.currentTimeMillis()
            identifyTimestampMs = captureAt
            identifyPhotoPath = if (imageBytes.isNotEmpty()) {
                try {
                    WildPhotoStore.save(context, captureAt, imageBytes)
                } catch (e: Exception) {
                    Log.w("SproutIdentify", "Photo save failed: ${e.message}")
                    null
                }
            } else {
                null
            }
            event(
                if (imageBytes.isNotEmpty()) "Photo saved (${formatKb(imageBytes.size)})"
                else "Empty capture"
            )
            try {
                val s = settingsStore.settings.first()
                // Same order as tray scan (see selectPlantIdentifier):
                // Demo (if demoMode) > Meta (if useMetaAi + Meta key) >
                // Gemini (if Gemini key) > Demo.
                val identifier = selectPlantIdentifier(s)
                val demo = identifier is DemoPlantIdentifier
                val useMeta = identifier is MetaPlantIdentifier
                event(
                    when {
                        demo -> "Identifier: scripted demo"
                        useMeta -> "Identifier: Meta Crop Scout"
                        else -> "Identifier: Gemini"
                    }
                )
                val uploadBytes = if (demo || imageBytes.isEmpty()) {
                    imageBytes
                } else {
                    downscaleForUpload(imageBytes)
                }
                identifyStage = when {
                    demo -> "Running scripted demo identification…"
                    useMeta -> "Identifying with Meta AI · ${formatKb(uploadBytes.size)}"
                    else -> "Identifying with Gemini · ${formatKb(uploadBytes.size)}"
                }
                pendingIdentification = identifier.identify(uploadBytes, ::event)
                val tookMs = SystemClock.elapsedRealtime() - identifyStartedAtMs
                event("Done in ${tookMs / 1000}s")
            } catch (e: CancellationException) {
                event("Cancelled")
                throw e
            } catch (e: Exception) {
                Log.e("SproutIdentify", "Identification failed: ${e.message}")
                identifyError = e.message ?: "Identification failed"
                event("Failed: ${identifyError?.take(120)}")
            } finally {
                identifying = false
                identifyStage = ""
                // A cancelled run stays put — only a finished run advances.
                if (isActive) onDone()
            }
        }
    }

    fun discardIdentification() {
        pendingIdentification = null
        identifyPhoto = null
        identifyPhotoPath = null
        identifyError = null
    }

    /** Explicit Save only: files the pending card in the herbarium. Never automatic. */
    fun saveIdentification(locationNote: String) {
        val result = pendingIdentification ?: return
        viewModelScope.launch {
            repository.saveHerbariumEntry(
                result, identifyPhotoPath, identifyTimestampMs, locationNote.trim()
            )
        }
    }

    fun deleteHerbariumEntry(entry: HerbariumEntity) {
        viewModelScope.launch { repository.deleteHerbariumEntry(entry) }
    }

    fun setTrayAnchor(trayId: Long, x: Float, y: Float, z: Float) {
        viewModelScope.launch { repository.setTrayAnchor(trayId, x, y, z) }
    }

    fun clearTrayAnchor(trayId: Long) {
        viewModelScope.launch { repository.clearTrayAnchor(trayId) }
    }

    fun toggleCardExpanded(trayId: Long) {
        expandedCardTrayId = if (expandedCardTrayId == trayId) null else trayId
    }

    /** Runs the HUD scan flow for a tray (e.g. from its floating card). */
    fun openTrayScan(trayId: Long) {
        placementMode = false
        expandedCardTrayId = null
        hudScreen = HudScreen.Scan(trayId)
    }

    fun observeBaseline(trayId: Long): kotlinx.coroutines.flow.Flow<BaselineEntity?> =
        repository.observeBaseline(trayId)

    /** Snapshots the tray's current scan as its baseline; [onResult] reports success. */
    fun snapshotBaseline(tray: TrayEntity, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onResult(repository.snapshotBaseline(tray))
        }
    }

    fun saveTrial(
        variety: String,
        daysToHarvest: Int?,
        yieldGrams: Int?,
        tasteRating: Int?
    ) {
        viewModelScope.launch {
            repository.saveTrial(variety, daysToHarvest, yieldGrams, tasteRating)
        }
    }

    /**
     * Exports the whole scan history to a CSV in Downloads (for DaVinci
     * compositing over timelapse footage). Must be called from a coroutine.
     */
    suspend fun exportScanHistory(context: Context): ScanExportResult =
        withContext(Dispatchers.IO) {
            val traysById = repository.getTraysOnce().associateBy { it.id }
            val scans = repository.getAllScansOnce()
            if (scans.isEmpty()) return@withContext ScanExportResult.Empty
            val rows = scans.map { scan ->
                val tray = traysById[scan.trayId]
                ScanCsvRow(
                    date = formatDateTime(scan.timestampEpochMs),
                    tray = tray?.name ?: "tray ${scan.trayId}",
                    variety = tray?.variety ?: scan.variety,
                    day = tray?.let { dayOn(it.seededAtEpochMs, scan.timestampEpochMs) } ?: 0,
                    health = scan.healthScore,
                    topIssue = scan.issues.split(";").firstOrNull()?.trim().orEmpty()
                )
            }
            val csv = ScanHistoryExport.buildCsv(rows)
            val name = ScanHistoryExport.writeToDownloads(context, csv)
            if (name != null) ScanExportResult.Saved(name) else ScanExportResult.Failed
        }

    private suspend fun recordAnalyzeDuration(demo: Boolean, ms: Long) {
        val history = if (demo) recentDemoMs else recentGeminiMs
        history.addLast(ms)
        while (history.size > MAX_TIMING_SAMPLES) history.removeFirst()
        typicalAnalyzeMs = history.average().toLong()
        if (demo) settingsStore.setRecentDemoMs(history.toList())
        else settingsStore.setRecentGeminiMs(history.toList())
    }

    private fun formatKb(bytes: Int): String = "${bytes / 1024} KB"

    private fun clearPendingScan() {
        pendingAnalysis = null
        pendingPhoto = null
        pendingPhotoPath = null
        pendingSamOverlay = null
    }
}
